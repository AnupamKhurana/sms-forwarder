package com.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * Listens for device boot completion and restarts the ForwarderService
 * if forwarding was previously enabled.
 *
 * Uses a small delay to avoid race conditions during early boot.
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    /** Delay before starting service on boot to let the system settle */
    private static final long BOOT_DELAY_MS = 10_000L; // 10 seconds

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {

            ConfigManager config = new ConfigManager(context);
            if (config.isEnabled()) {
                Log.i(TAG, "Device booted, scheduling ForwarderService start in " + BOOT_DELAY_MS + "ms");

                // Use a handler with delay to let the system settle before starting
                final Context appContext = context.getApplicationContext();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        Log.i(TAG, "Starting ForwarderService after boot delay");
                        Intent serviceIntent = new Intent(appContext, ForwarderService.class);
                        ContextCompat.startForegroundService(appContext, serviceIntent);

                        // Also ensure watchdog is enqueued
                        ServiceWatchdog.enqueue(appContext);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start service after boot", e);
                    }
                }, BOOT_DELAY_MS);
            } else {
                Log.d(TAG, "Device booted, but forwarding is disabled");
            }
        }
    }
}
