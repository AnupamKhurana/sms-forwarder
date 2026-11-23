package com.smsforwarder;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Lightweight, dependency-free SMTP client supporting SSL (port 465)
 * and STARTTLS (port 587) for sending forwarded SMS notifications via email.
 */
public class SmtpForwarder {

    private static final String TAG = "SmtpForwarder";
    private static final int TIMEOUT_MS = 15000;
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    public interface SmtpCallback {
        void onResult(boolean success, String message);
    }

    /**
     * Synchronously sends an email. Call only from background thread.
     */
    public static boolean sendEmailSync(String host, int port, String username, String password,
                                        String recipient, String sender, String smsBody) {
        if (host == null || host.trim().isEmpty()) host = "smtp.gmail.com";
        if (port <= 0) port = 465;

        String fromAddress = username != null ? username.trim() : "";
        String toAddress = recipient != null && !recipient.trim().isEmpty() ? recipient.trim() : fromAddress;

        if (fromAddress.isEmpty() || password == null || password.isEmpty() || toAddress.isEmpty()) {
            Log.e(TAG, "SMTP credentials incomplete");
            return false;
        }

        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String rfcDate = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(new Date());

        String subject = "📬 SMS from " + sender + " (" + timeStr + ")";
        String emailContent = "You received a new SMS message:\n\n"
                + "Sender: " + sender + "\n"
                + "Date/Time: " + timeStr + "\n\n"
                + "--- Message Content ---\n"
                + smsBody + "\n\n"
                + "— Forwarded automatically by SMS Forwarder";

        Socket socket = null;
        try {
            boolean useDirectSsl = (port == 465);

            if (useDirectSsl) {
                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket();
                sslSocket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                sslSocket.setSoTimeout(TIMEOUT_MS);
                javax.net.ssl.SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(sslParams);
                sslSocket.startHandshake();
                socket = sslSocket;
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // 1. Read Greeting (220)
            String response = readResponse(reader);
            if (!response.startsWith("220")) {
                Log.e(TAG, "SMTP Greeting failed: " + response);
                return false;
            }

            // 2. EHLO
            writer.print("EHLO localhost\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                Log.e(TAG, "SMTP EHLO failed: " + response);
                return false;
            }

            // 3. STARTTLS if on port 587 (or non-465)
            if (!useDirectSsl) {
                writer.print("STARTTLS\r\n");
                writer.flush();
                response = readResponse(reader);
                if (!response.startsWith("220")) {
                    Log.e(TAG, "STARTTLS not supported or failed: " + response);
                    return false;
                }

                SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true);
                sslSocket.setSoTimeout(TIMEOUT_MS);
                javax.net.ssl.SSLParameters sslParams = sslSocket.getSSLParameters();
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(sslParams);
                sslSocket.startHandshake();
                socket = sslSocket;

                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

                // Re-send EHLO after TLS handshake
                writer.print("EHLO localhost\r\n");
                writer.flush();
                response = readResponse(reader);
                if (!response.startsWith("250")) {
                    Log.e(TAG, "EHLO post-STARTTLS failed: " + response);
                    return false;
                }
            }

            // 4. AUTH LOGIN
            writer.print("AUTH LOGIN\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("334")) {
                Log.e(TAG, "AUTH LOGIN failed: " + response);
                return false;
            }

            // Send base64 username
            String userB64 = Base64.encodeToString(fromAddress.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            writer.print(userB64 + "\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("334")) {
                Log.e(TAG, "Username rejected: " + response);
                return false;
            }

            // Send base64 password
            String passB64 = Base64.encodeToString(password.trim().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            writer.print(passB64 + "\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("235")) {
                Log.e(TAG, "Authentication failed: " + response);
                return false;
            }

            // 5. MAIL FROM
            writer.print("MAIL FROM:<" + fromAddress + ">\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                Log.e(TAG, "MAIL FROM failed: " + response);
                return false;
            }

            // 6. RCPT TO
            writer.print("RCPT TO:<" + toAddress + ">\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("250")) {
                Log.e(TAG, "RCPT TO failed: " + response);
                return false;
            }

            // 7. DATA
            writer.print("DATA\r\n");
            writer.flush();
            response = readResponse(reader);
            if (!response.startsWith("354")) {
                Log.e(TAG, "DATA failed: " + response);
                return false;
            }

            // Encode Subject using MIME B-encoding for UTF-8 safety
            String encodedSubject = "=?UTF-8?B?" + Base64.encodeToString(subject.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP) + "?=";

            // Send Email Headers & Body
            writer.print("From: " + fromAddress + "\r\n");
            writer.print("To: " + toAddress + "\r\n");
            writer.print("Date: " + rfcDate + "\r\n");
            writer.print("Subject: " + encodedSubject + "\r\n");
            writer.print("MIME-Version: 1.0\r\n");
            writer.print("Content-Type: text/plain; charset=UTF-8\r\n");
            writer.print("Content-Transfer-Encoding: 8bit\r\n");
            writer.print("\r\n");
            writer.print(emailContent + "\r\n");
            writer.print(".\r\n");
            writer.flush();

            response = readResponse(reader);
            if (!response.startsWith("250")) {
                Log.e(TAG, "Email sending failed: " + response);
                return false;
            }

            // 8. QUIT
            writer.print("QUIT\r\n");
            writer.flush();
            Log.i(TAG, "Email successfully sent to: " + toAddress);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "SMTP Exception", e);
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Sends a test email asynchronously for UI configuration verification.
     */
    public static void testEmailAsync(String host, int port, String username, String password,
                                      String recipient, SmtpCallback callback) {
        EXECUTOR.execute(() -> {
            boolean success = sendEmailSync(host, port, username, password, recipient,
                    "+1234567890", "This is a test notification from SMS Forwarder. Email forwarding is properly configured!");
            String msg = success ? "Email test succeeded! Message delivered." : "Email test failed. Check SMTP host, port, and App Password.";
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onResult(success, msg));
            }
        });
    }

    private static String readResponse(BufferedReader reader) throws Exception {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
            // SMTP multiline replies have '-' after the 3-digit code (e.g. 250-); the final line has space (e.g. 250 )
            if (line.length() >= 4 && line.charAt(3) != '-') {
                break;
            }
        }
        return sb.toString().trim();
    }
}
