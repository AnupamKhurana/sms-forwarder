package com.smsforwarder;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.widget.SwitchCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST_CODE = 100;

    private ConfigManager config;
    private ForwardLog forwardLog;

    // Home screen views
    private SwitchCompat switchEnabled;
    private View statusIndicator;
    private TextView tvStatus;
    private TextView tvForwardMode;
    private TextView tvSmsSummary;
    private TextView tvNtfySummary;
    private TextView tvTelegramSummary;
    private TextView tvWebhookSummary;
    private TextView tvEmailSummary;
    private TextView tvKeywords;
    private TextView tvSourceNumbers;
    private TextView tvNegativeSourceNumbers;
    private TextView tvMonitoredApps;
    private View rowSms;
    private View rowNtfy;
    private View rowTelegram;
    private View rowWebhook;
    private View rowEmail;
    private View rowMonitoredApps;
    private View divSms;
    private View divNtfy;
    private View divTelegram;
    private View divWebhook;
    private View divEmail;
    private MaterialButton btnTestForwarding;

    // Setup guidance
    private View setupGuidanceCard;
    private TextView tvGuidanceMessage;

    // Logs screen views
    private MaterialButton btnClearLog;
    private LinearLayout logContainer;
    private TextView tvEmptyLog;

    // Screens
    private View screenHome;
    private View screenLogs;
    private View screenAbout;
    private com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        config = new ConfigManager(this);
        forwardLog = new ForwardLog(this);

        initViews();
        checkAndPromptProminentDisclosure();
        refreshSummary();
        if (config.isEnabled()) {
            startForwarderService();
        }
        checkAndShowSetupGuidance();
        refreshLog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (config.isEnabled()) {
            startForwarderService();
        }
        refreshSummary();
        refreshLog();
        checkAndShowSetupGuidance();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Status card
        switchEnabled = findViewById(R.id.switch_enabled);
        statusIndicator = findViewById(R.id.status_indicator);
        tvStatus = findViewById(R.id.tv_status);

        // Summary card
        tvForwardMode = findViewById(R.id.tv_forward_mode);
        tvSmsSummary = findViewById(R.id.tv_sms_summary);
        tvNtfySummary = findViewById(R.id.tv_ntfy_summary);
        tvTelegramSummary = findViewById(R.id.tv_telegram_summary);
        tvWebhookSummary = findViewById(R.id.tv_webhook_summary);
        tvEmailSummary = findViewById(R.id.tv_email_summary);
        tvKeywords = findViewById(R.id.tv_keywords);
        tvSourceNumbers = findViewById(R.id.tv_source_numbers);
        tvNegativeSourceNumbers = findViewById(R.id.tv_negative_source_numbers);
        tvMonitoredApps = findViewById(R.id.tv_monitored_apps);

        rowSms = findViewById(R.id.row_sms);
        rowNtfy = findViewById(R.id.row_ntfy);
        rowTelegram = findViewById(R.id.row_telegram);
        rowWebhook = findViewById(R.id.row_webhook);
        rowEmail = findViewById(R.id.row_email);
        rowMonitoredApps = findViewById(R.id.row_monitored_apps);
        divSms = findViewById(R.id.div_sms);
        divNtfy = findViewById(R.id.div_ntfy);
        divTelegram = findViewById(R.id.div_telegram);
        divWebhook = findViewById(R.id.div_webhook);
        divEmail = findViewById(R.id.div_email);
        btnTestForwarding = findViewById(R.id.btn_test_forwarding);

        // Logs
        btnClearLog = findViewById(R.id.btn_clear_log);
        logContainer = findViewById(R.id.log_container);
        tvEmptyLog = findViewById(R.id.tv_empty_log);

        // Screens
        screenHome = findViewById(R.id.screen_home);
        screenLogs = findViewById(R.id.screen_logs);
        screenAbout = findViewById(R.id.screen_about);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        setupBottomNavigation();
        setupSwitchListener();
        setupRowClickListeners();

        btnClearLog.setOnClickListener(v -> {
            forwardLog.clear();
            refreshLog();
            Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show();
        });

        if (btnTestForwarding != null) {
            btnTestForwarding.setOnClickListener(v -> handleTestForwarding());
        }
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                showScreen(screenHome, "SMS Forwarder");
                return true;
            } else if (itemId == R.id.nav_logs) {
                showScreen(screenLogs, "Forwarding Logs");
                refreshLog();
                return true;
            } else if (itemId == R.id.nav_about) {
                showScreen(screenAbout, "About");
                return true;
            }
            return false;
        });

        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void showScreen(View screenToShow, String title) {
        screenHome.setVisibility(View.GONE);
        screenLogs.setVisibility(View.GONE);
        screenAbout.setVisibility(View.GONE);
        screenToShow.setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    private void setupSwitchListener() {
        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            if (isChecked) {
                if (!config.isDestinationConfigured()) {
                    Toast.makeText(this,
                            "Please configure your active forwarding destination first",
                            Toast.LENGTH_SHORT).show();
                    buttonView.setChecked(false);
                    showDestinationConfigForActiveMode();
                    return;
                }
            }

            if (isChecked) {
                startForwarderService();
                requestBatteryOptimizationExemption();
                AutoStartUtils.checkAndPromptOptionalAutoStart(MainActivity.this);
                ServiceWatchdog.enqueue(this);
            } else {
                stopForwarderService();
                ServiceWatchdog.cancel(this);
            }
            config.setEnabled(isChecked);
            updateStatusUI();
            checkAndShowSetupGuidance();
        });
    }

    private void showDestinationConfigForActiveMode() {
        String mode = config.getForwardMode();
        if (ConfigManager.MODE_SMS.equals(mode)) {
            showSmsConfigDialog();
        } else if (ConfigManager.MODE_NTFY.equals(mode)) {
            showNtfyConfigDialog();
        } else if (ConfigManager.MODE_TELEGRAM.equals(mode)) {
            showTelegramConfigDialog();
        } else if (ConfigManager.MODE_WEBHOOK.equals(mode)) {
            showWebhookConfigDialog();
        } else if (ConfigManager.MODE_EMAIL.equals(mode)) {
            showEmailConfigDialog();
        } else {
            showForwardModeDialog();
        }
    }

    private void setupRowClickListeners() {
        // Forwarding mode selector
        findViewById(R.id.row_forward_mode).setOnClickListener(v -> showForwardModeDialog());

        // SMS / Messaging App settings
        if (rowSms != null) {
            rowSms.setOnClickListener(v -> showSmsConfigDialog());
        }

        // Instant Push settings
        if (rowNtfy != null) {
            rowNtfy.setOnClickListener(v -> showNtfyConfigDialog());
        }

        // Telegram settings
        if (rowTelegram != null) {
            rowTelegram.setOnClickListener(v -> showTelegramConfigDialog());
        }

        // Webhook settings
        if (rowWebhook != null) {
            rowWebhook.setOnClickListener(v -> showWebhookConfigDialog());
        }

        // Email settings
        if (rowEmail != null) {
            rowEmail.setOnClickListener(v -> showEmailConfigDialog());
        }

        findViewById(R.id.row_keywords).setOnClickListener(v ->
                showEditDialog("Forward only if message contains",
                        "Separate multiple words with commas. Leave empty to forward all messages.\n\nNote: If source senders are also configured, an SMS must match BOTH the source AND a keyword to be forwarded.",
                        config.getKeywordsRaw(),
                        InputType.TYPE_CLASS_TEXT,
                        "e.g. OTP, alert, verification",
                        value -> {
                            config.setKeywords(value);
                            refreshSummary();
                        }));

        findViewById(R.id.row_source_numbers).setOnClickListener(v ->
                showEditDialog("Only forward from these senders",
                        "Separate multiple senders with commas. Leave empty to accept messages from anyone.",
                        config.getSourceNumbersRaw(),
                        InputType.TYPE_CLASS_TEXT,
                        "e.g. +91987654, HDFC, Amazon",
                        value -> {
                            config.setSourceNumbers(value);
                            refreshSummary();
                        }));

        findViewById(R.id.row_negative_source_numbers).setOnClickListener(v ->
                showEditDialog("Never forward from these senders",
                        "Messages from these senders will always be ignored. Separate with commas.",
                        config.getNegativeSourceNumbersRaw(),
                        InputType.TYPE_CLASS_TEXT,
                        "e.g. AD-SPAM, 12345",
                        value -> {
                            config.setNegativeSourceNumbers(value);
                            refreshSummary();
                        }));

        if (rowMonitoredApps != null) {
            rowMonitoredApps.setOnClickListener(v -> showMonitoredAppsDialog());
        }

        View btnReadPolicy = findViewById(R.id.btn_read_privacy_policy);
        if (btnReadPolicy != null) {
            btnReadPolicy.setOnClickListener(v -> showFullPrivacyPolicyDialog());
        }

        View btnOpenPolicy = findViewById(R.id.btn_open_privacy_policy);
        if (btnOpenPolicy != null) {
            btnOpenPolicy.setOnClickListener(v -> openOnlinePrivacyPolicy());
        }
    }

    private void showForwardModeDialog() {
        String[] modes = {
                "💬 SMS (Messaging App / Phone Number)",
                "🔔 Instant Push (ntfy.sh — Zero setup, recommended)",
                "✈️ Telegram Bot",
                "🔗 Webhook / Discord",
                "📧 Email (SMTP)",
                "🌐 All Configured Channels"
        };

        String currentMode = config.getForwardMode();
        int selectedIndex = 0;
        if (ConfigManager.MODE_SMS.equals(currentMode)) selectedIndex = 0;
        else if (ConfigManager.MODE_NTFY.equals(currentMode)) selectedIndex = 1;
        else if (ConfigManager.MODE_TELEGRAM.equals(currentMode)) selectedIndex = 2;
        else if (ConfigManager.MODE_WEBHOOK.equals(currentMode)) selectedIndex = 3;
        else if (ConfigManager.MODE_EMAIL.equals(currentMode)) selectedIndex = 4;
        else if (ConfigManager.MODE_ALL.equals(currentMode)) selectedIndex = 5;
        else selectedIndex = 0;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Forwarding Destination")
                .setSingleChoiceItems(modes, selectedIndex, (dialog, which) -> {
                    String newMode;
                    switch (which) {
                        case 0: newMode = ConfigManager.MODE_SMS; break;
                        case 1: newMode = ConfigManager.MODE_NTFY; break;
                        case 2: newMode = ConfigManager.MODE_TELEGRAM; break;
                        case 3: newMode = ConfigManager.MODE_WEBHOOK; break;
                        case 4: newMode = ConfigManager.MODE_EMAIL; break;
                        case 5: newMode = ConfigManager.MODE_ALL; break;
                        default: newMode = ConfigManager.MODE_SMS; break;
                    }
                    config.setForwardMode(newMode);
                    refreshSummary();
                    requestPermissions();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showMonitoredAppsDialog() {
        String[] options = {
                "Messages & WhatsApp (Default, recommended)",
                "All Installed Apps (Forward notifications across all apps)"
        };
        int selected = config.isMonitorAllApps() ? 1 : 0;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Monitored Apps")
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    config.setMonitorAllApps(which == 1);
                    refreshSummary();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSmsConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView tvHelp = new TextView(this);
        tvHelp.setText("Forward incoming messages to a recipient phone number via SMS or your phone's default Messaging App.\n\n• Automatic (Recommended): Sends directly via background SMS if SEND_SMS permission is granted; otherwise prepares a draft in your Messaging App.\n• Direct Background SMS: Uses telephony SmsManager directly (requires SMS permission).\n• Via Messaging App: Prepares an outgoing SMS in Google Messages / Samsung Messages with a notification prompt.");
        tvHelp.setTextColor(getResources().getColor(R.color.on_surface_dim, getTheme()));
        tvHelp.setTextSize(13f);
        tvHelp.setPadding(0, 0, 0, padding / 2);
        layout.addView(tvHelp);

        TextInputLayout targetLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        targetLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        targetLayout.setHint("Target Phone Number (e.g. +919876543210)");
        TextInputEditText targetEdit = new TextInputEditText(targetLayout.getContext());
        targetEdit.setInputType(InputType.TYPE_CLASS_PHONE);
        targetEdit.setText(config.getTargetNumber());
        targetEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        targetLayout.addView(targetEdit);
        layout.addView(targetLayout);

        layout.addView(createSpacer(padding / 2));

        // Method selector
        TextView tvMethodLabel = new TextView(this);
        tvMethodLabel.setText("Forwarding Method:");
        tvMethodLabel.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        tvMethodLabel.setTextSize(14f);
        layout.addView(tvMethodLabel);

        final String[] methodOptions = {
                "⚡ Automatic (Direct SMS if permitted, else Messaging App)",
                "📡 Direct Background SMS (requires SEND_SMS permission)",
                "📱 Via Default Messaging App (Intent / Notification)"
        };
        final String[] methodValues = {
                ConfigManager.METHOD_AUTO,
                ConfigManager.METHOD_DIRECT,
                ConfigManager.METHOD_APP
        };

        String currentMethod = config.getSmsMethod();
        int defaultMethodIdx = 0;
        for (int i = 0; i < methodValues.length; i++) {
            if (methodValues[i].equals(currentMethod)) {
                defaultMethodIdx = i;
                break;
            }
        }

        android.widget.Spinner methodSpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, methodOptions);
        methodSpinner.setAdapter(adapter);
        methodSpinner.setSelection(defaultMethodIdx);
        layout.addView(methodSpinner);

        layout.addView(createSpacer(padding / 2));

        // Action buttons
        LinearLayout buttonBar = new LinearLayout(this);
        buttonBar.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnPermission = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        boolean hasSmsPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
        btnPermission.setText(hasSmsPerm ? "✅ SMS Permission Granted" : "🔑 Request SEND_SMS Permission");
        btnPermission.setOnClickListener(v -> {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.SEND_SMS}, PERM_REQUEST_CODE + 1);
        });
        buttonBar.addView(btnPermission);

        MaterialButton btnOpenApp = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnOpenApp.setText("💬 Test in Default Messaging App");
        btnOpenApp.setOnClickListener(v -> {
            String target = targetEdit.getText() != null ? targetEdit.getText().toString().trim() : "";
            if (target.isEmpty()) {
                Toast.makeText(this, "Please enter a target phone number first", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent smsIntent = SmsForwarder.createMessagingAppIntent(target, "Test notification from SMS Forwarder");
                startActivity(smsIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to launch messaging app", Toast.LENGTH_SHORT).show();
            }
        });
        buttonBar.addView(btnOpenApp);

        layout.addView(buttonBar);

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(this);
        scrollView.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("SMS Forwarding Setup")
                .setView(scrollView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String target = targetEdit.getText() != null ? targetEdit.getText().toString().trim() : "";
                    int chosenIdx = methodSpinner.getSelectedItemPosition();
                    String chosenMethod = chosenIdx >= 0 && chosenIdx < methodValues.length
                            ? methodValues[chosenIdx] : ConfigManager.METHOD_AUTO;

                    config.setTargetNumber(target);
                    config.setSmsMethod(chosenMethod);
                    refreshSummary();

                    if (!ConfigManager.METHOD_APP.equals(chosenMethod)
                            && ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.SEND_SMS}, PERM_REQUEST_CODE + 1);
                    }
                    Toast.makeText(this, "SMS forwarding settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    config.setTargetNumber("");
                    refreshSummary();
                })
                .show();
    }

    private void showNtfyConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView tvHelp = new TextView(this);
        tvHelp.setText("Zero accounts or passwords needed!\n\n1. Pick or generate a unique Topic Code.\n2. Tap 'Share Link with Receiver' to send it to the other device via WhatsApp or text.\n3. The receiver opens the link in their browser or the free ntfy app to get instant alerts.\n\n🔒 Privacy & Security Notice:\nPublic ntfy.sh topics are accessible to anyone with the exact topic code. For sensitive 2FA OTPs or banking alerts, always use a long, unguessable topic (use the Random Generator below) or a private self-hosted server.");
        tvHelp.setTextColor(getResources().getColor(R.color.on_surface_dim, getTheme()));
        tvHelp.setTextSize(13f);
        tvHelp.setPadding(0, 0, 0, padding / 2);
        layout.addView(tvHelp);

        TextInputLayout topicLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        topicLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        topicLayout.setHint("Topic Code (e.g. family-alert-924)");
        TextInputEditText topicEdit = new TextInputEditText(topicLayout.getContext());
        topicEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        topicEdit.setText(config.getNtfyTopic());
        topicEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        topicLayout.addView(topicEdit);
        layout.addView(topicLayout);

        layout.addView(createSpacer(padding / 3));

        // Quick action buttons
        LinearLayout buttonBar = new LinearLayout(this);
        buttonBar.setOrientation(LinearLayout.VERTICAL);

        MaterialButton btnGenerate = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnGenerate.setText("🎲 Generate Random Topic Code");
        btnGenerate.setOnClickListener(v -> {
            String randomCode = "alerts-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            topicEdit.setText(randomCode);
        });
        buttonBar.addView(btnGenerate);

        MaterialButton btnShare = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnShare.setText("📤 Share Link with Receiver");
        btnShare.setOnClickListener(v -> {
            String topic = topicEdit.getText() != null ? topicEdit.getText().toString().trim() : "";
            if (topic.isEmpty()) {
                Toast.makeText(this, "Please enter or generate a Topic Code first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Subscribe to forwarded alerts");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Subscribe to my forwarded alerts here: https://ntfy.sh/" + topic);
            startActivity(Intent.createChooser(shareIntent, "Share with Receiver"));
        });
        buttonBar.addView(btnShare);

        MaterialButton btnOpenWeb = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnOpenWeb.setText("🌐 Open Web Viewer in Browser");
        btnOpenWeb.setOnClickListener(v -> {
            String topic = topicEdit.getText() != null ? topicEdit.getText().toString().trim() : "";
            if (topic.isEmpty()) {
                Toast.makeText(this, "Please enter or generate a Topic Code first", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ntfy.sh/" + topic));
                startActivity(browserIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open browser", Toast.LENGTH_SHORT).show();
            }
        });
        buttonBar.addView(btnOpenWeb);

        layout.addView(buttonBar);

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(this);
        scrollView.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Instant Push (ntfy.sh) Setup")
                .setView(scrollView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String topic = topicEdit.getText() != null ? topicEdit.getText().toString().trim() : "";
                    config.setNtfyTopic(topic);
                    refreshSummary();
                    Toast.makeText(this, "Instant push settings saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    config.setNtfyTopic("");
                    refreshSummary();
                })
                .show();
    }

    private void showTelegramConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextInputLayout tokenLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        tokenLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        tokenLayout.setHint("Telegram Bot Token");
        TextInputEditText tokenEdit = new TextInputEditText(tokenLayout.getContext());
        tokenEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        tokenEdit.setText(config.getTelegramBotToken());
        tokenEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        tokenLayout.addView(tokenEdit);

        TextInputLayout chatIdLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        chatIdLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        chatIdLayout.setHint("Telegram Chat ID");
        TextInputEditText chatIdEdit = new TextInputEditText(chatIdLayout.getContext());
        chatIdEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        chatIdEdit.setText(config.getTelegramChatId());
        chatIdEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        chatIdLayout.addView(chatIdEdit);

        layout.addView(tokenLayout);
        layout.addView(createSpacer(padding / 2));
        layout.addView(chatIdLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Telegram Bot Setup")
                .setMessage("1. Create a bot using @BotFather to get your Bot Token.\n2. Message @userinfobot to get your Chat ID.")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    String token = tokenEdit.getText() != null ? tokenEdit.getText().toString().trim() : "";
                    String chatId = chatIdEdit.getText() != null ? chatIdEdit.getText().toString().trim() : "";
                    config.setTelegramBotToken(token);
                    config.setTelegramChatId(chatId);
                    refreshSummary();
                })
                .setNeutralButton("Clear", (dialog, which) -> {
                    config.setTelegramBotToken("");
                    config.setTelegramChatId("");
                    refreshSummary();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWebhookConfigDialog() {
        showEditDialog("Webhook / Discord URL",
                "Enter an HTTP or HTTPS endpoint (supports Discord Webhooks, Slack, Zapier, n8n, or custom servers).",
                config.getWebhookUrl(),
                InputType.TYPE_TEXT_VARIATION_URI,
                "https://discord.com/api/webhooks/...",
                value -> {
                    config.setWebhookUrl(value);
                    refreshSummary();
                });
    }

    private void showEmailConfigDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextInputLayout hostLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        hostLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        hostLayout.setHint("SMTP Server Host");
        TextInputEditText hostEdit = new TextInputEditText(hostLayout.getContext());
        hostEdit.setText(config.getSmtpHost());
        hostEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        hostLayout.addView(hostEdit);

        TextInputLayout portLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        portLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        portLayout.setHint("SMTP Port (465 SSL or 587 TLS)");
        TextInputEditText portEdit = new TextInputEditText(portLayout.getContext());
        portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portEdit.setText(String.valueOf(config.getSmtpPort()));
        portEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        portLayout.addView(portEdit);

        TextInputLayout userLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        userLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        userLayout.setHint("Sender Email (Username)");
        TextInputEditText userEdit = new TextInputEditText(userLayout.getContext());
        userEdit.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        userEdit.setText(config.getEmailUsername());
        userEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        userLayout.addView(userEdit);

        TextInputLayout passLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        passLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        passLayout.setHint("Password / Gmail App Password");
        TextInputEditText passEdit = new TextInputEditText(passLayout.getContext());
        passEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passEdit.setText(config.getEmailPassword());
        passEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        passLayout.addView(passEdit);

        TextInputLayout recipientLayout = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        recipientLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        recipientLayout.setHint("Recipient Email (optional, defaults to sender)");
        TextInputEditText recipientEdit = new TextInputEditText(recipientLayout.getContext());
        recipientEdit.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        recipientEdit.setText(config.getEmailRecipient());
        recipientEdit.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        recipientLayout.addView(recipientEdit);

        layout.addView(hostLayout);
        layout.addView(createSpacer(padding / 3));
        layout.addView(portLayout);
        layout.addView(createSpacer(padding / 3));
        layout.addView(userLayout);
        layout.addView(createSpacer(padding / 3));
        layout.addView(passLayout);
        layout.addView(createSpacer(padding / 3));
        layout.addView(recipientLayout);

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(this);
        scrollView.addView(layout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Email (SMTP) Setup")
                .setMessage("Tip for Gmail: Use smtp.gmail.com (Port 465) and generate a 16-character App Password from Google Account > Security > 2-Step Verification > App passwords.")
                .setView(scrollView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String host = hostEdit.getText() != null ? hostEdit.getText().toString().trim() : "smtp.gmail.com";
                    int port = 465;
                    try {
                        port = Integer.parseInt(portEdit.getText().toString().trim());
                    } catch (Exception ignored) {}
                    String username = userEdit.getText() != null ? userEdit.getText().toString().trim() : "";
                    String password = passEdit.getText() != null ? passEdit.getText().toString().trim() : "";
                    String recipient = recipientEdit.getText() != null ? recipientEdit.getText().toString().trim() : "";

                    config.setSmtpHost(host);
                    config.setSmtpPort(port);
                    config.setEmailUsername(username);
                    config.setEmailPassword(password);
                    config.setEmailRecipient(recipient);
                    refreshSummary();
                })
                .setNeutralButton("Clear", (dialog, which) -> {
                    config.setEmailUsername("");
                    config.setEmailPassword("");
                    config.setEmailRecipient("");
                    refreshSummary();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private View createSpacer(int height) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
        return spacer;
    }

    private void handleTestForwarding() {
        String mode = config.getForwardMode();
        Toast.makeText(this, "Sending test message…", Toast.LENGTH_SHORT).show();

        if (ConfigManager.MODE_SMS.equals(mode) || ConfigManager.MODE_ALL.equals(mode)) {
            if (config.getTargetNumber().isEmpty()) {
                Toast.makeText(this, "Please configure Target Phone Number first", Toast.LENGTH_LONG).show();
                showSmsConfigDialog();
                return;
            }
            SmsForwarder.testSms(this, config, (success, msg) ->
                    Toast.makeText(this, "[SMS] " + msg, Toast.LENGTH_LONG).show());
        }

        if (ConfigManager.MODE_NTFY.equals(mode) || ConfigManager.MODE_ALL.equals(mode)) {
            if (config.getNtfyTopic().isEmpty()) {
                Toast.makeText(this, "Please configure Instant Push Topic first", Toast.LENGTH_LONG).show();
                showNtfyConfigDialog();
                return;
            }
            NetworkForwarder.testNtfy(config.getNtfyServer(), config.getNtfyTopic(),
                    (success, msg) -> Toast.makeText(this, "[Instant Push] " + msg, Toast.LENGTH_LONG).show());
        }

        if (ConfigManager.MODE_TELEGRAM.equals(mode) || ConfigManager.MODE_ALL.equals(mode)) {
            if (config.getTelegramBotToken().isEmpty() || config.getTelegramChatId().isEmpty()) {
                Toast.makeText(this, "Please configure Telegram Bot Token and Chat ID first", Toast.LENGTH_LONG).show();
                showTelegramConfigDialog();
                return;
            }
            NetworkForwarder.testTelegram(config.getTelegramBotToken(), config.getTelegramChatId(),
                    (success, msg) -> Toast.makeText(this, "[Telegram] " + msg, Toast.LENGTH_LONG).show());
        }

        if (ConfigManager.MODE_WEBHOOK.equals(mode) || ConfigManager.MODE_ALL.equals(mode)) {
            if (config.getWebhookUrl().isEmpty()) {
                Toast.makeText(this, "Please configure Webhook URL first", Toast.LENGTH_LONG).show();
                showWebhookConfigDialog();
                return;
            }
            NetworkForwarder.testWebhook(config.getWebhookUrl(),
                    (success, msg) -> Toast.makeText(this, "[Webhook] " + msg, Toast.LENGTH_LONG).show());
        }

        if (ConfigManager.MODE_EMAIL.equals(mode) || ConfigManager.MODE_ALL.equals(mode)) {
            if (!config.isEmailDestinationEnabled()) {
                Toast.makeText(this, "Please configure Email sender and password first", Toast.LENGTH_LONG).show();
                showEmailConfigDialog();
                return;
            }
            SmtpForwarder.testEmailAsync(config.getSmtpHost(), config.getSmtpPort(),
                    config.getEmailUsername(), config.getEmailPassword(),
                    config.getEmailRecipient(),
                    (success, msg) -> Toast.makeText(this, "[Email] " + msg, Toast.LENGTH_LONG).show());
        }
    }

    private void showEditDialog(String title, String message, String currentValue,
                                int inputType, String hint, OnValueSaved callback) {

        TextInputLayout inputLayout = new TextInputLayout(this,
                null, com.google.android.material.R.attr.textInputOutlinedStyle);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setHint(hint);

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setInputType(inputType);
        editText.setText(currentValue);
        editText.setTextColor(getResources().getColor(R.color.on_surface, getTheme()));
        inputLayout.addView(editText);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(inputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = editText.getText() != null
                            ? editText.getText().toString().trim() : "";
                    callback.onSaved(value);
                })
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Clear", (dialog, which) -> {
                    callback.onSaved("");
                })
                .show();
    }

    private void refreshSummary() {
        String mode = config.getForwardMode();

        // Forward Mode UI
        if (tvForwardMode != null) {
            if (ConfigManager.MODE_SMS.equals(mode)) {
                tvForwardMode.setText("SMS (Messaging App)");
            } else if (ConfigManager.MODE_NTFY.equals(mode)) {
                tvForwardMode.setText("Instant Push (ntfy)");
            } else if (ConfigManager.MODE_TELEGRAM.equals(mode)) {
                tvForwardMode.setText("Telegram Bot");
            } else if (ConfigManager.MODE_WEBHOOK.equals(mode)) {
                tvForwardMode.setText("Webhook / Discord");
            } else if (ConfigManager.MODE_EMAIL.equals(mode)) {
                tvForwardMode.setText("Email (SMTP)");
            } else {
                tvForwardMode.setText("All Configured Channels");
            }
        }

        // Toggle visibility of destination rows based on selected mode
        boolean showSms = ConfigManager.MODE_SMS.equals(mode) || ConfigManager.MODE_ALL.equals(mode);
        boolean showNtfy = ConfigManager.MODE_NTFY.equals(mode) || ConfigManager.MODE_ALL.equals(mode);
        boolean showTelegram = ConfigManager.MODE_TELEGRAM.equals(mode) || ConfigManager.MODE_ALL.equals(mode);
        boolean showWebhook = ConfigManager.MODE_WEBHOOK.equals(mode) || ConfigManager.MODE_ALL.equals(mode);
        boolean showEmail = ConfigManager.MODE_EMAIL.equals(mode) || ConfigManager.MODE_ALL.equals(mode);

        if (rowSms != null) rowSms.setVisibility(showSms ? View.VISIBLE : View.GONE);
        if (divSms != null) divSms.setVisibility(showSms ? View.VISIBLE : View.GONE);
        if (rowNtfy != null) rowNtfy.setVisibility(showNtfy ? View.VISIBLE : View.GONE);
        if (divNtfy != null) divNtfy.setVisibility(showNtfy ? View.VISIBLE : View.GONE);
        if (rowTelegram != null) rowTelegram.setVisibility(showTelegram ? View.VISIBLE : View.GONE);
        if (divTelegram != null) divTelegram.setVisibility(showTelegram ? View.VISIBLE : View.GONE);
        if (rowWebhook != null) rowWebhook.setVisibility(showWebhook ? View.VISIBLE : View.GONE);
        if (divWebhook != null) divWebhook.setVisibility(showWebhook ? View.VISIBLE : View.GONE);
        if (rowEmail != null) rowEmail.setVisibility(showEmail ? View.VISIBLE : View.GONE);
        if (divEmail != null) divEmail.setVisibility(showEmail ? View.VISIBLE : View.GONE);

        // Monitored Apps summary
        if (tvMonitoredApps != null) {
            tvMonitoredApps.setText(config.isMonitorAllApps() ? "All Apps" : "Messages & WhatsApp");
        }

        // SMS summary
        if (tvSmsSummary != null) {
            String target = config.getTargetNumber();
            if (!target.isEmpty()) {
                String method = config.getSmsMethod();
                String methodDesc = ConfigManager.METHOD_DIRECT.equals(method) ? "Direct SMS" :
                        (ConfigManager.METHOD_APP.equals(method) ? "Messaging App" : "Auto");
                tvSmsSummary.setText(target + " (" + methodDesc + ")");
            } else {
                tvSmsSummary.setText("Not configured — tap to setup");
            }
        }

        // Instant Push (ntfy) summary
        if (tvNtfySummary != null) {
            String topic = config.getNtfyTopic();
            if (!topic.isEmpty()) {
                tvNtfySummary.setText("Topic: " + topic);
            } else {
                tvNtfySummary.setText("Not configured — tap to setup");
            }
        }

        // Telegram summary
        if (tvTelegramSummary != null) {
            String token = config.getTelegramBotToken();
            String chatId = config.getTelegramChatId();
            if (!token.isEmpty() && !chatId.isEmpty()) {
                tvTelegramSummary.setText("Configured (Chat ID: " + chatId + ")");
            } else {
                tvTelegramSummary.setText("Not configured — tap to setup");
            }
        }

        // Webhook summary
        if (tvWebhookSummary != null) {
            String webhook = config.getWebhookUrl();
            if (!webhook.isEmpty()) {
                tvWebhookSummary.setText(webhook);
            } else {
                tvWebhookSummary.setText("Not configured — tap to setup");
            }
        }

        // Email summary
        if (tvEmailSummary != null) {
            String user = config.getEmailUsername();
            String recipient = config.getEmailRecipient();
            if (!user.isEmpty()) {
                String targetEmail = recipient.isEmpty() ? user : recipient;
                tvEmailSummary.setText("Configured (" + targetEmail + ")");
            } else {
                tvEmailSummary.setText("Not configured — tap to setup");
            }
        }

        // Keywords
        String keywords = config.getKeywordsRaw();
        tvKeywords.setText(keywords.isEmpty() ? "All messages" : keywords);

        // Source numbers
        String sources = config.getSourceNumbersRaw();
        tvSourceNumbers.setText(sources.isEmpty() ? "Anyone" : sources);

        // Negative source numbers
        String negSources = config.getNegativeSourceNumbersRaw();
        tvNegativeSourceNumbers.setText(negSources.isEmpty() ? "No exclusions" : negSources);

        updateStatusUI();
    }

    private void updateStatusUI() {
        boolean enabled = config.isEnabled();
        switchEnabled.setChecked(enabled);
        if (enabled) {
            tvStatus.setText("Active — monitoring messages");
            statusIndicator.setBackgroundResource(R.drawable.dot_green);
        } else {
            tvStatus.setText("Inactive — forwarding paused");
            statusIndicator.setBackgroundResource(R.drawable.dot_red);
        }
    }

    private void refreshLog() {
        logContainer.removeAllViews();
        List<JSONObject> entries = forwardLog.getEntries();

        if (entries.isEmpty()) {
            tvEmptyLog.setVisibility(View.VISIBLE);
            btnClearLog.setVisibility(View.GONE);
        } else {
            tvEmptyLog.setVisibility(View.GONE);
            btnClearLog.setVisibility(View.VISIBLE);
        }

        for (JSONObject entry : entries) {
            View logItem = getLayoutInflater().inflate(R.layout.log_item, logContainer, false);

            TextView tvTimestamp = logItem.findViewById(R.id.tv_log_timestamp);
            TextView tvSender = logItem.findViewById(R.id.tv_log_sender);
            TextView tvMessage = logItem.findViewById(R.id.tv_log_message);
            View statusDot = logItem.findViewById(R.id.log_status_dot);

            tvTimestamp.setText(entry.optString("timestamp", "—"));
            String sender = entry.optString("sender", "Unknown");
            String channel = entry.optString("channel", "");
            if (!channel.isEmpty()) {
                tvSender.setText(sender + " • " + channel);
            } else {
                tvSender.setText(sender);
            }
            tvMessage.setText(entry.optString("message", ""));

            boolean isSent = "sent".equals(entry.optString("status"));
            statusDot.setBackgroundResource(isSent
                    ? R.drawable.dot_green : R.drawable.dot_red);

            logContainer.addView(logItem);
        }
    }

    private void startForwarderService() {
        Intent serviceIntent = new Intent(this, ForwarderService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopForwarderService() {
        Intent serviceIntent = new Intent(this, ForwarderService.class);
        stopService(serviceIntent);
    }

    private boolean isNotificationServiceEnabled() {
        return NotificationManagerCompat.getEnabledListenerPackages(this)
                .contains(getPackageName());
    }

    private void requestBatteryOptimizationExemption() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
                    Toast.makeText(this,
                            "Please disable battery optimization for this app in Settings",
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Checks notification access, battery optimization, service status, and shows guidance
     * banner if the user needs to take action for reliable operation.
     */
    private void checkAndShowSetupGuidance() {
        if (setupGuidanceCard == null) {
            setupGuidanceCard = findViewById(R.id.setup_guidance_card);
            tvGuidanceMessage = findViewById(R.id.tv_guidance_message);
        }
        if (setupGuidanceCard == null) return;

        if (!config.isEnabled()) {
            setupGuidanceCard.setVisibility(View.GONE);
            return;
        }

        List<String> issues = new ArrayList<>();

        // Check Notification Access
        if (!isNotificationServiceEnabled()) {
            issues.add("⚠️ Notification Access is required to detect incoming messages. Tap here to grant access in Settings.");
        }

        // Check battery optimization
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            issues.add("⚠️ Battery optimization is ON — the system may kill background services. Tap here to fix.");
        }

        // Samsung-specific tip
        if (Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                issues.add("📱 Samsung: Go to Settings > Battery > Background usage limits > Never sleeping apps and add this app.");
            }
        }

        if (issues.isEmpty()) {
            setupGuidanceCard.setVisibility(View.GONE);
        } else {
            setupGuidanceCard.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < issues.size(); i++) {
                sb.append(issues.get(i));
                if (i < issues.size() - 1) sb.append("\n\n");
            }
            tvGuidanceMessage.setText(sb.toString());

            setupGuidanceCard.setOnClickListener(v -> {
                if (!isNotificationServiceEnabled()) {
                    requestNotificationAccess();
                } else {
                    requestBatteryOptimizationExemption();
                }
            });
        }
    }

    /**
     * Google Play Prominent In-App Disclosure Dialog for Notification Access.
     */
    private void checkAndPromptProminentDisclosure() {
        if (config.isPrivacyDisclosureAccepted()) {
            requestNotificationAccess();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Notification & Message Access Notice")
                .setMessage("SMS & Message Forwarder requires Notification Access to detect incoming messages (such as OTP verification codes and transaction alerts) from Google Messages (SMS & RCS), WhatsApp, and other selected apps, evaluate your filters, and forward matching texts.\n\n"
                        + "📋 Data Access & Usage:\n"
                        + "• Data Accessed: Notification text, sender names, and alert timestamps.\n"
                        + "• Local Processing: All keyword matching and sender filtering occurs 100% locally on this device.\n"
                        + "• Direct Transmission: Matching messages are dispatched solely to your configured destinations (Instant Push via ntfy.sh, Telegram Bot, custom Webhook, or SMTP Email) over encrypted TLS/SSL connections.\n"
                        + "• Zero Developer Access: We do not operate intermediate servers. Your messages are never collected, stored, or sold to third parties or advertisers.\n"
                        + "• User Control: You can customize rules, delete forwarding history, or revoke Notification Access at any time in system settings.\n\n"
                        + "Do you agree to grant Notification Access for message forwarding?")
                .setPositiveButton("I Agree & Continue", (dialog, which) -> {
                    config.setPrivacyDisclosureAccepted(true);
                    requestNotificationAccess();
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    Toast.makeText(this,
                            "Notification access is required to monitor and forward messages.",
                            Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("Privacy Policy", (dialog, which) -> openOnlinePrivacyPolicy())
                .setCancelable(false)
                .show();
    }

    private void openOnlinePrivacyPolicy() {
        String url = "https://github.com/sms-forwarder/privacy-policy";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open browser. View in-app policy instead.", Toast.LENGTH_SHORT).show();
            showFullPrivacyPolicyDialog();
        }
    }

    private void showFullPrivacyPolicyDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Privacy Policy & Data Safety")
                .setMessage("SMS & Message Forwarder — Privacy Policy\n\n"
                        + "1. Data Collection & Access:\n"
                        + "The app requests Notification Access (android.service.notification.NotificationListenerService) strictly to inspect incoming notification alerts from SMS/RCS messaging apps and WhatsApp. The app does NOT request or declare telephony SMS permissions (READ_SMS, RECEIVE_SMS, SEND_SMS).\n\n"
                        + "2. Data Processing & Forwarding:\n"
                        + "All filtering rules (keywords, sender whitelist/blacklist) are evaluated strictly on-device. When a message matches your criteria, it is dispatched directly and exclusively to your configured destination (Instant Push via ntfy.sh, Telegram Bot API, user-specified Webhook, or SMTP Email) over encrypted TLS/SSL.\n\n"
                        + "3. Third-Party Sharing & Telemetry:\n"
                        + "No analytics SDKs, advertising networks, or tracking libraries are included in this application. No messages are ever uploaded to any developer-owned or intermediary servers.\n\n"
                        + "4. Data Retention & Deletion:\n"
                        + "Forwarding history is kept exclusively in local private device storage (capped at 50 entries) and can be cleared at any time via the 'Clear Log' button.\n\n"
                        + "5. Revoking Permissions:\n"
                        + "You can revoke Notification Access at any time via Android Settings > Special App Access > Device & App Notifications.")
                .setPositiveButton("Close", null)
                .setNeutralButton("Open Web Page", (dialog, which) -> openOnlinePrivacyPolicy())
                .show();
    }

    private void requestNotificationAccess() {
        if (!config.isPrivacyDisclosureAccepted()) {
            checkAndPromptProminentDisclosure();
            return;
        }

        if (!isNotificationServiceEnabled()) {
            try {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(intent);
                Toast.makeText(this, "Please enable Notification Access for SMS Forwarder", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open Notification Access settings", Toast.LENGTH_SHORT).show();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERM_REQUEST_CODE);
            }
        }
    }

    private void requestPermissions() {
        requestNotificationAccess();

        if (config.isSmsDestinationEnabled()
                && !ConfigManager.METHOD_APP.equals(config.getSmsMethod())) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS}, PERM_REQUEST_CODE + 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /** Callback interface for edit dialog save. */
    private interface OnValueSaved {
        void onSaved(String value);
    }
}
