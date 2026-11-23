package com.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Wraps SharedPreferences for reading/writing SMS forwarder configuration.
 */
public class ConfigManager {

    private static final String PREFS_NAME = "sms_forwarder_config";
    private static final String KEY_TARGET_NUMBER = "target_number";
    private static final String KEY_KEYWORDS = "keywords";
    private static final String KEY_SOURCE_NUMBERS = "source_numbers";
    private static final String KEY_NEGATIVE_SOURCE_NUMBERS = "negative_source_numbers";
    private static final String KEY_ENABLED = "enabled";

    // Keys for internet-based forwarding and compliance
    public static final String MODE_NTFY = "ntfy";
    public static final String MODE_SMS = "sms";
    public static final String MODE_TELEGRAM = "telegram";
    public static final String MODE_EMAIL = "email";
    public static final String MODE_WEBHOOK = "webhook";
    public static final String MODE_ALL = "all";

    // SMS forward methods: direct (SmsManager), app (Messaging App), auto (direct if permitted, else app)
    public static final String METHOD_AUTO = "auto";
    public static final String METHOD_DIRECT = "direct";
    public static final String METHOD_APP = "app";

    private static final String KEY_SMS_METHOD = "sms_forward_method";
    private static final String KEY_FORWARD_MODE = "forward_mode";
    private static final String KEY_NTFY_TOPIC = "ntfy_topic";
    private static final String KEY_NTFY_SERVER = "ntfy_server";
    private static final String KEY_TELEGRAM_BOT_TOKEN = "telegram_bot_token";
    private static final String KEY_TELEGRAM_CHAT_ID = "telegram_chat_id";
    private static final String KEY_WEBHOOK_URL = "webhook_url";
    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_EMAIL_USERNAME = "email_username";
    private static final String KEY_EMAIL_PASSWORD = "email_password";
    private static final String KEY_EMAIL_RECIPIENT = "email_recipient";
    private static final String KEY_DISCLOSURE_ACCEPTED = "privacy_disclosure_accepted";
    private static final String KEY_MONITOR_ALL_APPS = "monitor_all_apps";

    private final SharedPreferences prefs;

    public ConfigManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getTargetNumber() {
        return prefs.getString(KEY_TARGET_NUMBER, "");
    }

    public void setTargetNumber(String number) {
        prefs.edit().putString(KEY_TARGET_NUMBER, number.trim()).apply();
    }

    public List<String> getKeywords() {
        String raw = prefs.getString(KEY_KEYWORDS, "");
        if (raw.isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getKeywordsRaw() {
        return prefs.getString(KEY_KEYWORDS, "");
    }

    public void setKeywords(String commaSeparated) {
        prefs.edit().putString(KEY_KEYWORDS, commaSeparated.trim()).apply();
    }

    public List<String> getSourceNumbers() {
        String raw = prefs.getString(KEY_SOURCE_NUMBERS, "");
        if (raw.isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getSourceNumbersRaw() {
        return prefs.getString(KEY_SOURCE_NUMBERS, "");
    }

    public void setSourceNumbers(String commaSeparated) {
        prefs.edit().putString(KEY_SOURCE_NUMBERS, commaSeparated.trim()).apply();
    }

    public List<String> getNegativeSourceNumbers() {
        String raw = prefs.getString(KEY_NEGATIVE_SOURCE_NUMBERS, "");
        if (raw.isEmpty()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public String getNegativeSourceNumbersRaw() {
        return prefs.getString(KEY_NEGATIVE_SOURCE_NUMBERS, "");
    }

    public void setNegativeSourceNumbers(String commaSeparated) {
        prefs.edit().putString(KEY_NEGATIVE_SOURCE_NUMBERS, commaSeparated.trim()).apply();
    }

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getForwardMode() {
        return prefs.getString(KEY_FORWARD_MODE, MODE_ALL);
    }

    public void setForwardMode(String mode) {
        prefs.edit().putString(KEY_FORWARD_MODE, mode).apply();
    }

    public boolean isMonitorAllApps() {
        return prefs.getBoolean(KEY_MONITOR_ALL_APPS, false);
    }

    public void setMonitorAllApps(boolean allApps) {
        prefs.edit().putBoolean(KEY_MONITOR_ALL_APPS, allApps).apply();
    }

    public String getNtfyTopic() {
        return prefs.getString(KEY_NTFY_TOPIC, "");
    }

    public void setNtfyTopic(String topic) {
        prefs.edit().putString(KEY_NTFY_TOPIC, topic.trim()).apply();
    }

    public String getNtfyServer() {
        return prefs.getString(KEY_NTFY_SERVER, "https://ntfy.sh");
    }

    public void setNtfyServer(String server) {
        String cleanServer = server != null && !server.trim().isEmpty() ? server.trim() : "https://ntfy.sh";
        if (cleanServer.endsWith("/")) {
            cleanServer = cleanServer.substring(0, cleanServer.length() - 1);
        }
        prefs.edit().putString(KEY_NTFY_SERVER, cleanServer).apply();
    }

    public String getTelegramBotToken() {
        return prefs.getString(KEY_TELEGRAM_BOT_TOKEN, "");
    }

    public void setTelegramBotToken(String token) {
        prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, token.trim()).apply();
    }

    public String getTelegramChatId() {
        return prefs.getString(KEY_TELEGRAM_CHAT_ID, "");
    }

    public void setTelegramChatId(String chatId) {
        prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, chatId.trim()).apply();
    }

    public String getWebhookUrl() {
        return prefs.getString(KEY_WEBHOOK_URL, "");
    }

    public void setWebhookUrl(String url) {
        prefs.edit().putString(KEY_WEBHOOK_URL, url.trim()).apply();
    }

    public boolean isPrivacyDisclosureAccepted() {
        return prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false);
    }

    public void setPrivacyDisclosureAccepted(boolean accepted) {
        prefs.edit().putBoolean(KEY_DISCLOSURE_ACCEPTED, accepted).apply();
    }

    public String getSmtpHost() {
        return prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com");
    }

    public void setSmtpHost(String host) {
        prefs.edit().putString(KEY_SMTP_HOST, host.trim()).apply();
    }

    public int getSmtpPort() {
        return prefs.getInt(KEY_SMTP_PORT, 465);
    }

    public void setSmtpPort(int port) {
        prefs.edit().putInt(KEY_SMTP_PORT, port).apply();
    }

    public String getEmailUsername() {
        return prefs.getString(KEY_EMAIL_USERNAME, "");
    }

    public void setEmailUsername(String username) {
        prefs.edit().putString(KEY_EMAIL_USERNAME, username.trim()).apply();
    }

    public String getEmailPassword() {
        return prefs.getString(KEY_EMAIL_PASSWORD, "");
    }

    public void setEmailPassword(String password) {
        prefs.edit().putString(KEY_EMAIL_PASSWORD, password).apply();
    }

    public String getEmailRecipient() {
        return prefs.getString(KEY_EMAIL_RECIPIENT, "");
    }

    public void setEmailRecipient(String recipient) {
        prefs.edit().putString(KEY_EMAIL_RECIPIENT, recipient.trim()).apply();
    }

    public String getSmsMethod() {
        return prefs.getString(KEY_SMS_METHOD, METHOD_AUTO);
    }

    public void setSmsMethod(String method) {
        prefs.edit().putString(KEY_SMS_METHOD, method != null ? method : METHOD_AUTO).apply();
    }

    public boolean isSmsDestinationEnabled() {
        String mode = getForwardMode();
        return (MODE_SMS.equals(mode) || MODE_ALL.equals(mode))
                && !getTargetNumber().isEmpty();
    }

    public boolean isNtfyDestinationEnabled() {
        String mode = getForwardMode();
        return (MODE_NTFY.equals(mode) || MODE_ALL.equals(mode))
                && !getNtfyTopic().isEmpty();
    }

    public boolean isTelegramDestinationEnabled() {
        String mode = getForwardMode();
        return (MODE_TELEGRAM.equals(mode) || MODE_ALL.equals(mode))
                && !getTelegramBotToken().isEmpty() && !getTelegramChatId().isEmpty();
    }

    public boolean isWebhookDestinationEnabled() {
        String mode = getForwardMode();
        return (MODE_WEBHOOK.equals(mode) || MODE_ALL.equals(mode))
                && !getWebhookUrl().isEmpty();
    }

    public boolean isEmailDestinationEnabled() {
        String mode = getForwardMode();
        return (MODE_EMAIL.equals(mode) || MODE_ALL.equals(mode))
                && !getEmailUsername().isEmpty() && !getEmailPassword().isEmpty();
    }

    public boolean isDestinationConfigured() {
        String mode = getForwardMode();
        if (MODE_NTFY.equals(mode)) {
            return !getNtfyTopic().isEmpty();
        } else if (MODE_SMS.equals(mode)) {
            return !getTargetNumber().isEmpty();
        } else if (MODE_TELEGRAM.equals(mode)) {
            return !getTelegramBotToken().isEmpty() && !getTelegramChatId().isEmpty();
        } else if (MODE_WEBHOOK.equals(mode)) {
            return !getWebhookUrl().isEmpty();
        } else if (MODE_EMAIL.equals(mode)) {
            return !getEmailUsername().isEmpty() && !getEmailPassword().isEmpty();
        } else if (MODE_ALL.equals(mode)) {
            return !getNtfyTopic().isEmpty() ||
                   !getTargetNumber().isEmpty() ||
                   (!getTelegramBotToken().isEmpty() && !getTelegramChatId().isEmpty()) ||
                   !getWebhookUrl().isEmpty() ||
                   (!getEmailUsername().isEmpty() && !getEmailPassword().isEmpty());
        }
        return false;
    }
}
