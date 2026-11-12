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
1. Install Android Studio (free): https://developer.android.com/studio
2. File > Open > select this `DukaanDiary` folder. Let Gradle sync (needs internet only once, to download libraries).
3. Connect an Android phone (enable USB debugging) and press Run, or
   Build > Build Bundle(s) / APK(s) > Build APK(s), then copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and install it.

## Phone settings for reliable reminders
- Allow Notifications when the app asks (Android 13+).
- On some phones (Xiaomi, Oppo, Vivo, Realme, Samsung) set Battery to "No restrictions"
  / allow auto-start for Dukaan Diary, otherwise the phone may block the reminders.

## Backup
Data lives only on the phone. Uninstalling the app deletes it. A backup/export feature can be added later.
