package com.shop.dukaan

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.Calendar

const val TYPE_PAYMENT = "PAYMENT"
const val TYPE_SERVICE = "SERVICE"

data class DashboardStats(
    val todayPaymentCount: Int = 0,
    val overduePaymentCount: Int = 0,
    val totalDuePaymentCount: Int = 0,
    val totalDuePaymentAmount: Double = 0.0,
    val todayServiceCount: Int = 0,
    val overdueServiceCount: Int = 0,
    val totalDueServiceCount: Int = 0
)

data class Lead(
    var id: Long = 0,
    var type: String = TYPE_PAYMENT,
    var name: String = "",
    var phone: String = "",
    var product: String = "",
    var amount: Double = 0.0,
    var address: String = "",
    var dateMillis: Long = 0,
    var note: String = "",
    var audioPath: String? = null,
    var done: Boolean = false
)

/** Midnight (start) of the day containing [millis]. */
fun startOfDay(millis: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun addDays(millis: Long, days: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = millis
    c.add(Calendar.DAY_OF_YEAR, days)
    return c.timeInMillis
}

class Db(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "dukaan.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE leads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                phone TEXT,
                product TEXT,
                amount REAL DEFAULT 0,
                address TEXT,
                date_millis INTEGER NOT NULL,
                note TEXT,
                audio_path TEXT,
                done INTEGER DEFAULT 0,
                created INTEGER
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun read(c: Cursor) = Lead(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        type = c.getString(c.getColumnIndexOrThrow("type")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        phone = c.getString(c.getColumnIndexOrThrow("phone")) ?: "",
        product = c.getString(c.getColumnIndexOrThrow("product")) ?: "",
        amount = c.getDouble(c.getColumnIndexOrThrow("amount")),
        address = c.getString(c.getColumnIndexOrThrow("address")) ?: "",
        dateMillis = c.getLong(c.getColumnIndexOrThrow("date_millis")),
        note = c.getString(c.getColumnIndexOrThrow("note")) ?: "",
        audioPath = c.getString(c.getColumnIndexOrThrow("audio_path")),
        done = c.getInt(c.getColumnIndexOrThrow("done")) == 1
    )

    fun list(type: String, showDone: Boolean): List<Lead> {
        val where = if (showDone) "type=?" else "type=? AND done=0"
        val out = ArrayList<Lead>()
        readableDatabase.query(
            "leads", null, where, arrayOf(type), null, null,
            "done ASC, date_millis ASC"
        ).use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

    fun get(id: Long): Lead? {
        readableDatabase.query("leads", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { c -> return if (c.moveToFirst()) read(c) else null }
    }

    fun save(l: Lead): Long {
        val cv = ContentValues().apply {
            put("type", l.type)
            put("name", l.name)
            put("phone", l.phone)
            put("product", l.product)
            put("amount", l.amount)
            put("address", l.address)
            put("date_millis", l.dateMillis)
            put("note", l.note)
            put("audio_path", l.audioPath)
            put("done", if (l.done) 1 else 0)
        }
        return if (l.id == 0L) {
            cv.put("created", System.currentTimeMillis())
            writableDatabase.insert("leads", null, cv)
        } else {
            writableDatabase.update("leads", cv, "id=?", arrayOf(l.id.toString()))
            l.id
        }
    }

    fun setDone(id: Long, done: Boolean) {
        val cv = ContentValues().apply { put("done", if (done) 1 else 0) }
        writableDatabase.update("leads", cv, "id=?", arrayOf(id.toString()))
    }

    fun setAudio(id: Long, path: String?) {
        val cv = ContentValues().apply { put("audio_path", path) }
        writableDatabase.update("leads", cv, "id=?", arrayOf(id.toString()))
    }

    /** Deletes the entry and its voice recording file. */
    fun deleteWithAudio(l: Lead) {
        l.audioPath?.let { File(it).delete() }
        writableDatabase.delete("leads", "id=?", arrayOf(l.id.toString()))
    }

    /** Total count of all leads across payments and services. */
    fun countAll(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM leads", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Pending items that are due today or overdue. */
    fun countDue(type: String): Int {
        val endOfToday = addDays(startOfDay(), 1)
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE type=? AND done=0 AND date_millis < ?",
            arrayOf(type, endOfToday.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    /** Summary statistics for today's dashboard (today + overdue). */
    fun getDashboardStats(): DashboardStats {
        val startToday = startOfDay()
        val endToday = addDays(startToday, 1)

        var todayPay = 0
        var overduePay = 0
        var totalPayAmount = 0.0
        var todaySvc = 0
        var overdueSvc = 0

        // Payments due today or overdue
        readableDatabase.rawQuery(
            """SELECT 
                SUM(CASE WHEN date_millis >= ? AND date_millis < ? THEN 1 ELSE 0 END),
                SUM(CASE WHEN date_millis < ? THEN 1 ELSE 0 END),
                SUM(amount)
               FROM leads 
               WHERE type = ? AND done = 0 AND date_millis < ?""",
            arrayOf(
                startToday.toString(),
                endToday.toString(),
                startToday.toString(),
                TYPE_PAYMENT,
                endToday.toString()
            )
        ).use { c ->
            if (c.moveToFirst()) {
                todayPay = if (!c.isNull(0)) c.getInt(0) else 0
                overduePay = if (!c.isNull(1)) c.getInt(1) else 0
                totalPayAmount = if (!c.isNull(2)) c.getDouble(2) else 0.0
            }
        }

        // Services due today or overdue
        readableDatabase.rawQuery(
            """SELECT 
                SUM(CASE WHEN date_millis >= ? AND date_millis < ? THEN 1 ELSE 0 END),
                SUM(CASE WHEN date_millis < ? THEN 1 ELSE 0 END)
               FROM leads 
               WHERE type = ? AND done = 0 AND date_millis < ?""",
            arrayOf(
                startToday.toString(),
                endToday.toString(),
                startToday.toString(),
                TYPE_SERVICE,
                endToday.toString()
            )
        ).use { c ->
            if (c.moveToFirst()) {
                todaySvc = if (!c.isNull(0)) c.getInt(0) else 0
                overdueSvc = if (!c.isNull(1)) c.getInt(1) else 0
            }
        }

        return DashboardStats(
            todayPaymentCount = todayPay,
            overduePaymentCount = overduePay,
            totalDuePaymentCount = todayPay + overduePay,
            totalDuePaymentAmount = totalPayAmount,
            todayServiceCount = todaySvc,
            overdueServiceCount = overdueSvc,
            totalDueServiceCount = todaySvc + overdueSvc
        )
    }

    /** Returns leads due today or overdue for the Dashboard. */
    fun listDashboard(typeFilter: String? = null, showDone: Boolean = false): List<Lead> {
        val endToday = addDays(startOfDay(), 1)
        val where = StringBuilder()
        val args = ArrayList<String>()

        if (!showDone) {
            where.append("done = 0 AND ")
        }
        where.append("date_millis < ?")
        args.add(endToday.toString())

        if (typeFilter != null) {
            where.append(" AND type = ?")
            args.add(typeFilter)
        }

        val out = ArrayList<Lead>()
        readableDatabase.query(
            "leads", null, where.toString(), args.toTypedArray(), null, null,
            "done ASC, date_millis ASC"
        ).use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }
}
