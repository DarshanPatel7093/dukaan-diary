package com.shop.dukaan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private var currentTabPosition = 0
    private var player: MediaPlayer? = null

    private val moneyFmt = NumberFormat.getInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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

        tvSummary = findViewById(R.id.tvSummary)
        tvEmpty = findViewById(R.id.tvEmpty)
        cbShowDone = findViewById(R.id.cbShowDone)
        val recycler: RecyclerView = findViewById(R.id.recycler)
        btnAdd = findViewById(R.id.btnAdd)

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

    override fun onResume() {
        super.onResume()
        refresh()
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

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }

    private fun refresh() {
        val showDone = cbShowDone.isChecked
        val stats = db.getDashboardStats()

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
