# Google Play Store Listing & Compliance Guide: SMS & Message Forwarder

To successfully publish **SMS & Message Forwarder** (`com.smsforwarder`) on the Google Play Store without rejections, follow this comprehensive asset, policy, and Play Console declaration guide.

---

## 1. Text Assets (Metadata Compliance)

### App Name (Max 30 characters)
*Choose a descriptive name free of prohibited terms (no "Free", "#1", emojis, or CAPITALIZED hype words).*
- **Primary Recommendation:** `SMS & Message Forwarder` *(25 characters)*
- **Alternative 1:** `Auto Message Forwarder` *(22 characters)*
- **Alternative 2:** `Smart Notification Forward` *(26 characters)*

### Short Description (Max 80 characters)
*Clear elevator pitch summarizing the core utility.*
- **Recommended:** `Forward SMS, RCS, and message alerts to Telegram, Instant Push, or Email.` *(74 characters)*

### Full Description (Max 4000 characters)
*Policy-compliant draft detailing features, permissions, and privacy stance without deceptive claims.*

```text
Never miss critical verification codes, bank alerts, or client messages again! SMS & Message Forwarder is a lightweight, privacy-focused automation tool that intercepts incoming text notifications and automatically forwards them to your personal Telegram bot, Instant Push (ntfy.sh) topic, email inbox, or custom webhook based on your custom rules.

🌟 KEY FEATURES
• Universal Messaging Coverage: Forward alerts from Google Messages (SMS and RCS), WhatsApp, WhatsApp Business, and other messaging apps.
• Zero Setup Instant Push: Forward directly to your computer browser or secondary phone with zero accounts or passwords using private ntfy topics.
• Flexible Destinations: Send to Telegram Bot, Email (SMTP with SSL/TLS), or Webhooks (Discord, Slack, Zapier, n8n).
• Smart Keyword Filters: Forward only messages containing specific words (e.g., OTP, alert, verification, bank).
• Sender Whitelist & Blacklist: Filter by specific senders or automatically ignore promotional and spam messages.
• Local History Logs: Review forwarded alerts and timestamps directly on your device. Clear history anytime.
• On-Device Processing: Rule matching happens entirely on your phone. No developer intermediate servers or cloud databases.

🎯 POPULAR USE CASES
• Multi-Device Convenience: Receive 2FA login codes on your PC, laptop, or tablet while your phone is charging in another room.
• Travel & Dual-SIM: Leave your home SIM phone connected to WiFi and receive banking OTPs abroad in your personal Telegram chat or email.
• Team & Family Automation: Share delivery updates, server alerts, or shared account verification codes with family members or team Discord channels.

🔒 PERMISSIONS & PRIVACY NOTICE
SMS & Message Forwarder does NOT require or declare sensitive telephony SMS permissions (READ_SMS or RECEIVE_SMS). Instead, it uses Android's official Notification Access (NotificationListenerService) to detect message alerts on-device according to your filter rules. 

Matching messages are transmitted exclusively to your user-configured personal destination endpoints (Telegram API, ntfy.sh, your mail server, or your webhook) over encrypted TLS/SSL connections. We run no telemetry, ad networks, or analytics trackers.
```

---

## 2. Graphic Assets

### App Icon
- **Size:** 512 x 512 pixels
- **Format:** 32-bit PNG (with alpha)
- **Style:** Clean, high-resolution app icon representing forward messaging. Avoid badges like "Free" or rating stars.

### Feature Graphic
- **Size:** 1024 x 500 pixels
- **Format:** JPEG or 24-bit PNG (no alpha)
- **Content:** Title text and a modern device mockup demonstrating notification forwarding.

### Screenshots
- **Count:** Minimum 4 recommended (portrait 9:16 or landscape 16:9).
- **Recommended Sequence:**
  1. **Dashboard:** Active service status and quick destination toggles.
  2. **Instant Push & Telegram Setup:** Demonstrating simple topic link sharing and bot setup.
  3. **Keyword & Sender Filters:** Showing whitelist/blacklist rules.
  4. **Forwarding Logs:** Transparent in-app log history.

---

## 3. Policy & Permissions Declarations (Play Console)

### A. SMS and Call Log Permission Declaration Form
- **Action:** **NOT REQUIRED / DO NOT FILL OUT**.
- **Reason:** The app does **not** declare `READ_SMS`, `RECEIVE_SMS`, or `SEND_SMS` telephony permissions. It relies on `android.service.notification.NotificationListenerService`.

### B. Foreground Service (FGS) Declaration (Target SDK 34 & 35)
In the Google Play Console under **Policy > App Content > Foreground Service Permissions**:
1. **FGS Type:** Select **`specialUse`**.
2. **Subtype Text:** `Personal notification forwarding monitor`
3. **User-Facing Description (Copy & Paste):**
   > *"SMS & Message Forwarder provides continuous real-time monitoring of incoming text notifications (SMS/RCS and WhatsApp) and dispatches user-filtered messages directly to their personal Telegram bot, Instant Push topic, or email. The foreground service maintains an ongoing notification in the status bar so the user is at all times aware that message monitoring is active and can stop it with a single tap. WorkManager cannot be used because message forwarding requires immediate, low-latency dispatch upon arrival rather than deferrable background batching."*
4. **Video Demonstration Link:** Provide a link to an unlisted YouTube video showing:
   - Launching the app and enabling the switch.
   - The ongoing notification appearing in the Android notification drawer.
   - A notification being received and successfully forwarded.

### C. Prominent Disclosure & Anti-Spyware Compliance
- **Prominent Disclosure:** App presents an upfront in-app consent dialog before redirecting the user to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
- **Anti-Stalking Policy:** App complies with Google Play Stalkerware policies:
  - App displays a visible launcher icon (cannot be hidden).
  - An ongoing, persistent foreground notification is visible whenever monitoring is active.
  - Forwarded messages are logged in a user-visible local log.
  - No marketing claims suggesting covert surveillance or spying on others.

---

## 4. Google Play Console: Data Safety Questionnaire Mapping

Fill in the Data Safety form under **App Content > Data Safety** with these exact selections:

| Questionnaire Field | Response | Justification / Details |
| :--- | :--- | :--- |
| **Does your app collect or share user data?** | **Yes** | Transmits notification message content to user-configured endpoints. |
| **Is all user data encrypted in transit?** | **Yes** | All endpoints (ntfy.sh, Telegram, Webhook, SMTP) enforce HTTPS / SSL / TLS. |
| **Do you provide a way for users to request data deletion?** | **Yes** | Users can tap "Clear All" in the Logs tab at any time to purge stored history. |
| **Data Types > Messages** | Select **SMS or MMS** and **Other in-app messages** | Transmits SMS/RCS and messaging app notification text. |
| **Data Collection vs Sharing for Messages** | **Collected: Yes**<br>**Shared: Yes** (if treating Telegram/ntfy as 3rd party) or **No** | Sent solely to user's personal endpoints; never to developer servers. |
| **Data Processing Purpose** | Select **App functionality** | Solely to deliver forwarded alerts as requested by user. |
| **Is this data linked to the user's identity?** | **No** | The app has no accounts, profiles, or device fingerprinting. |

---

## 5. Privacy Policy Hosting

1. Host the Privacy Policy (from `README.md` or web page) on a public URL (e.g., GitHub Pages or Google Sites):
   `https://<username>.github.io/sms-forwarder/privacy-policy.html`
2. Submit this URL in the Play Console under **App Content > Privacy Policy**.
3. Verify that the in-app "Read Full Privacy Policy" and "Open Online Privacy Policy" buttons in the About screen load successfully.
