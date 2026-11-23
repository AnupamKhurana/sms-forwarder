package com.smsforwarder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles SMS forwarding to a target phone number using:
 * 1. Direct background SMS via SmsManager (when SEND_SMS permission is granted)
 * 2. Intent to the system default Messaging App (Google Messages, Samsung Messages, etc.)
 */
public class SmsForwarder {

    private static final String TAG = "SmsForwarder";
    private static final String CHANNEL_SMS_ACTION = "sms_forward_action_channel";
    private static final int NOTIFICATION_BASE_ID = 2000;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public interface SmsCallback {
        void onResult(boolean success, String message);
    }

    /**
     * Checks if direct background SMS sending is permitted and configured.
     */
    public static boolean isDirectSmsAvailable(Context context, ConfigManager config) {
        if (ConfigManager.METHOD_APP.equals(config.getSmsMethod())) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Forwards an incoming notification message via SMS according to user settings.
     * Call from a background thread.
     */
    public static boolean forwardSms(Context context, ConfigManager config, String sender, String messageBody) {
        String targetNumber = config.getTargetNumber();
        if (targetNumber == null || targetNumber.trim().isEmpty()) {
            Log.e(TAG, "Target phone number is empty");
            return false;
        }

        String target = targetNumber.trim();
        String formattedMessage = "📬 SMS from " + sender + ":\n" + messageBody;
        String method = config.getSmsMethod();

        if (ConfigManager.METHOD_DIRECT.equals(method)) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "SEND_SMS permission not granted for direct SMS forwarding");
                prepareMessagingAppNotification(context, target, formattedMessage, sender,
                        "SEND_SMS permission missing. Tap to send via Messaging App.");
                return false;
            }
            return sendViaSmsManager(context, target, formattedMessage);
        } else if (ConfigManager.METHOD_APP.equals(method)) {
            prepareMessagingAppNotification(context, target, formattedMessage, sender, null);
            return true;
        } else {
            // METHOD_AUTO: try direct SMS if permission is granted, otherwise fallback to Messaging App
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                boolean sent = sendViaSmsManager(context, target, formattedMessage);
                if (sent) {
                    return true;
                }
                Log.w(TAG, "Direct SMS failed, falling back to messaging app notification");
            }
            prepareMessagingAppNotification(context, target, formattedMessage, sender, null);
            return true;
        }
    }

    /**
     * Sends an SMS directly in background using telephony SmsManager.
     */
    public static boolean sendViaSmsManager(Context context, String targetNumber, String messageText) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager is null on this device");
                return false;
            }

            ArrayList<String> parts = smsManager.divideMessage(messageText);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(targetNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(targetNumber, null, messageText, null, null);
            }

            Log.i(TAG, "Successfully sent SMS to " + targetNumber + " via SmsManager (" + parts.size() + " part(s))");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS via SmsManager to " + targetNumber, e);
            return false;
        }
    }

    /**
     * Creates an Intent to pre-fill the default messaging app.
     */
    public static Intent createMessagingAppIntent(String targetNumber, String messageText) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(targetNumber)));
        intent.putExtra("sms_body", messageText);
        intent.putExtra(Intent.EXTRA_TEXT, messageText);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Posts a notification prompting the user to send the forwarded SMS via the default Messaging App.
     */
    public static void prepareMessagingAppNotification(Context context, String targetNumber,
                                                       String messageText, String originalSender,
                                                       String optionalWarning) {
        createNotificationChannel(context);

        Intent intent = createMessagingAppIntent(targetNumber, messageText);
        int requestCode = (int) (System.currentTimeMillis() % 100000);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String subText = optionalWarning != null ? optionalWarning : "Tap to send via Messages app";
        String snippet = messageText.length() > 70 ? messageText.substring(0, 70) + "…" : messageText;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SMS_ACTION)
                .setSmallIcon(R.drawable.ic_info)
                .setContentTitle("💬 Forward SMS to " + targetNumber)
                .setContentText(snippet)
                .setSubText(subText)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .setBigContentTitle("💬 Forward SMS to " + targetNumber)
                        .bigText((optionalWarning != null ? optionalWarning + "\n\n" : "") + messageText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_info, "Open Messages", pendingIntent);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            int notifId = NOTIFICATION_BASE_ID + (int) (System.currentTimeMillis() % 1000);
            manager.notify(notifId, builder.build());
            Log.i(TAG, "Posted Messaging App notification (id=" + notifId + ") for " + targetNumber);
        }
    }

    /**
     * Tests SMS forwarding asynchronously.
     */
    public static void testSms(Context context, ConfigManager config, SmsCallback callback) {
        EXECUTOR.execute(() -> {
            String target = config.getTargetNumber();
            if (target == null || target.trim().isEmpty()) {
                deliverResult(callback, false, "Target phone number is empty. Please enter a number.");
                return;
            }

            String testMessage = "📬 Test message from SMS Forwarder: Forwarding to " + target + " is active!";
            boolean directAvailable = isDirectSmsAvailable(context, config);

            if (directAvailable) {
                boolean sent = sendViaSmsManager(context, target.trim(), testMessage);
                if (sent) {
                    deliverResult(callback, true, "Test SMS sent directly to " + target + " via SmsManager!");
                } else {
                    deliverResult(callback, false, "Failed to send SMS directly. Check SIM card or permission.");
                }
            } else {
                prepareMessagingAppNotification(context, target.trim(), testMessage, "SMS Forwarder Test", null);
                deliverResult(callback, true, "Messaging App notification generated! Tap notification to send.");
            }
        });
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(CHANNEL_SMS_ACTION) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_SMS_ACTION,
                        "SMS Forwarding Actions",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Prompts to open Messaging app for forwarded SMS");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static void deliverResult(SmsCallback callback, boolean success, String msg) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(success, msg));
        }
    }
}
