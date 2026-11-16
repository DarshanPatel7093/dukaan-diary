package com.shop.dukaan

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var adapter: LeadAdapter

    // Navigation Drawer
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnOpenDrawer: ImageView

    // Dashboard views
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var tvSelectedDate: TextView
    private lateinit var tvSelectedDateSub: TextView
    private lateinit var btnPrevDate: MaterialButton
    private lateinit var btnNextDate: MaterialButton
    private lateinit var layoutDateClick: View
    private lateinit var chipGroupDates: ChipGroup
    private lateinit var chipDateYesterday: Chip
    private lateinit var chipDateToday: Chip
    private lateinit var chipDateTomorrow: Chip
    private lateinit var chipDatePick: Chip

    // KPI Cards on Dashboard
    private lateinit var cardTodayPayments: MaterialCardView
    private lateinit var cardToPay: MaterialCardView
    private lateinit var cardTodayServices: MaterialCardView
    private lateinit var tvDashPaymentAmount: TextView
    private lateinit var tvDashPaymentCount: TextView
    private lateinit var tvDashToPayAmount: TextView
    private lateinit var tvDashToPayCount: TextView
    private lateinit var tvDashServiceCount: TextView
    private lateinit var tvDashServiceDetail: TextView
    private lateinit var tvDashboardRemindersTitle: TextView

    // Filter Chips for Dashboard
    private lateinit var chipGroupDashboard: ChipGroup
    private lateinit var chipFilterAll: Chip
    private lateinit var chipFilterPayments: Chip
    private lateinit var chipFilterToPay: Chip
    private lateinit var chipFilterServices: Chip

    // General UI
    private lateinit var tvHeaderDate: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var cbShowDone: CheckBox
    private lateinit var tabs: TabLayout
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnBackupSecurity: MaterialButton

    // Google Drive Quick Backup & Restore Views
    private lateinit var cardDriveBackup: MaterialCardView
    private lateinit var tvLastBackupStatus: TextView
    private lateinit var btnQuickDriveBackup: MaterialButton
    private lateinit var cardRestorePrompt: MaterialCardView
    private lateinit var btnQuickRestoreDrive: MaterialButton

    // Lock Overlay UI
    private lateinit var layoutLockOverlay: View
    private lateinit var btnUnlockApp: MaterialButton

    private var currentTabPosition = 0
    private var selectedDateMillis = startOfDay()
    private var player: MediaPlayer? = null

    // Preferences
    private val PREFS_SECURITY = "dukaan_security"
    private val PREFS_SETTINGS = "dukaan_settings"
    private val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private val KEY_THEME_MODE = "theme_mode"
    private val KEY_DRIVE_FOLDER_ID = "drive_folder_id"

    private var isUnlockedForSession = false
    private var tempBackupFileForExport: File? = null

    private val moneyFmt = NumberFormat.getInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }
    private val dateDisplayFmt = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.ENGLISH)
    private val shortDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val targetUri = result.data!!.data!!
                tempBackupFileForExport?.let { file ->
                    val success = BackupManager.writeBackupToUri(this, file, targetUri)
                    if (success) {
                        val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                        getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_BACKUP_TIME, timeStr)
                            .apply()
                        updateLastBackupDisplay()
                        Toast.makeText(this, "✅ Backup saved safely to Google Drive / Phone!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Failed to write backup", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val sourceUri = result.data!!.data!!
                stopPlayback()
                val success = BackupManager.restoreBackup(this, sourceUri)
                if (success) {
                    db = Db(this)
                    refresh()
                    Toast.makeText(this, "✅ All records, payments & voice notes restored!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Could not restore backup. Please select a valid Dukaan Diary backup file.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val appUnlockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                isUnlockedForSession = true
                layoutLockOverlay.visibility = View.GONE
            } else {
                isUnlockedForSession = false
                layoutLockOverlay.visibility = View.VISIBLE
            }
        }

    private val toggleLockAuthLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val newState = !isAppLockEnabled()
                setAppLockEnabled(newState)
                isUnlockedForSession = true
                Toast.makeText(this, if (newState) "🔒 App Lock enabled" else "🔓 App Lock disabled", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme preference
        applySavedTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = Db(this)

        // Find drawer and toolbar views
        drawerLayout = findViewById(R.id.drawerLayout)
        btnOpenDrawer = findViewById(R.id.btnOpenDrawer)
        btnOpenDrawer.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        setupNavigationDrawer()

        tvHeaderDate = findViewById(R.id.tvHeaderDate)
        tabs = findViewById(R.id.tabs)

        // Dashboard views
        layoutDashboard = findViewById(R.id.layoutDashboard)
        tvSelectedDate = findViewById(R.id.tvSelectedDate)
        tvSelectedDateSub = findViewById(R.id.tvSelectedDateSub)
        btnPrevDate = findViewById(R.id.btnPrevDate)
        btnNextDate = findViewById(R.id.btnNextDate)
        layoutDateClick = findViewById(R.id.layoutDateClick)
        chipGroupDates = findViewById(R.id.chipGroupDates)
        chipDateYesterday = findViewById(R.id.chipDateYesterday)
        chipDateToday = findViewById(R.id.chipDateToday)
        chipDateTomorrow = findViewById(R.id.chipDateTomorrow)
        chipDatePick = findViewById(R.id.chipDatePick)

        cardTodayPayments = findViewById(R.id.cardTodayPayments)
        cardToPay = findViewById(R.id.cardToPay)
        cardTodayServices = findViewById(R.id.cardTodayServices)
        tvDashPaymentAmount = findViewById(R.id.tvDashPaymentAmount)
        tvDashPaymentCount = findViewById(R.id.tvDashPaymentCount)
        tvDashToPayAmount = findViewById(R.id.tvDashToPayAmount)
        tvDashToPayCount = findViewById(R.id.tvDashToPayCount)
        tvDashServiceCount = findViewById(R.id.tvDashServiceCount)
        tvDashServiceDetail = findViewById(R.id.tvDashServiceDetail)
        tvDashboardRemindersTitle = findViewById(R.id.tvDashboardRemindersTitle)

        chipGroupDashboard = findViewById(R.id.chipGroupDashboard)
        chipFilterAll = findViewById(R.id.chipFilterAll)
        chipFilterPayments = findViewById(R.id.chipFilterPayments)
        chipFilterToPay = findViewById(R.id.chipFilterToPay)
        chipFilterServices = findViewById(R.id.chipFilterServices)
        btnBackupSecurity = findViewById(R.id.btnBackupSecurity)

        cardDriveBackup = findViewById(R.id.cardDriveBackup)
        tvLastBackupStatus = findViewById(R.id.tvLastBackupStatus)
        btnQuickDriveBackup = findViewById(R.id.btnQuickDriveBackup)
        cardRestorePrompt = findViewById(R.id.cardRestorePrompt)
        btnQuickRestoreDrive = findViewById(R.id.btnQuickRestoreDrive)

        cardDriveBackup.setOnClickListener { startBackupExport() }
        btnQuickDriveBackup.setOnClickListener { startBackupExport() }
        btnQuickRestoreDrive.setOnClickListener { confirmRestore() }
        updateLastBackupDisplay()

        tvSummary = findViewById(R.id.tvSummary)
        tvEmpty = findViewById(R.id.tvEmpty)
        cbShowDone = findViewById(R.id.cbShowDone)
        val recycler: RecyclerView = findViewById(R.id.recycler)
        btnAdd = findViewById(R.id.btnAdd)

        layoutLockOverlay = findViewById(R.id.layoutLockOverlay)
        btnUnlockApp = findViewById(R.id.btnUnlockApp)

        // Today's header date
        val dateHeaderFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        tvHeaderDate.text = "Today: " + dateHeaderFmt.format(Date())

        // Recycler & Adapter setup
        adapter = LeadAdapter(
            onCall = { l ->
                if (l.phone.isNotBlank()) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + l.phone)))
                } else {
                    toast("No phone number available")
                }
            },
            onMessage = { l -> handleMessageAction(l) },
            onDone = { l ->
                stopPlayback()
                val newDone = !l.done
                db.setDone(l.id, newDone)
                if (newDone) {
                    when (l.type) {
                        TYPE_PAYMENT -> toast("💰 Payment marked as received!")
                        TYPE_PAYMENT_PAY -> toast("💳 Payment marked as paid!")
                        else -> toast("✓ Service visit marked as done!")
                    }
                } else {
                    toast("Restored to pending")
                }
                refresh()
            },
            onDelete = { l ->
                stopPlayback()
                confirmDelete(l)
            },
            onOpen = { l ->
                stopPlayback()
                openEdit(l.id, l.type)
            },
            onPlayVoice = { l -> handlePlayVoice(l) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Setup 4 Navigation Tabs
        tabs.addTab(tabs.newTab().setText("📊 DASHBOARD"))
        tabs.addTab(tabs.newTab().setText("💰 TO COLLECT"))
        tabs.addTab(tabs.newTab().setText("📤 TO PAY"))
        tabs.addTab(tabs.newTab().setText("🔧 SERVICES"))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                stopPlayback()
                currentTabPosition = tab.position
                updateTabVisibility()
                refresh()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Calendar Date Controls
        btnPrevDate.setOnClickListener {
            selectedDateMillis = addDays(selectedDateMillis, -1)
            updateDateChipsState()
            refresh()
        }
        btnNextDate.setOnClickListener {
            selectedDateMillis = addDays(selectedDateMillis, 1)
            updateDateChipsState()
            refresh()
        }
        layoutDateClick.setOnClickListener { pickCalendarDate() }
        chipDatePick.setOnClickListener { pickCalendarDate() }

        chipDateYesterday.setOnClickListener {
            selectedDateMillis = addDays(startOfDay(), -1)
            refresh()
        }
        chipDateToday.setOnClickListener {
            selectedDateMillis = startOfDay()
            refresh()
        }
        chipDateTomorrow.setOnClickListener {
            selectedDateMillis = addDays(startOfDay(), 1)
            refresh()
        }

        // Dashboard card shortcuts
        cardTodayPayments.setOnClickListener {
            if (chipFilterPayments.isChecked) chipFilterAll.isChecked = true else chipFilterPayments.isChecked = true
        }
        cardToPay.setOnClickListener {
            if (chipFilterToPay.isChecked) chipFilterAll.isChecked = true else chipFilterToPay.isChecked = true
        }
        cardTodayServices.setOnClickListener {
            if (chipFilterServices.isChecked) chipFilterAll.isChecked = true else chipFilterServices.isChecked = true
        }

        chipGroupDashboard.setOnCheckedChangeListener { _, _ -> refresh() }
        cbShowDone.setOnCheckedChangeListener { _, _ -> refresh() }

        btnBackupSecurity.setOnClickListener { showBackupSecurityDialog() }

        btnAdd.setOnClickListener {
            stopPlayback()
            when (currentTabPosition) {
                1 -> openEdit(0L, TYPE_PAYMENT)
                2 -> openEdit(0L, TYPE_PAYMENT_PAY)
                3 -> openEdit(0L, TYPE_SERVICE)
                else -> showAddChoiceDialog()
            }
        }

        btnUnlockApp.setOnClickListener { requestAppUnlock() }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Reminder.schedule(this)

        updateDateChipsState()
    }

    private fun setupNavigationDrawer() {
        findViewById<View>(R.id.navDashboard).setOnClickListener {
            tabs.getTabAt(0)?.select()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.navToCollect).setOnClickListener {
            tabs.getTabAt(1)?.select()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.navToPay).setOnClickListener {
            tabs.getTabAt(2)?.select()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.navServices).setOnClickListener {
            tabs.getTabAt(3)?.select()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        findViewById<View>(R.id.navEmployees).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showEmployeesDialog()
        }
        findViewById<View>(R.id.navSettings).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showSettingsDialog()
        }
        findViewById<View>(R.id.navBackup).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showBackupSecurityDialog()
        }
    }

    private fun pickCalendarDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance()
                c.set(year, month, dayOfMonth, 0, 0, 0)
                c.set(Calendar.MILLISECOND, 0)
                selectedDateMillis = c.timeInMillis
                updateDateChipsState()
                refresh()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateChipsState() {
        val diffDays = ((selectedDateMillis - startOfDay()) / 86_400_000L).toInt()
        when (diffDays) {
            -1 -> chipDateYesterday.isChecked = true
            0 -> chipDateToday.isChecked = true
            1 -> chipDateTomorrow.isChecked = true
            else -> chipGroupDates.clearCheck()
        }
    }

    private fun showAddChoiceDialog() {
        val choices = arrayOf(
            "💰 Customer Udhar (To Collect)",
            "📤 Distributor Payment (To Pay / Cheque)",
            "🔧 Service Visit (RO, AC, Repair)"
        )
        AlertDialog.Builder(this)
            .setTitle("Add New Entry")
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> openEdit(0L, TYPE_PAYMENT)
                    1 -> openEdit(0L, TYPE_PAYMENT_PAY)
                    2 -> openEdit(0L, TYPE_SERVICE)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        checkAppSecurityOnResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    // ==================== SECURITY & APP LOCK ====================

    private fun isAppLockEnabled(): Boolean =
        getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE).getBoolean(KEY_APP_LOCK_ENABLED, false)

    private fun setAppLockEnabled(enabled: Boolean) {
        getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_LOCK_ENABLED, enabled)
            .apply()
    }

    private fun checkAppSecurityOnResume() {
        if (isAppLockEnabled() && !isUnlockedForSession) {
            layoutLockOverlay.visibility = View.VISIBLE
            requestAppUnlock()
        } else {
            layoutLockOverlay.visibility = View.GONE
        }
    }

    private fun requestAppUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            isUnlockedForSession = true
            layoutLockOverlay.visibility = View.GONE
            return
        }
        val intent = km.createConfirmDeviceCredentialIntent("Unlock Dukaan Diary", "Confirm your fingerprint, face, or PIN to continue")
        if (intent != null) {
            try {
                appUnlockLauncher.launch(intent)
            } catch (_: Exception) {
                isUnlockedForSession = true
                layoutLockOverlay.visibility = View.GONE
            }
        } else {
            isUnlockedForSession = true
            layoutLockOverlay.visibility = View.GONE
        }
    }

    private fun updateTabVisibility() {
        val isDashboard = currentTabPosition == 0
        layoutDashboard.visibility = if (isDashboard) View.VISIBLE else View.GONE
        tvSummary.visibility = if (isDashboard) View.GONE else View.VISIBLE

        when (currentTabPosition) {
            0 -> btnAdd.text = "+  ADD NEW REMINDER"
            1 -> btnAdd.text = "+  ADD CUSTOMER UDHAR"
            2 -> btnAdd.text = "+  ADD DISTRIBUTOR PAYMENT"
            3 -> btnAdd.text = "+  ADD SERVICE VISIT"
        }
    }

    private fun refresh() {
        val isDashboard = currentTabPosition == 0
        val totalAllCount = db.countAll()

        // Show prompt to restore if database has zero records
        cardRestorePrompt.visibility = if (isDashboard && totalAllCount == 0) View.VISIBLE else View.GONE

        if (isDashboard) {
            refreshDashboard()
        } else {
            refreshSingleTab()
        }
    }

    private fun refreshDashboard() {
        // 1. Update Calendar Header Text
        val diffDays = ((selectedDateMillis - startOfDay()) / 86_400_000L).toInt()
        val dateLabel = dateDisplayFmt.format(Date(selectedDateMillis))
        val subLabel = when {
            diffDays == 0 -> "Today"
            diffDays == -1 -> "Yesterday"
            diffDays == 1 -> "Tomorrow"
            diffDays < -1 -> "${-diffDays} days ago"
            else -> "In $diffDays days"
        }
        tvSelectedDate.text = "📅 $dateLabel"
        tvSelectedDateSub.text = "$subLabel • Tap to pick date ▾"
        tvDashboardRemindersTitle.text = "📅 Reminders for $subLabel"

        // 2. Fetch statistics for selected date
        val stats = db.getDashboardStatsForDate(selectedDateMillis)
        tvDashPaymentAmount.text = "₹ " + moneyFmt.format(stats.toCollectAmount)
        tvDashPaymentCount.text = "${stats.toCollectCount} pending"

        tvDashToPayAmount.text = "₹ " + moneyFmt.format(stats.toPayAmount)
        tvDashToPayCount.text = "${stats.toPayCount} cheques/pay"

        tvDashServiceCount.text = "${stats.serviceCount}"
        tvDashServiceDetail.text = if (stats.isToday) "Due today / overdue" else "Scheduled visits"

        // 3. Filter type for Dashboard
        val typeFilter = when {
            chipFilterPayments.isChecked -> TYPE_PAYMENT
            chipFilterToPay.isChecked -> TYPE_PAYMENT_PAY
            chipFilterServices.isChecked -> TYPE_SERVICE
            else -> null
        }

        val list = db.listForDate(selectedDateMillis, typeFilter, cbShowDone.isChecked)
        adapter.submit(list)

        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = if (list.isEmpty()) {
            "No reminders scheduled for $subLabel.\nTap + below to add a reminder."
        } else ""
    }

    private fun refreshSingleTab() {
        val type = when (currentTabPosition) {
            1 -> TYPE_PAYMENT
            2 -> TYPE_PAYMENT_PAY
            else -> TYPE_SERVICE
        }

        val list = db.list(type, cbShowDone.isChecked)
        adapter.submit(list)

        val pending = list.filter { !it.done }
        if (currentTabPosition == 1) {
            val total = pending.sumOf { it.amount }
            tvSummary.text = "💰 ${pending.size} customers owe you: ₹ " + moneyFmt.format(total)
            tvSummary.setTextColor(Color.parseColor("#1565C0"))
            tvSummary.setBackgroundColor(Color.parseColor("#E3F2FD"))
        } else if (currentTabPosition == 2) {
            val total = pending.sumOf { it.amount }
            tvSummary.text = "📤 ${pending.size} payments to pay distributors: ₹ " + moneyFmt.format(total)
            tvSummary.setTextColor(Color.parseColor("#C2410C"))
            tvSummary.setBackgroundColor(Color.parseColor("#FFEDD5"))
        } else {
            tvSummary.text = "🔧 ${pending.size} service visits pending"
            tvSummary.setTextColor(Color.parseColor("#512DA8"))
            tvSummary.setBackgroundColor(Color.parseColor("#EDE7F6"))
        }

        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        tvEmpty.text = when {
            list.isNotEmpty() -> ""
            currentTabPosition == 1 -> "No customer payment records yet.\nTap + to add an udhar."
            currentTabPosition == 2 -> "No distributor payments scheduled.\nTap + to note cheques/payments to pay."
            else -> "No service visits scheduled.\nTap + to add one."
        }
    }

    // ==================== MESSAGING & DISPATCH ====================

    private fun handleMessageAction(l: Lead) {
        val isService = l.type == TYPE_SERVICE
        val hasAssignedEmp = isService && l.assignedEmployeePhone.isNotBlank()

        if (hasAssignedEmp) {
            val options = arrayOf(
                "📲 Send Details to Employee (${l.assignedEmployeeName})",
                "💬 Message Customer (${l.name})"
            )
            AlertDialog.Builder(this)
                .setTitle("Select Message Recipient")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        sendServiceDispatchToEmployee(l)
                    } else {
                        sendReminderToParty(l)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            sendReminderToParty(l)
        }
    }

    private fun sendReminderToParty(l: Lead) {
        val phone = l.phone.trim()
        if (phone.isEmpty()) {
            toast("No phone number available")
            return
        }

        val dateStr = shortDateFmt.format(Date(l.dateMillis))
        val text = when (l.type) {
            TYPE_PAYMENT -> {
                "Hello ${l.name},\nGentle reminder from Dukaan Diary regarding pending payment of ₹${moneyFmt.format(l.amount)}" +
                    (if (l.product.isNotBlank()) " for ${l.product}" else "") +
                    " due on $dateStr. Thank you!"
            }
            TYPE_PAYMENT_PAY -> {
                "Hello ${l.name},\nPayment of ₹${moneyFmt.format(l.amount)}" +
                    (if (l.product.isNotBlank()) " (${l.product})" else "") +
                    " is scheduled for $dateStr. Thank you!"
            }
            else -> {
                "Hello ${l.name},\nReminder regarding service visit for ${l.product} scheduled on $dateStr. Thank you!"
            }
        }

        openWhatsAppOrSms(phone, text)
    }

    private fun sendServiceDispatchToEmployee(l: Lead) {
        val empPhone = l.assignedEmployeePhone.trim()
        val dateStr = dateDisplayFmt.format(Date(l.dateMillis))

        val msg = StringBuilder().apply {
            append("🔧 *Service Visit Assignment [Dukaan Diary]*\n")
            append("👤 Customer: ${l.name}\n")
            if (l.phone.isNotBlank()) append("📞 Customer Mobile: ${l.phone}\n")
            if (l.address.isNotBlank()) append("📍 Location / Address: ${l.address}\n")
            if (l.product.isNotBlank()) append("🛠️ Appliance / Product: ${l.product}\n")
            append("📅 Scheduled Date: $dateStr\n")
            if (l.note.isNotBlank()) append("📝 Notes: ${l.note}\n")
        }.toString()

        openWhatsAppOrSms(empPhone, msg)
    }

    private fun openWhatsAppOrSms(phone: String, text: String) {
        val cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
        val formattedPhone = if (cleanPhone.length == 10) "91$cleanPhone" else cleanPhone
        try {
            val waIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=" + Uri.encode(text))
            }
            startActivity(waIntent)
        } catch (_: Exception) {
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$cleanPhone")
                    putExtra("sms_body", text)
                }
                startActivity(smsIntent)
            } catch (_: Exception) {
                toast("Could not open WhatsApp or SMS app")
            }
        }
    }

    // ==================== AUDIO PLAYBACK ====================

    private fun handlePlayVoice(l: Lead) {
        val path = l.audioPath ?: return
        if (adapter.playingId == l.id) {
            stopPlayback()
            return
        }

        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            mp.setOnCompletionListener {
                stopPlayback()
            }
            mp.start()
            player = mp
            adapter.playingId = l.id
        } catch (e: Exception) {
            stopPlayback()
            toast("Could not play audio recording")
        }
    }

    private fun stopPlayback() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
        adapter.playingId = null
    }

    private fun openEdit(id: Long, type: String) {
        startActivity(
            Intent(this, EditActivity::class.java).apply {
                putExtra("id", id)
                putExtra("type", type)
            }
        )
    }

    private fun confirmDelete(l: Lead) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage("Name: ${l.name}\nThis cannot be undone.")
            .setPositiveButton("YES, DELETE") { _, _ ->
                db.deleteWithAudio(l)
                refresh()
            }
            .setNegativeButton("NO", null)
            .show()
    }

    // ==================== EMPLOYEES MANAGEMENT ====================

    private fun showEmployeesDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_manage_employees, null)
        val rv: RecyclerView = v.findViewById(R.id.rvEmployees)
        val tvEmptyEmp: TextView = v.findViewById(R.id.tvEmptyEmployees)
        val btnAddEmp: MaterialButton = v.findViewById(R.id.btnAddEmployee)

        val dialog = AlertDialog.Builder(this)
            .setView(v)
            .setNegativeButton("CLOSE", null)
            .create()

        fun loadEmployees() {
            val list = db.listEmployees()
            tvEmptyEmp.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rv.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE

            rv.layoutManager = LinearLayoutManager(this)
            rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_employee, parent, false)
                    return object : RecyclerView.ViewHolder(itemView) {}
                }

                override fun getItemCount() = list.size

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
                    val emp = list[pos]
                    val view = holder.itemView
                    val tvName: TextView = view.findViewById(R.id.tvEmpItemName)
                    val tvDetails: TextView = view.findViewById(R.id.tvEmpItemDetails)
                    val btnCall: MaterialButton = view.findViewById(R.id.btnCallEmp)
                    val btnMsg: MaterialButton = view.findViewById(R.id.btnMsgEmp)
                    val btnDel: MaterialButton = view.findViewById(R.id.btnDeleteEmp)

                    tvName.text = emp.name
                    tvDetails.text = emp.phone + (if (emp.role.isNotBlank()) " • " + emp.role else "")

                    btnCall.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + emp.phone)))
                    }
                    btnMsg.setOnClickListener {
                        openWhatsAppOrSms(emp.phone, "Hello ${emp.name}, message from Dukaan Diary.")
                    }
                    btnDel.setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Remove Employee?")
                            .setMessage("Remove ${emp.name} from employee list?")
                            .setPositiveButton("REMOVE") { _, _ ->
                                db.deleteEmployee(emp.id)
                                loadEmployees()
                            }
                            .setNegativeButton("CANCEL", null)
                            .show()
                    }
                }
            }
        }

        btnAddEmp.setOnClickListener {
            showAddEmployeeDialog {
                loadEmployees()
            }
        }

        loadEmployees()
        dialog.show()
    }

    private fun showAddEmployeeDialog(onAdded: () -> Unit) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_add_employee, null)
        val etEmpName: TextInputEditText = v.findViewById(R.id.etEmpName)
        val etEmpPhone: TextInputEditText = v.findViewById(R.id.etEmpPhone)
        val etEmpRole: TextInputEditText = v.findViewById(R.id.etEmpRole)

        AlertDialog.Builder(this)
            .setTitle("➕ Add Service Employee / Technician")
            .setView(v)
            .setPositiveButton("SAVE") { _, _ ->
                val name = etEmpName.text.toString().trim()
                val phone = etEmpPhone.text.toString().trim()
                val role = etEmpRole.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    db.saveEmployee(Employee(name = name, phone = phone, role = role))
                    toast("Employee '$name' added!")
                    onAdded()
                } else {
                    toast("Please provide name and phone number")
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ==================== SETTINGS & THEME ====================

    private fun applySavedTheme() {
        val sp = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        when (sp.getString(KEY_THEME_MODE, "system")) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showSettingsDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        val rgTheme: RadioGroup = v.findViewById(R.id.rgThemeMode)
        val rbSystem: RadioButton = v.findViewById(R.id.rbThemeSystem)
        val rbLight: RadioButton = v.findViewById(R.id.rbThemeLight)
        val rbDark: RadioButton = v.findViewById(R.id.rbThemeDark)
        val etDriveFolder: TextInputEditText = v.findViewById(R.id.etDriveFolderId)
        val btnTestNotif: MaterialButton = v.findViewById(R.id.btnTestNotification)
        val btnSaveSettings: MaterialButton = v.findViewById(R.id.btnSaveSettings)

        val sp = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        when (sp.getString(KEY_THEME_MODE, "system")) {
            "light" -> rbLight.isChecked = true
            "dark" -> rbDark.isChecked = true
            else -> rbSystem.isChecked = true
        }
        etDriveFolder.setText(sp.getString(KEY_DRIVE_FOLDER_ID, ""))

        val dialog = AlertDialog.Builder(this)
            .setView(v)
            .create()

        btnTestNotif.setOnClickListener {
            Reminder.notifyNow(this)
            toast("Reminder notification sent!")
        }

        btnSaveSettings.setOnClickListener {
            val selectedTheme = when {
                rbLight.isChecked -> "light"
                rbDark.isChecked -> "dark"
                else -> "system"
            }
            val folderId = etDriveFolder.text.toString().trim()

            sp.edit()
                .putString(KEY_THEME_MODE, selectedTheme)
                .putString(KEY_DRIVE_FOLDER_ID, folderId)
                .apply()

            applySavedTheme()
            toast("Settings saved successfully!")
            dialog.dismiss()
        }

        dialog.show()
    }

    // ==================== BACKUP & SECURITY DIALOG ====================

    private fun updateLastBackupDisplay() {
        val lastTime = getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
            .getString(KEY_LAST_BACKUP_TIME, null)
        if (lastTime != null) {
            tvLastBackupStatus.text = "Last synced: $lastTime"
        } else {
            tvLastBackupStatus.text = "Tap to sync tasks to Google Drive"
        }
    }

    private fun showBackupSecurityDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_backup_security, null)
        val btnSaveBackup: MaterialButton = view.findViewById(R.id.btnSaveBackup)
        val btnShareBackup: MaterialButton = view.findViewById(R.id.btnShareBackup)
        val btnRestoreBackup: MaterialButton = view.findViewById(R.id.btnRestoreBackup)
        val btnToggleAppLock: MaterialButton = view.findViewById(R.id.btnToggleAppLock)
        val tvLockStatus: TextView = view.findViewById(R.id.tvLockStatus)

        val isLocked = isAppLockEnabled()
        tvLockStatus.text = if (isLocked) "App Lock is ACTIVE (Fingerprint / Face / PIN)" else "App Lock is DISABLED"
        tvLockStatus.setTextColor(Color.parseColor(if (isLocked) "#16A34A" else "#64748B"))
        btnToggleAppLock.text = if (isLocked) "🔓 TURN OFF APP LOCK" else "🔒 TURN ON APP LOCK"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton("CLOSE", null)
            .create()

        btnSaveBackup.setOnClickListener {
            dialog.dismiss()
            startBackupExport()
        }

        btnShareBackup.setOnClickListener {
            dialog.dismiss()
            shareBackupDirectly()
        }

        btnRestoreBackup.setOnClickListener {
            dialog.dismiss()
            confirmRestore()
        }

        btnToggleAppLock.setOnClickListener {
            dialog.dismiss()
            toggleAppLock()
        }

        view.findViewById<View>(R.id.btnCloseDialog)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startBackupExport() {
        val file = BackupManager.createBackupArchive(this)
        tempBackupFileForExport = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
        try {
            createDocumentLauncher.launch(intent)
        } catch (_: Exception) {
            shareBackupDirectly()
        }
    }

    private fun shareBackupDirectly() {
        val file = BackupManager.createBackupArchive(this)
        BackupManager.shareBackupFile(this, file)
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle("Restore Records from Backup?")
            .setMessage("Select your .dukaan backup file from Google Drive or Phone Storage. All your customer debts, notes, and audio recordings will be restored safely.")
            .setPositiveButton("SELECT BACKUP FILE") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                try {
                    openDocumentLauncher.launch(intent)
                } catch (_: Exception) {
                    toast("Cannot open file picker")
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun toggleAppLock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            AlertDialog.Builder(this)
                .setTitle("Device Security Not Set")
                .setMessage("Please set up a Fingerprint, Face, or PIN lock in your phone's Android Settings first before enabling App Lock.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val prompt = if (isAppLockEnabled()) "Confirm to disable App Lock" else "Confirm your Fingerprint, Face, or PIN to enable App Lock"
        val intent = km.createConfirmDeviceCredentialIntent("App Security", prompt)
        if (intent != null) {
            toggleLockAuthLauncher.launch(intent)
        } else {
            val newState = !isAppLockEnabled()
            setAppLockEnabled(newState)
            toast(if (newState) "🔒 App Lock enabled" else "🔓 App Lock disabled")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
