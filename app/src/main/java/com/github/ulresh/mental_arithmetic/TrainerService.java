package com.github.ulresh.mental_arithmetic;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Voice training session: speaks a problem, listens for the answer, reacts, and so on.
 * It is a microphone foreground service so that it keeps working on the locked screen,
 * and it stops as soon as the user unlocks the phone.
 */
public class TrainerService extends Service {
    private static final String TAG = "MentalArithmetic";
    private static final String CHANNEL_ID = "session";
    private static final int NOTIFICATION_ID = 1;
    private static final Locale RUSSIAN = Locale.forLanguageTag("ru-RU");
    /** Media usage puts the voice on the media volume ("Мультимедиа"), which the volume keys control. */
    private static final AudioAttributes VOICE_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private static final long WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L;
    /** Pause after speaking so the recognizer does not catch the tail of our own voice. */
    private static final long AFTER_SPEECH_DELAY_MS = 200;
    private static final long RELISTEN_DELAY_MS = 300;
    private static final long MAX_RETRY_DELAY_MS = 5000;
    /** Restart the recognizer if it gives no callbacks for this long. */
    private static final long LISTEN_TIMEOUT_MS = 20_000;
    private static final long FAIL_STOP_TIMEOUT_MS = 10_000;

    private enum State { WAITING, SPEAKING, LISTENING, STOPPED }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Trainer trainer = new Trainer(new Random());
    private final Runnable listenTask = this::listen;
    private final Runnable listenTimeoutTask = this::onListenTimeout;
    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopSelf();
        }
    };

    private State state = State.WAITING;
    private TextToSpeech tts;
    private boolean ttsReady;
    private SpeechRecognizer recognizer;
    private PowerManager.WakeLock wakeLock;
    private MediaSession mediaSession;
    private int utteranceCounter;
    private String currentUtteranceId;
    private int listenSession;
    private int failures;
    /** Stop the service once the current utterance (an error message) has been spoken. */
    private boolean finishing;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
        // Handler delays must not stall while the CPU sleeps with the screen off.
        wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mental-arithmetic:session");
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        // Between phrases nothing is playing, so the volume keys (on the locked screen too) would
        // change the ringtone volume. A "playing" media session makes them change the voice volume.
        mediaSession = new MediaSession(this, TAG);
        mediaSession.setPlaybackToLocal(VOICE_ATTRIBUTES);
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());
        mediaSession.setActive(true);
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        tts = new TextToSpeech(this, this::onTtsInit);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        if (!LockScreenService.lockScreen()) {
            Log.w(TAG, "Could not lock the screen: the accessibility service is not connected");
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        state = State.STOPPED;
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(unlockReceiver);
        recognizer.destroy();
        tts.stop();
        tts.shutdown();
        mediaSession.release();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    private void startInForeground() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW));
        Intent openApp = getPackageManager().getLaunchIntentForPackage(getPackageName());
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(PendingIntent.getActivity(this, 0, openApp, PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void onTtsInit(int status) {
        if (state == State.STOPPED) {
            return;
        }
        if (status != TextToSpeech.SUCCESS) {
            fail(R.string.tts_failed);
            return;
        }
        int language = tts.setLanguage(RUSSIAN);
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail(R.string.no_russian_tts);
            return;
        }
        tts.setAudioAttributes(VOICE_ATTRIBUTES);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(() -> onUtteranceDone(utteranceId));
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                handler.post(() -> onUtteranceDone(utteranceId));
            }
        });
        ttsReady = true;
        speak(trainer.next().speech());
    }

    private void speak(String text) {
        stopListening();
        state = State.SPEAKING;
        String utteranceId = "utterance-" + ++utteranceCounter;
        currentUtteranceId = utteranceId;
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Could not speak: " + text);
            onUtteranceDone(utteranceId);
        }
    }

    private void onUtteranceDone(String utteranceId) {
        if (state != State.SPEAKING || !utteranceId.equals(currentUtteranceId)) {
            return;
        }
        if (finishing) {
            stopSelf();
        } else {
            scheduleListen(AFTER_SPEECH_DELAY_MS);
        }
    }

    /** Reports a fatal problem on the screen and, if possible, by voice, then stops the session. */
    private void fail(int messageId) {
        String message = getString(messageId);
        Log.e(TAG, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (!ttsReady) {
            stopSelf();
            return;
        }
        finishing = true;
        speak(message);
        handler.postDelayed(this::stopSelf, FAIL_STOP_TIMEOUT_MS);
    }

    private void scheduleListen(long delayMs) {
        state = State.WAITING;
        handler.removeCallbacks(listenTask);
        handler.postDelayed(listenTask, delayMs);
    }

    private void listen() {
        if (state != State.WAITING) {
            return;
        }
        state = State.LISTENING;
        recognizer.setRecognitionListener(new SessionListener(++listenSession));
        recognizer.startListening(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, RUSSIAN.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName()));
        restartListenTimeout();
    }

    private void stopListening() {
        handler.removeCallbacks(listenTask);
        handler.removeCallbacks(listenTimeoutTask);
        if (state == State.LISTENING) {
            recognizer.cancel();
        }
    }

    private void restartListenTimeout() {
        handler.removeCallbacks(listenTimeoutTask);
        handler.postDelayed(listenTimeoutTask, LISTEN_TIMEOUT_MS);
    }

    private void onListenTimeout() {
        if (state != State.LISTENING) {
            return;
        }
        Log.w(TAG, "The recognizer does not respond, restarting it");
        recreateRecognizer();
        scheduleListen(RELISTEN_DELAY_MS);
    }

    private void recreateRecognizer() {
        recognizer.destroy();
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
    }

    private void onRecognitionResults(List<String> hypotheses) {
        handler.removeCallbacks(listenTimeoutTask);
        state = State.WAITING;
        failures = 0;
        Log.d(TAG, trainer.current() + " -> " + hypotheses);
        switch (trainer.onAnswer(hypotheses)) {
            case CORRECT -> speak(trainer.next().speech());
            case REPEAT -> speak(trainer.current().speech());
            case WRONG -> speak(getString(R.string.wrong_answer));
            case NO_ANSWER -> scheduleListen(RELISTEN_DELAY_MS);
        }
    }

    @SuppressLint("InlinedApi") // Plain int constants: on older Android they are just never reported.
    private void onRecognitionError(int error) {
        handler.removeCallbacks(listenTimeoutTask);
        state = State.WAITING;
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                // Silence or unintelligible speech: keep waiting for the answer.
                failures = 0;
                scheduleListen(RELISTEN_DELAY_MS);
            }
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(R.string.need_microphone);
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                 SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> fail(R.string.no_russian_recognition);
            default -> {
                Log.w(TAG, "Recognition error " + error + ", restarting the recognizer");
                failures++;
                recreateRecognizer();
                scheduleListen(Math.min(failures * 1000L, MAX_RETRY_DELAY_MS));
            }
        }
    }

    /** Ignores callbacks that belong to an earlier, cancelled listening attempt. */
    private final class SessionListener implements RecognitionListener {
        private final int session;

        SessionListener(int session) {
            this.session = session;
        }

        private boolean isCurrent() {
            return state == State.LISTENING && session == listenSession;
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
            if (isCurrent()) {
                restartListenTimeout();
            }
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            if (isCurrent()) {
                restartListenTimeout();
            }
        }

        @Override
        public void onError(int error) {
            if (isCurrent()) {
                onRecognitionError(error);
            }
        }

        @Override
        public void onResults(Bundle results) {
            if (isCurrent()) {
                onRecognitionResults(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
