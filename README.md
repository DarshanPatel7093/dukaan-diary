# Dukaan Diary (Android, fully offline & secure)

A complete offline management app for shop owners and technicians:
- **💰 To Collect (Customer Udhar)**: customer, product, amount, promised payment date, voice notes.
- **📤 To Pay (Distributor / Cheque Payments)**: distributor name, bank cheque / payment details, clearance date, notes.
- **🔧 Service Visits**: customer, appliance (e.g. RO filter, AC), address, date, and assigned employee / technician.
- **👥 Service Team Management**: Manage employees and dispatch work details via WhatsApp with one tap.

Each entry can have a **voice note** (record, play, re-record, delete) so no typing is needed.

---

## What's New in v1.3

1. **📅 Interactive Calendar on Dashboard**:
   - Tap ◀ / ▶ or pick any date from the calendar to view reminder entries for that specific date.
   - Quick date chips: `Yesterday`, `Today`, `Tomorrow`, `Pick Date 📅`.
   - Real-time KPI summaries for the selected date: **To Collect (₹)**, **To Pay (₹)**, and **Services count**.
2. **⏰ Reminders Every 2 Hours (8:00 AM to 10:00 PM)**:
   - Reminders trigger every 2 hours: 8:00 AM, 10:00 AM, 12:00 PM, 2:00 PM, 4:00 PM, 6:00 PM, 8:00 PM, 10:00 PM.
   - Shows detailed notifications of due payments to collect, cheques to pay, and services.
3. **☰ Left Navigation Drawer**:
   - Access all app sections from the hamburger menu:
     - 📊 Dashboard & Calendar
     - 💰 To Collect (Customer Udhar)
     - 📤 To Pay (Distributor Payments)
     - 🔧 Service Visits
     - 👥 Manage Employees
     - ⚙️ Settings & Theme
     - 🛡️ Backup & Restore
4. **⚙️ App Settings**:
   - **Theme Mode**: Choose between **Follow System Default**, **☀️ Light Theme**, or **🌙 Dark Theme**.
   - **Google Drive Backup ID Setup**: Configure custom Drive Folder ID or account label.
   - **Notification test tool**.
5. **📤 Distributor Payments (To Pay)**:
   - Track payments you owe to distributors/parties (e.g. bank cheques given to Soham Enterprise to withdraw on 20 Nov).
   - Enter party name, amount, cheque number / bank mode, and due date.
6. **👥 Employee / Technician Management**:
   - Add employees with name, mobile number, and role (e.g. RO Technician, Electrician).
   - Quick call, WhatsApp, or delete directly from the employee list.
7. **📲 Service Assignment & WhatsApp Dispatch**:
   - Assign any service visit to a technician with one tap.
   - One-tap button **"📲 Send Details to Employee"** generates a formatted WhatsApp dispatch containing customer name, phone number, address location, scheduled date, and appliance details!
8. **🔘 Card Action Buttons**:
   - **📞 CALL**: One-tap phone dialer.
   - **💬 MSG**: Pre-filled professional WhatsApp/SMS reminders (for customers, distributors, or employee dispatch).
   - **💰 RECEIVED / 💳 PAID / ✓ DONE**: One-tap completion with undo.
   - **🗑️ DELETE**: With safety confirmation.
   - **▶ LISTEN**: Direct audio player on the reminder card.

---

## How to build the APK

### Without Installing Android Studio (Using GitHub Actions)
1. Push changes to GitHub:
   ```bash
   git add .
   git commit -m "Add calendar dashboard, distributor payments, employee dispatch, settings and theme"
   git push origin main
   ```
2. Open your repository on GitHub: `https://github.com/DarshanPatel7093/dukaan-diary`
3. Click on the **Actions** tab at the top.
4. Select the latest **Build APK** workflow run.
5. Once complete (~1-2 minutes), download the **DukaanDiary-apk** artifact zip file.
6. Unzip to get `app-debug.apk` and install on your phone!

---

## App Updates & Data Safety
- Thanks to the permanent keystore configuration, you can install new APK updates directly **over** your existing app without uninstalling—your database and voice notes will remain 100% intact!
- For extra safety, tap **🛡️ Backup** anytime to save a `.dukaan` archive directly to **Google Drive** or local phone storage.
