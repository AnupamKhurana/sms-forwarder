package com.smsforwarder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager periodic worker that acts as a safety net.
 * Checks every 15 minutes if ForwarderService is running.
 * If it's not running but forwarding is enabled, restarts it.
 *
 * This catches the case where both the service AND the AlarmManager
 * restart fail (e.g., Samsung killed everything aggressively).
 */
public class ServiceWatchdog extends Worker {

    private static final String TAG = "ServiceWatchdog";
    public static final String UNIQUE_WORK_NAME = "sms_forwarder_watchdog";

    public ServiceWatchdog(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        ConfigManager config = new ConfigManager(context);

        if (!config.isEnabled()) {
            Log.d(TAG, "Forwarding disabled, watchdog skipping");
            return Result.success();
        }

        if (!isServiceRunning(context)) {
            Log.w(TAG, "ForwarderService is NOT running! Restarting...");
            try {
                Intent serviceIntent = new Intent(context, ForwarderService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
                Log.i(TAG, "ForwarderService restarted by watchdog");
            } catch (Exception e) {
                Log.e(TAG, "Watchdog failed to restart service", e);
                return Result.retry();
            }
        } else {
            Log.d(TAG, "ForwarderService is running, all good");
        }

        return Result.success();
    }

    private boolean isServiceRunning(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return false;

        // Note: getRunningServices is deprecated for third-party apps but still works
        // for checking our own services.
        for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
            if (ForwarderService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Enqueue the periodic watchdog work. Safe to call multiple times —
     * KEEP policy ensures only one instance runs.
     */
    public static void enqueue(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ServiceWatchdog.class, 15, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);

        Log.i(TAG, "Watchdog work enqueued");
    }

    /**
     * Cancel the periodic watchdog work.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);
        Log.i(TAG, "Watchdog work cancelled");
    }
}
