package com.shop.dukaan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var db: Db
    private lateinit var adapter: LeadAdapter
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var cbShowDone: CheckBox
    private var type = TYPE_PAYMENT

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = Db(this)

        tvSummary = findViewById(R.id.tvSummary)
        tvEmpty = findViewById(R.id.tvEmpty)
        cbShowDone = findViewById(R.id.cbShowDone)
        val tabs: TabLayout = findViewById(R.id.tabs)
        val recycler: RecyclerView = findViewById(R.id.recycler)
        val btnAdd: MaterialButton = findViewById(R.id.btnAdd)

        adapter = LeadAdapter(
            onCall = { l -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + l.phone))) },
            onDone = { l ->
                db.setDone(l.id, !l.done)
                refresh()
            },
            onDelete = { l -> confirmDelete(l) },
            onOpen = { l -> openEdit(l.id) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        tabs.addTab(tabs.newTab().setText("PAYMENTS (Udhar)"))
        tabs.addTab(tabs.newTab().setText("SERVICE VISITS"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                type = if (tab.position == 0) TYPE_PAYMENT else TYPE_SERVICE
                refresh()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        cbShowDone.setOnCheckedChangeListener { _, _ -> refresh() }
        btnAdd.setOnClickListener { openEdit(0L) }

        // Daily reminders (9 AM and 5 PM) - work offline
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openEdit(id: Long) {
        startActivity(
            Intent(this, EditActivity::class.java)
                .putExtra("id", id)
                .putExtra("type", type)
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

    private fun refresh() {
        val list = db.list(type, cbShowDone.isChecked)
        adapter.submit(list)

        val pay = db.countDue(TYPE_PAYMENT)
        val svc = db.countDue(TYPE_SERVICE)
        tvSummary.text = "Today / overdue:  $pay payment(s)  |  $svc service visit(s)"
        tvSummary.visibility = if (pay + svc > 0) View.VISIBLE else View.GONE

        tvEmpty.text = if (type == TYPE_PAYMENT)
            "No pending payments.\nTap  + ADD NEW  to note a new one."
        else
            "No pending service visits.\nTap  + ADD NEW  to note a new one."
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
