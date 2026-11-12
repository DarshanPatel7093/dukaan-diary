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

    /** Pending items that are due today or overdue. */
    fun countDue(type: String): Int {
        val endOfToday = addDays(startOfDay(), 1)
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM leads WHERE type=? AND done=0 AND date_millis < ?",
            arrayOf(type, endOfToday.toString())
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }
}
