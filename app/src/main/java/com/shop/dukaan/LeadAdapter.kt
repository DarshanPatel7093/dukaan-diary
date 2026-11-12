package com.shop.dukaan

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeadAdapter(
    private val onCall: (Lead) -> Unit,
    private val onDone: (Lead) -> Unit,
    private val onDelete: (Lead) -> Unit,
    private val onOpen: (Lead) -> Unit,
    private val onPlayVoice: (Lead) -> Unit
) : RecyclerView.Adapter<LeadAdapter.VH>() {

    private var items: List<Lead> = emptyList()
    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val moneyFmt = NumberFormat.getInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }

    var playingId: Long? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(list: List<Lead>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val typeBadge: TextView = v.findViewById(R.id.tvTypeBadge)
        val name: TextView = v.findViewById(R.id.tvName)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val amount: TextView = v.findViewById(R.id.tvAmount)
        val product: TextView = v.findViewById(R.id.tvProduct)
        val extra: TextView = v.findViewById(R.id.tvExtra)
        val date: TextView = v.findViewById(R.id.tvDate)
        val note: TextView = v.findViewById(R.id.tvNote)
        val layoutVoice: View = v.findViewById(R.id.layoutVoice)
        val btnPlayVoice: MaterialButton = v.findViewById(R.id.btnPlayVoice)
        val btnCall: MaterialButton = v.findViewById(R.id.btnCall)
        val btnDone: MaterialButton = v.findViewById(R.id.btnDone)
        val btnDelete: MaterialButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lead, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val l = items[position]
        val isPayment = l.type == TYPE_PAYMENT

        // Type Badge
        h.typeBadge.text = if (isPayment) "💰 PAYMENT" else "🔧 SERVICE"
        h.typeBadge.setTextColor(Color.parseColor(if (isPayment) "#1565C0" else "#512DA8"))
        h.typeBadge.setBackgroundResource(if (isPayment) R.drawable.bg_badge_payment else R.drawable.bg_badge_service)

        // Name
        h.name.text = l.name

        // Amount (Prominent for payments)
        if (isPayment && l.amount > 0) {
            h.amount.visibility = View.VISIBLE
            h.amount.text = "₹ " + moneyFmt.format(l.amount) + " pending"
        } else {
            h.amount.visibility = View.GONE
        }

        // Product
        h.product.text = (if (isPayment) "📦 Product: " else "🔧 Service for: ") + l.product
        h.product.visibility = if (l.product.isBlank()) View.GONE else View.VISIBLE

        // Address (For Services)
        if (!isPayment && l.address.isNotBlank()) {
            h.extra.text = "📍 Address: " + l.address
            h.extra.visibility = View.VISIBLE
        } else {
            h.extra.visibility = View.GONE
        }

        // Date
        val datePrefix = if (isPayment) "📅 Promised to pay: " else "📅 Visit scheduled: "
        h.date.text = datePrefix + dateFmt.format(Date(l.dateMillis))

        // Note
        if (l.note.isNotBlank()) {
            h.note.text = "📝 " + l.note
            h.note.visibility = View.VISIBLE
        } else {
            h.note.visibility = View.GONE
        }

        // Voice Note Player directly on the card
        if (l.audioPath != null) {
            h.layoutVoice.visibility = View.VISIBLE
            val isPlaying = playingId == l.id
            if (isPlaying) {
                h.btnPlayVoice.text = "⏹ STOP"
                h.btnPlayVoice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DC2626"))
            } else {
                h.btnPlayVoice.text = "▶ LISTEN"
                h.btnPlayVoice.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
            }
            h.btnPlayVoice.setOnClickListener { onPlayVoice(l) }
        } else {
            h.layoutVoice.visibility = View.GONE
        }

        // Status Badge with soft rounded pill styling
        val days = ((l.dateMillis - startOfDay()) / 86_400_000L).toInt()
        val (label, textColor, bgRes) = when {
            l.done -> Triple("DONE", "#64748B", R.drawable.bg_badge_done)
            days < 0 -> Triple("OVERDUE ${-days}d", "#DC2626", R.drawable.bg_badge_overdue)
            days == 0 -> Triple("TODAY", "#D97706", R.drawable.bg_badge_today)
            days == 1 -> Triple("TOMORROW", "#2563EB", R.drawable.bg_badge_tomorrow)
            else -> Triple("In $days days", "#16A34A", R.drawable.bg_badge_today)
        }
        h.status.text = label
        h.status.setTextColor(Color.parseColor(textColor))
        h.status.setBackgroundResource(bgRes)

        // Action Buttons
        h.btnCall.visibility = if (l.phone.isBlank()) View.GONE else View.VISIBLE
        h.btnDone.text = if (l.done) "UNDO" else "✓ DONE"

        h.btnCall.setOnClickListener { onCall(l) }
        h.btnDone.setOnClickListener { onDone(l) }
        h.btnDelete.setOnClickListener { onDelete(l) }
        h.itemView.setOnClickListener { onOpen(l) }
    }
}
