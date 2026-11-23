package com.smsforwarder;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages a JSON-based forwarding log stored in internal storage.
 * Capped at MAX_ENTRIES (50) to prevent unbounded growth.
 */
public class ForwardLog {

    private static final String LOG_FILE = "forward_log.json";
    private static final int MAX_ENTRIES = 50;
    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private final File logFile;
    private static final Object FILE_LOCK = new Object();
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor();

    public ForwardLog(Context context) {
        logFile = new File(context.getFilesDir(), LOG_FILE);
    }

    /**
     * Add a log entry for a forwarded (or failed) SMS.
     */
    public void addEntry(final String sender, final String messageSnippet, final boolean success) {
        addEntry(sender, messageSnippet, success, "");
    }

    /**
     * Add a log entry with channel indicator (e.g. SMS, Telegram, Webhook).
     */
    public void addEntry(final String sender, final String messageSnippet, final boolean success, final String channel) {
        LOG_EXECUTOR.execute(() -> {
            synchronized (FILE_LOCK) {
                List<JSONObject> entries = readEntries();

                try {
                    JSONObject entry = new JSONObject();
                    entry.put("timestamp", DATE_FMT.format(new Date()));
                    entry.put("sender", sender);
                    entry.put("message", messageSnippet.length() > 80
                            ? messageSnippet.substring(0, 80) + "…" : messageSnippet);
                    entry.put("status", success ? "sent" : "failed");
                    if (channel != null && !channel.isEmpty()) {
                        entry.put("channel", channel);
                    }

                    entries.add(0, entry); // newest first

                    // Trim to max size
                    while (entries.size() > MAX_ENTRIES) {
                        entries.remove(entries.size() - 1);
                    }

                    writeEntries(entries);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Get all log entries as a list of JSON objects.
     */
    public List<JSONObject> getEntries() {
        synchronized (FILE_LOCK) {
            return readEntries();
        }
    }

    /**
     * Get the log as a JSON array string for display.
     */
    public String toJsonString() {
        synchronized (FILE_LOCK) {
            JSONArray arr = new JSONArray();
            for (JSONObject obj : readEntries()) {
                arr.put(obj);
            }
            return arr.toString();
        }
    }

    /**
     * Clear all log entries.
     */
    public void clear() {
        LOG_EXECUTOR.execute(() -> {
            synchronized (FILE_LOCK) {
                writeEntries(new ArrayList<>());
            }
        });
    }

    private List<JSONObject> readEntries() {
        List<JSONObject> entries = new ArrayList<>();
        if (!logFile.exists()) return entries;

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                entries.add(arr.getJSONObject(i));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return entries;
    }

    private void writeEntries(List<JSONObject> entries) {
        JSONArray arr = new JSONArray();
        for (JSONObject obj : entries) {
            arr.put(obj);
        }
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(arr.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
