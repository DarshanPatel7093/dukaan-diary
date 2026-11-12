# Dukaan Diary (Android, fully offline)

Simple app for a shop owner to note:
- **Payments (Udhar)**: customer, product, amount, date the customer promised to pay
- **Service visits**: customer, product (e.g. RO filter), address, date the worker must visit

Each entry can have a **voice note** (record, play, re-record, delete) so no typing is needed.
Any entry or voice note can be deleted (with a confirmation).

## Features
- Local SQLite database, no internet needed, no account, no server
- Two tabs: Payments / Service visits, sorted by date (earliest first)
- Colour status: OVERDUE (red), TODAY (orange), TOMORROW (blue), later (green)
- Banner at top: how many payments/services are due today or overdue
- CALL button opens the phone dialer with the customer's number
- DONE button marks complete (hidden from the list; tick "Show completed" to see them; UNDO available)
- Reminder notifications every day at 9:00 AM and 5:00 PM when something is due or overdue
- Voice notes are stored privately inside the app (not in the gallery)

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

## Backup
Data lives only on the phone. Uninstalling the app deletes it. A backup/export feature can be added later.
