package com.github.ulresh.mental_arithmetic;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 1;

    private boolean startOnResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // The voice is played at the media volume, so let the volume keys adjust it here as well.
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        findViewById(R.id.start).setOnClickListener(view -> start());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The activity is visible only on an unlocked screen, so any running session is over.
        stopService(new Intent(this, TrainerService.class));
        if (startOnResume) {
            startOnResume = false;
            start();
        }
    }

    private void start() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissionsToRequest(), REQUEST_PERMISSIONS);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast(R.string.no_recognition);
            return;
        }
        if (!LockScreenService.isConnected()) {
            toast(R.string.enable_accessibility);
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }
        // The service turns the screen off and runs the session by voice.
        startForegroundService(new Intent(this, TrainerService.class));
    }

    private static String[] permissionsToRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
        }
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_PERMISSIONS) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Starting right here would be undone by onResume(), which follows the permission dialog.
            startOnResume = true;
        } else {
            toast(R.string.need_microphone);
        }
    }

    private void toast(int messageId) {
        Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
    }
}
