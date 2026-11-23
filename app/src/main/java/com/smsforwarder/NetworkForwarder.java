package com.smsforwarder;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles internet-based forwarding of SMS messages to:
 * 1. Telegram Bot API
 * 2. Generic HTTP/HTTPS Webhook (compatible with Discord, Slack, Zapier, n8n, custom servers)
 */
public class NetworkForwarder {

    private static final String TAG = "NetworkForwarder";
    private static final int TIMEOUT_MS = 15000;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public interface ForwardCallback {
        void onResult(boolean success, String message);
    }

    /**
     * Synchronously forwards a notification message to an ntfy topic.
     * Compatible with ntfy.sh and self-hosted ntfy instances.
     * Call only from a background thread.
     */
    public static boolean sendNtfySync(String server, String topic, String sender, String messageBody) {
        if (topic == null || topic.trim().isEmpty()) {
            Log.e(TAG, "ntfy topic is empty");
            return false;
        }

        String host = (server != null && !server.trim().isEmpty()) ? server.trim() : "https://ntfy.sh";
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        HttpURLConnection conn = null;
        try {
            String urlStr = host + "/" + topic.trim();
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            String safeTitle = sender != null && !sender.isEmpty()
                    ? sender.replaceAll("[\\r\\n]", " ").trim() : "Incoming Alert";
            conn.setRequestProperty("Title", safeTitle);
            conn.setRequestProperty("Priority", "high");
            conn.setRequestProperty("Tags", "incoming_envelope,bell");
            conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            byte[] outputBytes = (messageBody != null ? messageBody : "").getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outputBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Log.i(TAG, "Successfully dispatched to ntfy: " + responseCode);
                return true;
            } else {
                String errorBody = readStream(conn.getErrorStream());
                Log.e(TAG, "ntfy error (" + responseCode + "): " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send to ntfy", e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Synchronously forwards an SMS to Telegram Bot API.
     * Call only from a background thread.
     */
    public static boolean sendTelegramSync(String botToken, String chatId, String sender, String messageBody) {
        if (botToken == null || botToken.trim().isEmpty() || chatId == null || chatId.trim().isEmpty()) {
            Log.e(TAG, "Telegram credentials incomplete");
            return false;
        }

        HttpURLConnection conn = null;
        try {
            String urlStr = "https://api.telegram.org/bot" + botToken.trim() + "/sendMessage";
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            // Build formatted text for Telegram
            String formattedText = "📬 <b>SMS Forwarded</b>\n"
                    + "<b>From:</b> <code>" + escapeHtml(sender) + "</code>\n"
                    + "<b>Time:</b> " + timeStr + "\n\n"
                    + escapeHtml(messageBody);

            JSONObject payload = new JSONObject();
            payload.put("chat_id", chatId.trim());
            payload.put("text", formattedText);
            payload.put("parse_mode", "HTML");

            byte[] outputBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outputBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Log.i(TAG, "Successfully forwarded to Telegram: " + responseCode);
                return true;
            } else {
                String errorBody = readStream(conn.getErrorStream());
                Log.e(TAG, "Telegram API error (" + responseCode + "): " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send to Telegram", e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Synchronously forwards an SMS to a custom HTTP/HTTPS Webhook.
     * Call only from a background thread.
     */
    public static boolean sendWebhookSync(String webhookUrl, String sender, String messageBody) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            Log.e(TAG, "Webhook URL empty");
            return false;
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "SMS-Forwarder-Android");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);

            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            // Multi-platform compatible JSON payload (compatible with Discord, Slack, and generic webhooks)
            JSONObject payload = new JSONObject();
            payload.put("sender", sender);
            payload.put("message", messageBody);
            payload.put("body", messageBody);
            payload.put("timestamp", timeStr);
            // "content" field enables direct Discord webhook compatibility
            payload.put("content", "📬 **SMS from " + sender + "** (" + timeStr + "):\n" + messageBody);
            // "text" field enables Slack incoming webhook compatibility
            payload.put("text", "📬 *SMS from " + sender + "* (" + timeStr + "):\n" + messageBody);

            byte[] outputBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(outputBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Log.i(TAG, "Successfully forwarded to Webhook: " + responseCode);
                return true;
            } else {
                String errorBody = readStream(conn.getErrorStream());
                Log.e(TAG, "Webhook error (" + responseCode + "): " + errorBody);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send to Webhook", e);
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Sends a test message asynchronously to verify user credentials.
     */
    public static void testTelegram(String token, String chatId, ForwardCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = sendTelegramSync(token, chatId, "+1234567890",
                    "This is a test notification from SMS Forwarder. Forwarding is properly configured!");
            deliverResult(callback, success, success ? "Telegram test succeeded!" : "Failed to send to Telegram. Check Bot Token and Chat ID.");
        });
    }

    /**
     * Sends a test webhook asynchronously to verify URL endpoint.
     */
    public static void testWebhook(String webhookUrl, ForwardCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = sendWebhookSync(webhookUrl, "+1234567890",
                    "This is a test notification from SMS Forwarder. Webhook is working!");
            deliverResult(callback, success, success ? "Webhook test succeeded!" : "Failed to reach Webhook. Check URL and server response.");
        });
    }

    /**
     * Sends a test push notification to the configured ntfy topic.
     */
    public static void testNtfy(String server, String topic, ForwardCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = sendNtfySync(server, topic, "SMS Forwarder Test",
                    "This is a test notification from SMS Forwarder! Instant push is working properly.");
            deliverResult(callback, success, success
                    ? "Instant Push test succeeded! Check your ntfy app or browser tab."
                    : "Failed to send to ntfy. Check your topic name and connection.");
        });
    }

    private static void deliverResult(ForwardCallback callback, boolean success, String msg) {
        if (callback != null) {
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(success, msg));
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
