package com.shop.dukaan

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

object Reminder {
    private const val CHANNEL = "dukaan_reminders"

    /**
     * Schedules reminders every 2 hours from 8:00 AM to 10:00 PM:
     * 8:00 AM, 10:00 AM, 12:00 PM, 2:00 PM, 4:00 PM, 6:00 PM, 8:00 PM, 10:00 PM.
     * Works 100% offline.
     */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val hours = listOf(8, 10, 12, 14, 16, 18, 20, 22)

        hours.forEachIndexed { index, hour ->
            scheduleAt(ctx, am, hour, 100 + index)
        }
    }

    private fun scheduleAt(ctx: Context, am: AlarmManager, hour: Int, requestCode: Int) {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            action = "com.shop.dukaan.REMINDER_ALARM"
            putExtra("hour", hour)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun notifyNow(ctx: Context) {
        val db = Db(ctx)
        val toCollect = db.countDue(TYPE_PAYMENT)
        val toPay = db.countDue(TYPE_PAYMENT_PAY)
        val svc = db.countDue(TYPE_SERVICE)

        if (toCollect == 0 && toPay == 0 && svc == 0) return

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL, "Dukaan Diary Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders for payments to collect, payments to pay, and service visits"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val parts = ArrayList<String>()
        if (toCollect > 0) parts.add("💰 $toCollect payment(s) to collect")
        if (toPay > 0) parts.add("📤 $toPay payment(s) to pay")
        if (svc > 0) parts.add("🔧 $svc service visit(s) scheduled")

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🏪 Dukaan Diary - Due Reminders")
            .setContentText(parts.joinToString(" • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(ctx).notify(100, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.notifyNow(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Reminder.schedule(context)
        }
    }
}
