package com.smsforwarder;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Foreground service that keeps the app alive so the BroadcastReceiver
 * fires reliably even when the app is in the background.
 *
 * Uses specialUse foreground service type (no timeout limit).
 * Implements multi-layer self-healing:
 *   - AlarmManager restart on destroy/task-removed
 *   - Periodic heartbeat to prove liveness to the OS
 *   - WorkManager watchdog (see ServiceWatchdog.java)
 */
public class ForwarderService extends Service {

    private static final String TAG = "ForwarderService";
    private static final String CHANNEL_ID = "sms_forwarder_channel_v2";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESTART_REQUEST_CODE = 42;

    /** Heartbeat interval: update notification every 30 minutes to prove liveness */
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 60 * 1000L;

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    private Handler heartbeatHandler;
    private PowerManager.WakeLock wakeLock;

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Heartbeat — service is alive");
            updateNotification();
            if (heartbeatHandler != null) {
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        createNotificationChannel();
        heartbeatHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = buildNotification(pendingIntent);

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (API 34+): specialUse type
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                // Android 8.0 - 13 (API 26-33): standard foreground service
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to start foreground", t);
            startForeground(NOTIFICATION_ID, notification);
        }

        // Start heartbeat
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "App swiped from recents — scheduling restart");
        scheduleRestart();
        super.onTaskRemoved(rootIntent);
    }

    /**
     * Handle the Android 15+ foreground service timeout.
     * specialUse type should NOT get timeouts, but we handle it
     * defensively in case the OS behavior changes.
     */
    @Override
    public void onTimeout(int startId) {
        Log.w(TAG, "onTimeout called (startId=" + startId + ") — scheduling restart");
        scheduleRestart();
        stopSelf(startId);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Service destroyed — scheduling restart");
        running = false;

        // Stop heartbeat
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to release wake lock", e);
            }
        }

        // Schedule restart if forwarding is still enabled
        scheduleRestart();

        super.onDestroy();
    }

    /**
     * Schedules an AlarmManager alarm to restart the service in ~5 seconds.
     * This is the primary self-healing mechanism when the OS kills us.
     */
    private void scheduleRestart() {
        ConfigManager config = new ConfigManager(this);
        if (!config.isEnabled()) {
            Log.d(TAG, "Forwarding disabled, not scheduling restart");
            return;
        }

        try {
            Intent restartIntent = new Intent(this, RestartReceiver.class);
            restartIntent.setAction("com.smsforwarder.RESTART_SERVICE");
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this, RESTART_REQUEST_CODE, restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerTime = SystemClock.elapsedRealtime() + 5000; // 5 seconds
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerTime,
                            pendingIntent);
                } else {
                    alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerTime,
                            pendingIntent);
                }
                Log.i(TAG, "Restart alarm scheduled for ~5 seconds from now");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restart alarm", e);
        }
    }

    /**
     * Updates the notification with a fresh timestamp to prove liveness.
     */
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = buildNotification(pendingIntent);
        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to update notification", e);
        }
    }

    private Notification buildNotification(PendingIntent contentIntent) {
        String timestamp = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date());

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS & Message Forwarder")
                .setContentText("Monitoring messages and notifications…")
                .setSubText("Active since " + timestamp)
                .setSmallIcon(R.drawable.ic_info)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();

        notification.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder Service",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Shows when SMS Forwarder is actively monitoring messages");

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
