package com.github.ulresh.mental_arithmetic;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service used only to turn off and lock the screen the same way the power button
 * does. Unlike DevicePolicyManager.lockNow(), this keeps fingerprint unlock available.
 */
public class LockScreenService extends AccessibilityService {
    @SuppressLint("StaticFieldLeak") // Cleared as soon as the system unbinds the service.
    private static LockScreenService instance;

    static boolean isConnected() {
        return instance != null;
    }

    static boolean lockScreen() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
