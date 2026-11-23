package com.smsforwarder;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core notification listener service.
 * Intercepts notifications from Google Messages (SMS & RCS), WhatsApp, Samsung Messages,
 * and other apps, evaluates keyword and sender filters, and forwards them to
 * Telegram, Email, or Webhook.
 */
public class NotificationForwarderService extends NotificationListenerService {

    private static final String TAG = "NotificationForwarder";

    // Known messaging packages
    private static final Set<String> MESSAGING_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.messaging", // Google Messages (SMS + RCS)
            "com.samsung.android.messaging",     // Samsung Messages
            "com.android.mms",                   // Default AOSP Messaging
            "com.oneplus.mms",                   // OnePlus SMS
            "com.coloros.mms",                   // Oppo / OnePlus ColorOS
            "com.oppo.mms",                      // Oppo Messages
            "com.xiaomi.mms",                    // Xiaomi / MIUI SMS
            "com.whatsapp",                      // WhatsApp
            "com.whatsapp.w4b",                  // WhatsApp Business
            "com.android.shell"                  // Shell / ADB Testing
    ));

    // Deduplication cache: key -> timestamp (ms)
    private static final Map<String, Long> RECENT_MESSAGES = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 15000; // 15 seconds

    private ConfigManager config;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate() {
        super.onCreate();
        config = new ConfigManager(this);
        Log.i(TAG, "NotificationForwarderService created and active");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Notification listener connected to system notification manager");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();
        Log.d(TAG, "onNotificationPosted from package: " + packageName);

        // 1. Ignore notifications from our own app
        if (packageName == null || packageName.equals(getPackageName())) {
            return;
        }

        // 2. Check if master forwarding is enabled
        if (config == null) {
            config = new ConfigManager(this);
        }
        if (!config.isEnabled()) {
            return;
        }

        // 3. Package check (messaging apps vs all apps)
        if (!config.isMonitorAllApps() && !MESSAGING_PACKAGES.contains(packageName)) {
            return;
        }

        // 4. Extract notification content
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // Ignore ongoing notifications like media playback or call-in-progress
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) return;

        String sender = extractSender(extras, packageName);
        String body = extractMessageBody(extras, notification);

        if (body == null || body.trim().isEmpty()) {
            return;
        }

        // 5. Deduplicate identical messages received within DEDUP_WINDOW_MS
        String dedupKey = packageName + "||" + sender + "||" + body;
        long now = System.currentTimeMillis();
        Long lastSeen = RECENT_MESSAGES.get(dedupKey);
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Ignoring duplicate notification update within window: " + dedupKey);
            return;
        }
        RECENT_MESSAGES.put(dedupKey, now);
        cleanOldCacheEntries(now);

        // 6. Evaluate filters
        String appLabel = getAppFriendlyName(packageName);
        String senderWithSource = sender.isEmpty() ? appLabel : sender + " (" + appLabel + ")";

        if (!matchesKeywordFilter(body)) {
            Log.d(TAG, "Message from " + senderWithSource + " does not match keyword filter: " + body);
            return;
        }

        if (!matchesSourceFilter(sender)) {
            Log.d(TAG, "Message sender " + sender + " rejected by source whitelist/blacklist");
            return;
        }

        Log.i(TAG, "Forwarding message from " + senderWithSource + ": " + body);

        // 7. Dispatch asynchronously to active destinations
        executor.execute(() -> dispatchForwarding(senderWithSource, body));
    }

    private String extractSender(Bundle extras, String packageName) {
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title != null && title.length() > 0) {
            return title.toString().trim();
        }
        CharSequence titleBig = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        if (titleBig != null && titleBig.length() > 0) {
            return titleBig.toString().trim();
        }
        return getAppFriendlyName(packageName);
    }

    private String extractMessageBody(Bundle extras, Notification notification) {
        // Try MessagingStyle latest message
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0) {
            Parcelable lastMsg = messages[messages.length - 1];
            if (lastMsg instanceof Bundle) {
                Bundle msgBundle = (Bundle) lastMsg;
                CharSequence text = msgBundle.getCharSequence("text");
                if (text != null && text.length() > 0) {
                    return text.toString().trim();
                }
            }
        }

        // Try BigText
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bigText != null && bigText.length() > 0) {
            return bigText.toString().trim();
        }

        // Try standard Text
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (text != null && text.length() > 0) {
            return text.toString().trim();
        }

        // Try ticker
        if (notification.tickerText != null && notification.tickerText.length() > 0) {
            return notification.tickerText.toString().trim();
        }

        return null;
    }

    private String getAppFriendlyName(String packageName) {
        if (packageName.contains("whatsapp.w4b")) return "WhatsApp Business";
        if (packageName.contains("whatsapp")) return "WhatsApp";
        if (packageName.contains("messaging") || packageName.contains("mms")) return "Messages";
        return packageName;
    }

    private boolean matchesKeywordFilter(String body) {
        List<String> keywords = config.getKeywords();
        if (keywords.isEmpty()) return true; // No filter configured -> forward all

        String lowerBody = body.toLowerCase();
        for (String kw : keywords) {
            if (lowerBody.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSourceFilter(String sender) {
        // Check negative blacklist
        List<String> blacklist = config.getNegativeSourceNumbers();
        for (String black : blacklist) {
            if (!black.isEmpty() && sender.toLowerCase().contains(black.toLowerCase())) {
                return false;
            }
        }

        // Check whitelist
        List<String> whitelist = config.getSourceNumbers();
        if (whitelist.isEmpty()) return true;

        for (String white : whitelist) {
            if (!white.isEmpty() && sender.toLowerCase().contains(white.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void dispatchForwarding(String sender, String body) {
        ForwardLog log = new ForwardLog(this);
        boolean anyAttempted = false;

        // Forward to SMS / Messaging App
        if (config.isSmsDestinationEnabled()) {
            anyAttempted = true;
            boolean success = SmsForwarder.forwardSms(this, config, sender, body);
            String channel = SmsForwarder.isDirectSmsAvailable(this, config) ? "SMS (Direct)" : "SMS (Messaging App)";
            log.addEntry(sender, body, success, channel);
        }

        // Forward to Instant Push (ntfy)
        if (config.isNtfyDestinationEnabled()) {
            anyAttempted = true;
            String server = config.getNtfyServer();
            String topic = config.getNtfyTopic();
            boolean success = NetworkForwarder.sendNtfySync(server, topic, sender, body);
            log.addEntry(sender, body, success, "Instant Push");
        }

        // Forward to Telegram
        if (config.isTelegramDestinationEnabled()) {
            anyAttempted = true;
            String token = config.getTelegramBotToken();
            String chatId = config.getTelegramChatId();
            boolean success = NetworkForwarder.sendTelegramSync(token, chatId, sender, body);
            log.addEntry(sender, body, success, "Telegram");
        }

        // Forward to Webhook
        if (config.isWebhookDestinationEnabled()) {
            anyAttempted = true;
            String webhookUrl = config.getWebhookUrl();
            boolean success = NetworkForwarder.sendWebhookSync(webhookUrl, sender, body);
            log.addEntry(sender, body, success, "Webhook");
        }

        // Forward to Email
        if (config.isEmailDestinationEnabled()) {
            anyAttempted = true;
            boolean success = SmtpForwarder.sendEmailSync(
                    config.getSmtpHost(),
                    config.getSmtpPort(),
                    config.getEmailUsername(),
                    config.getEmailPassword(),
                    config.getEmailRecipient(),
                    sender,
                    body
            );
            log.addEntry(sender, body, success, "Email");
        }

        if (!anyAttempted) {
            log.addEntry(sender, body, false, "Unconfigured");
        }
    }

    private void cleanOldCacheEntries(long now) {
        RECENT_MESSAGES.entrySet().removeIf(entry -> (now - entry.getValue()) > (DEDUP_WINDOW_MS * 2));
    }
}
