package com.shop.dukaan

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeadAdapter(
    private val onCall: (Lead) -> Unit,
    private val onMessage: (Lead) -> Unit,
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
        val assignedEmp: TextView = v.findViewById(R.id.tvAssignedEmployee)
        val date: TextView = v.findViewById(R.id.tvDate)
        val note: TextView = v.findViewById(R.id.tvNote)
        val layoutVoice: View = v.findViewById(R.id.layoutVoice)
        val btnPlayVoice: MaterialButton = v.findViewById(R.id.btnPlayVoice)
        val btnCall: MaterialButton = v.findViewById(R.id.btnCall)
        val btnMessage: MaterialButton = v.findViewById(R.id.btnMessage)
        val btnDone: MaterialButton = v.findViewById(R.id.btnDone)
        val btnDelete: MaterialButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lead, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val l = items[position]
        val isToCollect = l.type == TYPE_PAYMENT
        val isToPay = l.type == TYPE_PAYMENT_PAY
        val isService = l.type == TYPE_SERVICE

        // Type Badge
        val ctx = h.itemView.context
        when {
            isToCollect -> {
                h.typeBadge.text = "💰 TO COLLECT"
                h.typeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.badge_payment_text))
                h.typeBadge.setBackgroundResource(R.drawable.bg_badge_payment)
            }
            isToPay -> {
                h.typeBadge.text = "📤 TO PAY"
                h.typeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.badge_to_pay_text))
                h.typeBadge.setBackgroundResource(R.drawable.bg_badge_to_pay)
            }
            else -> {
                h.typeBadge.text = "🔧 SERVICE"
                h.typeBadge.setTextColor(ContextCompat.getColor(ctx, R.color.badge_service_text))
                h.typeBadge.setBackgroundResource(R.drawable.bg_badge_service)
            }
        }

        // Name (Customer or Distributor)
        h.name.text = l.name

        // Amount
        if ((isToCollect || isToPay) && l.amount > 0) {
            h.amount.visibility = View.VISIBLE
            if (isToCollect) {
                h.amount.setTextColor(ContextCompat.getColor(ctx, R.color.green))
                h.amount.text = "₹ " + moneyFmt.format(l.amount) + " pending"
            } else {
                h.amount.setTextColor(ContextCompat.getColor(ctx, R.color.orange))
                h.amount.text = "₹ " + moneyFmt.format(l.amount) + " to pay"
            }
        } else {
            h.amount.visibility = View.GONE
        }

        // Product / Payment Mode
        val productPrefix = when {
            isToCollect -> "📦 Item: "
            isToPay -> "💳 Mode / Details: "
            else -> "🔧 Service for: "
        }
        h.product.text = productPrefix + l.product
        h.product.visibility = if (l.product.isBlank()) View.GONE else View.VISIBLE

        // Address (For Services)
        if (isService && l.address.isNotBlank()) {
            h.extra.text = "📍 Location: " + l.address
            h.extra.visibility = View.VISIBLE
        } else {
            h.extra.visibility = View.GONE
        }

        // Assigned Employee (For Services)
        if (isService && l.assignedEmployeeName.isNotBlank()) {
            h.assignedEmp.visibility = View.VISIBLE
            h.assignedEmp.text = "👤 Assigned to: " + l.assignedEmployeeName
        } else {
            h.assignedEmp.visibility = View.GONE
        }

        // Date
        val datePrefix = when {
            isToCollect -> "📅 Customer promised on: "
            isToPay -> "📅 Pay / Cheque date: "
            else -> "📅 Worker visit scheduled: "
        }
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

        // Status Badge
        val days = ((l.dateMillis - startOfDay()) / 86_400_000L).toInt()
        val (label, textColorRes, bgRes) = when {
            l.done -> Triple("DONE", R.color.badge_done_text, R.drawable.bg_badge_done)
            days < 0 -> Triple("OVERDUE ${-days}d", R.color.badge_overdue_text, R.drawable.bg_badge_overdue)
            days == 0 -> Triple("TODAY", R.color.badge_today_text, R.drawable.bg_badge_today)
            days == 1 -> Triple("TOMORROW", R.color.badge_tomorrow_text, R.drawable.bg_badge_tomorrow)
            else -> Triple("In $days days", R.color.badge_today_text, R.drawable.bg_badge_today)
        }
        h.status.text = label
        h.status.setTextColor(ContextCompat.getColor(ctx, textColorRes))
        h.status.setBackgroundResource(bgRes)

        // Action Buttons
        h.btnCall.visibility = if (l.phone.isBlank()) View.GONE else View.VISIBLE
        h.btnCall.setOnClickListener { onCall(l) }

        // Message button (WhatsApp / SMS)
        val hasPhoneToMessage = l.phone.isNotBlank() || (isService && l.assignedEmployeePhone.isNotBlank())
        h.btnMessage.visibility = if (hasPhoneToMessage) View.VISIBLE else View.GONE
        h.btnMessage.setOnClickListener { onMessage(l) }

        // Done button text (Received / Paid / Done)
        if (l.done) {
            h.btnDone.text = "UNDO"
            h.btnDone.setTextColor(Color.parseColor("#475569"))
        } else {
            when {
                isToCollect -> {
                    h.btnDone.text = "💰 RECEIVED"
                    h.btnDone.setTextColor(Color.parseColor("#16A34A"))
                }
                isToPay -> {
                    h.btnDone.text = "💳 PAID"
                    h.btnDone.setTextColor(Color.parseColor("#C2410C"))
                }
                else -> {
                    h.btnDone.text = "✓ DONE"
                    h.btnDone.setTextColor(Color.parseColor("#16A34A"))
                }
            }
        }
        h.btnDone.setOnClickListener { onDone(l) }

        // Delete button
        h.btnDelete.setOnClickListener { onDelete(l) }

        // Tap card to open/edit
        h.itemView.setOnClickListener { onOpen(l) }
    }
}
