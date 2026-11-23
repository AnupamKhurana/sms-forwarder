package com.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.core.content.ContextCompat;

/**
 * Multi-purpose receiver that restarts the ForwarderService:
 *   - When AlarmManager fires after service was killed
 *   - When the app package is replaced (updated)
 *   - As a fallback restart mechanism
 */
public class RestartReceiver extends BroadcastReceiver {
    private static final String TAG = "RestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);

        // Security check: only accept expected system or internal restart actions
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !"com.smsforwarder.RESTART_SERVICE".equals(action)) {
            Log.w(TAG, "Ignoring untrusted or unhandled action: " + action);
            return;
        }

        ConfigManager config = new ConfigManager(context);
        if (!config.isEnabled()) {
            Log.d(TAG, "Forwarding disabled, not restarting");
            return;
        }

        Log.i(TAG, "Restarting ForwarderService...");
        try {
            Intent serviceIntent = new Intent(context, ForwarderService.class);
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart service", e);
        }
    }
}
