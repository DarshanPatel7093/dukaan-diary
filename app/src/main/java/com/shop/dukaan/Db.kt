package com.shop.dukaan

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.Calendar

// Entry types
const val TYPE_PAYMENT = "PAYMENT"              // Customer Udhar (Money to collect)
const val TYPE_PAYMENT_PAY = "PAYMENT_PAY"      // Distributor / Vendor (Money to pay)
const val TYPE_SERVICE = "SERVICE"              // Service Visits (RO, AC, Repair)

data class DashboardStats(
    val toCollectCount: Int = 0,
    val toCollectAmount: Double = 0.0,
    val toPayCount: Int = 0,
    val toPayAmount: Double = 0.0,
    val serviceCount: Int = 0,
    val overdueCount: Int = 0,
    val isToday: Boolean = true
)

data class Employee(
    var id: Long = 0,
    var name: String = "",
    var phone: String = "",
    var role: String = "",
    var created: Long = System.currentTimeMillis()
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
    var done: Boolean = false,
    var assignedEmployeeId: Long = 0,
    var assignedEmployeeName: String = "",
    var assignedEmployeePhone: String = ""
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
    SQLiteOpenHelper(context.applicationContext, "dukaan.db", null, 2) {

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
                assigned_emp_id INTEGER DEFAULT 0,
                assigned_emp_name TEXT,
                assigned_emp_phone TEXT,
                created INTEGER
            )"""
        )

        db.execSQL(
            """CREATE TABLE employees (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL,
                role TEXT,
                created INTEGER
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE leads ADD COLUMN assigned_emp_id INTEGER DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE leads ADD COLUMN assigned_emp_name TEXT")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE leads ADD COLUMN assigned_emp_phone TEXT")
            } catch (_: Exception) {}
            try {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS employees (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        role TEXT,
                        created INTEGER
                    )"""
                )
            } catch (_: Exception) {}
        }
    }

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
        done = c.getInt(c.getColumnIndexOrThrow("done")) == 1,
        assignedEmployeeId = try { c.getLong(c.getColumnIndexOrThrow("assigned_emp_id")) } catch (_: Exception) { 0L },
        assignedEmployeeName = try { c.getString(c.getColumnIndexOrThrow("assigned_emp_name")) ?: "" } catch (_: Exception) { "" },
        assignedEmployeePhone = try { c.getString(c.getColumnIndexOrThrow("assigned_emp_phone")) ?: "" } catch (_: Exception) { "" }
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
            put("assigned_emp_id", l.assignedEmployeeId)
            put("assigned_emp_name", l.assignedEmployeeName)
            put("assigned_emp_phone", l.assignedEmployeePhone)
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

    /** Total count of all leads across all types. */
    fun countAll(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM leads", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    /** Pending items that are due today or overdue for a type. */
    fun countDue(type: String): Int {
        val endOfToday = addDays(startOfDay(), 1)
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE type=? AND done=0 AND date_millis < ?",
            arrayOf(type, endOfToday.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    // ==================== EMPLOYEES CRUD ====================

    private fun readEmployee(c: Cursor) = Employee(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        name = c.getString(c.getColumnIndexOrThrow("name")) ?: "",
        phone = c.getString(c.getColumnIndexOrThrow("phone")) ?: "",
        role = c.getString(c.getColumnIndexOrThrow("role")) ?: "",
        created = c.getLong(c.getColumnIndexOrThrow("created"))
    )

    fun listEmployees(): List<Employee> {
        val out = ArrayList<Employee>()
        readableDatabase.query("employees", null, null, null, null, null, "name ASC")
            .use { c -> while (c.moveToNext()) out.add(readEmployee(c)) }
        return out
    }

    fun getEmployee(id: Long): Employee? {
        readableDatabase.query("employees", null, "id=?", arrayOf(id.toString()), null, null, null)
            .use { c -> return if (c.moveToFirst()) readEmployee(c) else null }
    }

    fun saveEmployee(e: Employee): Long {
        val cv = ContentValues().apply {
            put("name", e.name)
            put("phone", e.phone)
            put("role", e.role)
        }
        return if (e.id == 0L) {
            cv.put("created", System.currentTimeMillis())
            writableDatabase.insert("employees", null, cv)
        } else {
            writableDatabase.update("employees", cv, "id=?", arrayOf(e.id.toString()))
            e.id
        }
    }

    fun deleteEmployee(id: Long) {
        writableDatabase.delete("employees", "id=?", arrayOf(id.toString()))
    }

    // ==================== DASHBOARD & CALENDAR METHODS ====================

    /**
     * Summary statistics for a specific selected date.
     * If [selectedDateMillis] is today, includes today's items + overdue items.
     * If [selectedDateMillis] is another date, includes items for that specific date.
     */
    fun getDashboardStatsForDate(selectedDateMillis: Long): DashboardStats {
        val selectedStart = startOfDay(selectedDateMillis)
        val selectedEnd = addDays(selectedStart, 1)
        val todayStart = startOfDay()
        val isToday = selectedStart == todayStart

        var toCollectCount = 0
        var toCollectAmount = 0.0
        var toPayCount = 0
        var toPayAmount = 0.0
        var serviceCount = 0
        var overdueCount = 0

        // 1. To Collect (Customer Udhar)
        val whereCollect = if (isToday) {
            "type = ? AND done = 0 AND date_millis < ?"
        } else {
            "type = ? AND done = 0 AND date_millis >= ? AND date_millis < ?"
        }
        val argsCollect = if (isToday) {
            arrayOf(TYPE_PAYMENT, selectedEnd.toString())
        } else {
            arrayOf(TYPE_PAYMENT, selectedStart.toString(), selectedEnd.toString())
        }

        readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(amount) FROM leads WHERE $whereCollect",
            argsCollect
        ).use { c ->
            if (c.moveToFirst()) {
                toCollectCount = if (!c.isNull(0)) c.getInt(0) else 0
                toCollectAmount = if (!c.isNull(1)) c.getDouble(1) else 0.0
            }
        }

        // 2. To Pay (Distributor Payments)
        val wherePay = if (isToday) {
            "type = ? AND done = 0 AND date_millis < ?"
        } else {
            "type = ? AND done = 0 AND date_millis >= ? AND date_millis < ?"
        }
        val argsPay = if (isToday) {
            arrayOf(TYPE_PAYMENT_PAY, selectedEnd.toString())
        } else {
            arrayOf(TYPE_PAYMENT_PAY, selectedStart.toString(), selectedEnd.toString())
        }

        readableDatabase.rawQuery(
            "SELECT COUNT(*), SUM(amount) FROM leads WHERE $wherePay",
            argsPay
        ).use { c ->
            if (c.moveToFirst()) {
                toPayCount = if (!c.isNull(0)) c.getInt(0) else 0
                toPayAmount = if (!c.isNull(1)) c.getDouble(1) else 0.0
            }
        }

        // 3. Service Visits
        val whereSvc = if (isToday) {
            "type = ? AND done = 0 AND date_millis < ?"
        } else {
            "type = ? AND done = 0 AND date_millis >= ? AND date_millis < ?"
        }
        val argsSvc = if (isToday) {
            arrayOf(TYPE_SERVICE, selectedEnd.toString())
        } else {
            arrayOf(TYPE_SERVICE, selectedStart.toString(), selectedEnd.toString())
        }

        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE $whereSvc",
            argsSvc
        ).use { c ->
            if (c.moveToFirst()) {
                serviceCount = if (!c.isNull(0)) c.getInt(0) else 0
            }
        }

        // 4. Overdue count (items before today that are not done)
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE done = 0 AND date_millis < ?",
            arrayOf(todayStart.toString())
        ).use { c ->
            if (c.moveToFirst()) {
                overdueCount = if (!c.isNull(0)) c.getInt(0) else 0
            }
        }

        return DashboardStats(
            toCollectCount = toCollectCount,
            toCollectAmount = toCollectAmount,
            toPayCount = toPayCount,
            toPayAmount = toPayAmount,
            serviceCount = serviceCount,
            overdueCount = overdueCount,
            isToday = isToday
        )
    }

    /**
     * Returns leads for a specific date on the Dashboard.
     * If [selectedDateMillis] is today, includes items scheduled for today + overdue items.
     * If [selectedDateMillis] is another date, includes items for that specific date.
     */
    fun listForDate(selectedDateMillis: Long, typeFilter: String? = null, showDone: Boolean = false): List<Lead> {
        val selectedStart = startOfDay(selectedDateMillis)
        val selectedEnd = addDays(selectedStart, 1)
        val todayStart = startOfDay()
        val isToday = selectedStart == todayStart

        val where = StringBuilder()
        val args = ArrayList<String>()

        if (!showDone) {
            where.append("done = 0 AND ")
        }

        if (isToday) {
            where.append("date_millis < ?")
            args.add(selectedEnd.toString())
        } else {
            where.append("date_millis >= ? AND date_millis < ?")
            args.add(selectedStart.toString())
            args.add(selectedEnd.toString())
        }

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

    /** Backward compatibility for today's dashboard. */
    fun getDashboardStats(): DashboardStats = getDashboardStatsForDate(System.currentTimeMillis())

    /** Backward compatibility. */
    fun listDashboard(typeFilter: String? = null, showDone: Boolean = false): List<Lead> =
        listForDate(System.currentTimeMillis(), typeFilter, showDone)
}
