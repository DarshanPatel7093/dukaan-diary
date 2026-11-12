package com.shop.dukaan

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
    private val onOpen: (Lead) -> Unit
) : RecyclerView.Adapter<LeadAdapter.VH>() {

    private var items: List<Lead> = emptyList()
    private val dateFmt = SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH)
    private val moneyFmt = NumberFormat.getInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }

    fun submit(list: List<Lead>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val product: TextView = v.findViewById(R.id.tvProduct)
        val extra: TextView = v.findViewById(R.id.tvExtra)
        val date: TextView = v.findViewById(R.id.tvDate)
        val note: TextView = v.findViewById(R.id.tvNote)
        val voice: TextView = v.findViewById(R.id.tvVoice)
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

        h.name.text = l.name
        h.product.text = (if (isPayment) "Product: " else "Service for: ") + l.product
        h.product.visibility = if (l.product.isBlank()) View.GONE else View.VISIBLE

        if (isPayment) {
            h.extra.text = "Amount pending: Rs. " + moneyFmt.format(l.amount)
            h.extra.visibility = if (l.amount > 0) View.VISIBLE else View.GONE
            h.date.text = "Promised to pay on: " + dateFmt.format(Date(l.dateMillis))
        } else {
            h.extra.text = "Address: " + l.address
            h.extra.visibility = if (l.address.isBlank()) View.GONE else View.VISIBLE
            h.date.text = "Visit on: " + dateFmt.format(Date(l.dateMillis))
        }

        h.note.text = l.note
        h.note.visibility = if (l.note.isBlank()) View.GONE else View.VISIBLE
        h.voice.visibility = if (l.audioPath != null) View.VISIBLE else View.GONE

        // Status label and colour
        val days = ((l.dateMillis - startOfDay()) / 86_400_000L).toInt()
        val (label, color) = when {
            l.done -> "DONE" to "#757575"
            days < 0 -> "OVERDUE ${-days}d" to "#D32F2F"
            days == 0 -> "TODAY" to "#EF6C00"
            days == 1 -> "TOMORROW" to "#1565C0"
            else -> "In $days days" to "#2E7D32"
        }
        h.status.text = label
        h.status.setTextColor(Color.parseColor(color))

        h.btnCall.visibility = if (l.phone.isBlank()) View.INVISIBLE else View.VISIBLE
        h.btnCall.text = "CALL"
        h.btnDone.text = if (l.done) "UNDO" else "DONE"

        h.btnCall.setOnClickListener { onCall(l) }
        h.btnDone.setOnClickListener { onDone(l) }
        h.btnDelete.setOnClickListener { onDelete(l) }
        h.itemView.setOnClickListener { onOpen(l) }
    }
}
