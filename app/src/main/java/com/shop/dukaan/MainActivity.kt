package com.shop.dukaan

import android.Manifest
import android.app.Activity
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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var adapter: LeadAdapter

    // Dashboard views
    private lateinit var layoutDashboard: LinearLayout
    private lateinit var cardTodayPayments: MaterialCardView
    private lateinit var cardTodayServices: MaterialCardView
    private lateinit var tvDashPaymentAmount: TextView
    private lateinit var tvDashPaymentCount: TextView
    private lateinit var tvDashServiceCount: TextView
    private lateinit var tvDashServiceDetail: TextView
    private lateinit var chipGroupDashboard: ChipGroup
    private lateinit var chipFilterAll: Chip
    private lateinit var chipFilterPayments: Chip
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
    private var player: MediaPlayer? = null

    // Security & Backup state
    private val PREFS_SECURITY = "dukaan_security"
    private val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
    private val KEY_LAST_BACKUP_TIME = "last_backup_time"
    private var isUnlockedForSession = false
    private var tempBackupFileForExport: File? = null

    private val moneyFmt = NumberFormat.getInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }

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
                    Toast.makeText(this, "✅ All records & voice notes restored successfully!", Toast.LENGTH_LONG).show()
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = Db(this)

        // Find views
        tvHeaderDate = findViewById(R.id.tvHeaderDate)
        tabs = findViewById(R.id.tabs)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        cardTodayPayments = findViewById(R.id.cardTodayPayments)
        cardTodayServices = findViewById(R.id.cardTodayServices)
        tvDashPaymentAmount = findViewById(R.id.tvDashPaymentAmount)
        tvDashPaymentCount = findViewById(R.id.tvDashPaymentCount)
        tvDashServiceCount = findViewById(R.id.tvDashServiceCount)
        tvDashServiceDetail = findViewById(R.id.tvDashServiceDetail)
        chipGroupDashboard = findViewById(R.id.chipGroupDashboard)
        chipFilterAll = findViewById(R.id.chipFilterAll)
        chipFilterPayments = findViewById(R.id.chipFilterPayments)
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
            onCall = { l -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + l.phone))) },
            onDone = { l ->
                stopPlayback()
                db.setDone(l.id, !l.done)
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

        // Setup 3 Navigation Tabs
        tabs.addTab(tabs.newTab().setText("📊 DASHBOARD"))
        tabs.addTab(tabs.newTab().setText("💰 PAYMENTS"))
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

        // Dashboard card shortcuts
        cardTodayPayments.setOnClickListener {
            if (chipFilterPayments.isChecked) {
                chipFilterAll.isChecked = true
            } else {
                chipFilterPayments.isChecked = true
            }
        }

        cardTodayServices.setOnClickListener {
            if (chipFilterServices.isChecked) {
                chipFilterAll.isChecked = true
            } else {
                chipFilterServices.isChecked = true
            }
        }

        chipGroupDashboard.setOnCheckedChangeListener { _, _ ->
            refresh()
        }

        cbShowDone.setOnCheckedChangeListener { _, _ -> refresh() }

        btnAdd.setOnClickListener {
            when (currentTabPosition) {
                0 -> showAddSelectionDialog()
                1 -> openEdit(0L, TYPE_PAYMENT)
                2 -> openEdit(0L, TYPE_SERVICE)
            }
        }

        btnBackupSecurity.setOnClickListener {
            showBackupSecurityDialog()
        }

        btnUnlockApp.setOnClickListener {
            requestAppUnlock()
        }

        // Daily reminders (9 AM and 5 PM) - work offline
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        updateTabVisibility()
    }

    override fun onStart() {
        super.onStart()
        checkAppLock()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
        if (isAppLockEnabled()) {
            isUnlockedForSession = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun checkAppLock() {
        if (isAppLockEnabled()) {
            if (!isUnlockedForSession) {
                layoutLockOverlay.visibility = View.VISIBLE
                requestAppUnlock()
            } else {
                layoutLockOverlay.visibility = View.GONE
            }
        } else {
            layoutLockOverlay.visibility = View.GONE
        }
    }

    private fun requestAppUnlock() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (km.isKeyguardSecure) {
            val intent = km.createConfirmDeviceCredentialIntent(
                "Dukaan Diary is Locked",
                "Authenticate with Fingerprint, Face, or PIN to continue"
            )
            if (intent != null) {
                appUnlockLauncher.launch(intent)
            } else {
                isUnlockedForSession = true
                layoutLockOverlay.visibility = View.GONE
            }
        } else {
            isUnlockedForSession = true
            layoutLockOverlay.visibility = View.GONE
        }
    }

    private fun isAppLockEnabled(): Boolean {
        val sp = getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_APP_LOCK_ENABLED, false)
    }

    private fun setAppLockEnabled(enabled: Boolean) {
        val sp = getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
    }

    private fun showBackupSecurityDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_backup_security, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val btnBackupSaveDrive: MaterialButton = dialogView.findViewById(R.id.btnBackupSaveDrive)
        val btnBackupShare: MaterialButton = dialogView.findViewById(R.id.btnBackupShare)
        val btnBackupRestore: MaterialButton = dialogView.findViewById(R.id.btnBackupRestore)
        val cbAppLock: CheckBox = dialogView.findViewById(R.id.cbAppLock)
        val tvLockStatusHint: TextView = dialogView.findViewById(R.id.tvLockStatusHint)
        val btnCloseDialog: MaterialButton = dialogView.findViewById(R.id.btnCloseDialog)

        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        cbAppLock.isChecked = isAppLockEnabled()

        if (!km.isKeyguardSecure) {
            tvLockStatusHint.text = "⚠️ No screen lock set on phone. Set a PIN/Fingerprint in Android Settings first."
            cbAppLock.isEnabled = false
        } else {
            tvLockStatusHint.text = if (isAppLockEnabled()) "App is protected with phone's Fingerprint / PIN" else "Requires Fingerprint / PIN to open Dukaan Diary"
        }

        btnBackupSaveDrive.setOnClickListener {
            dialog.dismiss()
            startBackupExport()
        }

        btnBackupShare.setOnClickListener {
            dialog.dismiss()
            startBackupShare()
        }

        btnBackupRestore.setOnClickListener {
            dialog.dismiss()
            confirmRestore()
        }

        cbAppLock.setOnClickListener {
            val targetState = cbAppLock.isChecked
            cbAppLock.isChecked = !targetState // Revert visually until confirmed
            if (!km.isKeyguardSecure) {
                Toast.makeText(this, "Please set a PIN or Fingerprint on your phone first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = km.createConfirmDeviceCredentialIntent(
                "Confirm Identity",
                if (targetState) "Confirm to enable App Lock" else "Confirm to disable App Lock"
            )
            if (intent != null) {
                toggleLockAuthLauncher.launch(intent)
            } else {
                setAppLockEnabled(targetState)
                cbAppLock.isChecked = targetState
            }
        }

        btnCloseDialog.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startBackupExport() {
        try {
            val file = BackupManager.createBackupArchive(this)
            tempBackupFileForExport = file
            val timeStamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, "DukaanDiary_Backup_$timeStamp.dukaan")
            }
            createDocumentLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not create backup: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startBackupShare() {
        try {
            val file = BackupManager.createBackupArchive(this)
            BackupManager.shareBackupFile(this, file)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not create backup: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle("Restore Data from Backup?")
            .setMessage("⚠️ Restoring a backup will replace your current records with the data from the backup file.\n\nMake sure to select a valid Dukaan Diary backup file from Google Drive or Downloads.")
            .setPositiveButton("SELECT BACKUP FILE") { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                openDocumentLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateTabVisibility() {
        when (currentTabPosition) {
            0 -> {
                layoutDashboard.visibility = View.VISIBLE
                tvSummary.visibility = View.GONE
                btnAdd.text = "+  ADD NEW REMINDER"
            }
            1 -> {
                layoutDashboard.visibility = View.GONE
                btnAdd.text = "+  ADD NEW PAYMENT"
            }
            2 -> {
                layoutDashboard.visibility = View.GONE
                btnAdd.text = "+  ADD NEW SERVICE VISIT"
            }
        }
    }

    private fun showAddSelectionDialog() {
        val options = arrayOf("💰 Payment (Udhar)", "🔧 Service Visit")
        AlertDialog.Builder(this)
            .setTitle("Add New Entry")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEdit(0L, TYPE_PAYMENT)
                    1 -> openEdit(0L, TYPE_SERVICE)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEdit(id: Long, defaultType: String) {
        startActivity(
            Intent(this, EditActivity::class.java)
                .putExtra("id", id)
                .putExtra("type", defaultType)
        )
    }

    private fun confirmDelete(l: Lead) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage(l.name + (if (l.audioPath != null) "\n(Voice note will also be deleted)" else ""))
            .setPositiveButton("YES, DELETE") { _, _ ->
                db.deleteWithAudio(l)
                refresh()
            }
            .setNegativeButton("NO", null)
            .show()
    }

    private fun handlePlayVoice(lead: Lead) {
        val path = lead.audioPath ?: return
        if (adapter.playingId == lead.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                }
                start()
            }
            player = mp
            adapter.playingId = lead.id
        } catch (e: Exception) {
            stopPlayback()
            Toast.makeText(this, "Could not play voice note", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {}
            it.release()
        }
        player = null
        adapter.playingId = null
    }

    private fun updateLastBackupDisplay() {
        val saved = getSharedPreferences(PREFS_SECURITY, Context.MODE_PRIVATE)
            .getString(KEY_LAST_BACKUP_TIME, null)
        tvLastBackupStatus.text = if (saved != null) "Last synced to Drive: $saved" else "Tap to sync newly added tasks to Drive"
    }

    private fun refresh() {
        val showDone = cbShowDone.isChecked
        val stats = db.getDashboardStats()

        val totalCount = db.countAll()
        cardRestorePrompt.visibility = if (totalCount == 0 && currentTabPosition == 0) View.VISIBLE else View.GONE

        // Update dashboard KPI stats
        tvDashPaymentAmount.text = "Rs. " + moneyFmt.format(stats.totalDuePaymentAmount)
        if (stats.overduePaymentCount > 0) {
            tvDashPaymentCount.text = "${stats.totalDuePaymentCount} pending (${stats.overduePaymentCount} overdue)"
            tvDashPaymentCount.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            tvDashPaymentCount.text = "${stats.todayPaymentCount} pending today"
            tvDashPaymentCount.setTextColor(Color.parseColor("#616161"))
        }

        tvDashServiceCount.text = "${stats.totalDueServiceCount} visit" + (if (stats.totalDueServiceCount == 1) "" else "s")
        if (stats.overdueServiceCount > 0) {
            tvDashServiceDetail.text = "${stats.totalDueServiceCount} pending (${stats.overdueServiceCount} overdue)"
            tvDashServiceDetail.setTextColor(Color.parseColor("#D32F2F"))
        } else {
            tvDashServiceDetail.text = "${stats.todayServiceCount} scheduled for today"
            tvDashServiceDetail.setTextColor(Color.parseColor("#616161"))
        }

        chipFilterAll.text = "All Today (${stats.totalDuePaymentCount + stats.totalDueServiceCount})"
        chipFilterPayments.text = "Payments (${stats.totalDuePaymentCount})"
        chipFilterServices.text = "Services (${stats.totalDueServiceCount})"

        // Populate list based on current tab
        when (currentTabPosition) {
            0 -> {
                val filter = when (chipGroupDashboard.checkedChipId) {
                    R.id.chipFilterPayments -> TYPE_PAYMENT
                    R.id.chipFilterServices -> TYPE_SERVICE
                    else -> null
                }
                val list = db.listDashboard(filter, showDone)
                adapter.submit(list)
                tvSummary.visibility = View.GONE
                tvEmpty.text = "🎉 All caught up for today!\nNo pending payments or services due today."
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
            1 -> {
                val list = db.list(TYPE_PAYMENT, showDone)
                adapter.submit(list)
                val payDue = stats.totalDuePaymentCount
                if (payDue > 0) {
                    tvSummary.text = "Today / overdue: $payDue payment(s)"
                    tvSummary.visibility = View.VISIBLE
                } else {
                    tvSummary.visibility = View.GONE
                }
                tvEmpty.text = "No pending payments.\nTap  + ADD NEW PAYMENT  to note a new one."
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
            2 -> {
                val list = db.list(TYPE_SERVICE, showDone)
                adapter.submit(list)
                val svcDue = stats.totalDueServiceCount
                if (svcDue > 0) {
                    tvSummary.text = "Today / overdue: $svcDue service visit(s)"
                    tvSummary.visibility = View.VISIBLE
                } else {
                    tvSummary.visibility = View.GONE
                }
                tvEmpty.text = "No pending service visits.\nTap  + ADD NEW SERVICE VISIT  to note a new one."
                tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
