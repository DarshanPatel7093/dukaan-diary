# Dukaan Diary (Android, fully offline)

Simple app for a shop owner to note:
- **Payments (Udhar)**: customer, product, amount, date the customer promised to pay
- **Service visits**: customer, product (e.g. RO filter), address, date the worker must visit

Each entry can have a **voice note** (record, play, re-record, delete) so no typing is needed.
Any entry or voice note can be deleted (with a confirmation).

## Features
- **Today's Dashboard**: Quick overview of today's pending payments, total money to collect (Rs.), and service visits scheduled for today/overdue, with one-tap filter chips (`All Today`, `Payments`, `Services`).
- **Payments (Udhar)**: Track customer credit, product, amount, promised date, notes, and voice notes.
- **Service Visits**: Track service visits (e.g. RO filter, AC repair), address, scheduled date, notes, and voice notes.
- **Speech-to-Text (Voice Input)**: Tap the mic 🎤 icon in Customer Name, Product, Amount, Address, or Note to speak instead of typing. Mobile number remains manual typing for accuracy.
- **Direct Audio Playback on Dashboard**: Listen to voice notes directly from the reminder card using the `▶ LISTEN` button without opening the entry.
- **Voice Notes**: Stored privately inside the app's internal storage (not in public gallery/music players).
- **100% Offline**: Local SQLite database, runs with no internet permission, no account, no server.
- **Colour Status**: OVERDUE (red), TODAY (orange), TOMORROW (blue), later (green).
- **CALL Button**: One-tap phone dialer with customer's number.
- **DONE Button**: Mark complete with one tap (tick "Show completed" to see history; UNDO available).
- **App Security (App Lock)**: Protect customer records with your phone's Fingerprint, Face, or PIN lock.
- **Google Drive & Phone Storage Backup**: Export your full database and voice recordings into a single backup file directly to Google Drive or phone storage. Restore it anytime with one tap.
- **Data Safety on App Updates**: Installing updated APKs over existing ones preserves all local records without deletion.
- **Daily Reminders**: Offline notifications at 9:00 AM and 5:00 PM when something is due.

## How to build the APK

### Method 1: Without Installing Android Studio (Using GitHub Actions - Recommended)
You do not need to install Android Studio or heavy tools on your computer. GitHub will build the APK for you in the cloud for free:

1. Push this project to your GitHub repository:
   ```bash
   git add .
   git commit -m "Update workflow"
   git push
   ```
2. Open your repository on GitHub in a web browser: `https://github.com/DarshanPatel7093/dukaan-diary`
3. Click on the **Actions** tab at the top.
4. Select the latest **Build APK** workflow run (or click **Run workflow**).
5. Once complete (takes ~1-2 minutes), scroll down to the **Artifacts** section and click **DukaanDiary-apk** to download the zip file.
6. Unzip it to get `app-debug.apk`. Transfer it to your Android phone via WhatsApp, USB cable, or Google Drive, and tap to install!

---

### Method 2: Using Android Studio (Optional)
1. Install Android Studio: https://developer.android.com/studio
2. Open this project folder in Android Studio.
3. Click **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. The generated APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Phone settings for reliable reminders
- Allow Notifications when the app asks (Android 13+).
- On some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) set Battery to "No restrictions"
  / allow auto-start for Dukaan Diary, otherwise the phone may block the reminders.

## Backup & Security
1. **App Lock**: Tap the `🛡️ Backup` button at the top right to enable App Lock. When enabled, your Fingerprint, Face, or PIN is required to open the app.
2. **Backing up to Google Drive or Phone**: Tap `🛡️ Backup` > `Save to Google Drive / Phone`. Select Google Drive in the side menu (or Downloads/Documents) to save your `.dukaan` backup file.
3. **Restoring Data**: If you uninstall the app or move to a new phone, install Dukaan Diary, tap `🛡️ Backup` > `Restore from Google Drive / Phone`, and choose your backup file.
4. **App Updates**: When updating Dukaan Diary with a new APK, simply install it **over** your existing app without uninstalling first — all your records will remain completely intact.
