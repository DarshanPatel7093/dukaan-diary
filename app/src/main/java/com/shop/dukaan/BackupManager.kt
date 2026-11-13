package com.shop.dukaan

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {

    private const val DB_NAME = "dukaan.db"
    private const val VOICE_DIR = "voice"

    /**
     * Creates a single zip backup archive containing dukaan.db and all voice recordings.
     */
    fun createBackupArchive(context: Context): File {
        val dbHelper = Db(context)
        try {
            // Checkpoint WAL to flush all transactions into the main .db file
            dbHelper.readableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        } catch (_: Exception) {
        } finally {
            dbHelper.close()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "DukaanDiary_Backup_$timeStamp.dukaan")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupFile))).use { zos ->
            // 1. Add database file
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, "database/$DB_NAME")
            }

            // 2. Add all voice notes
            val voiceDir = File(context.filesDir, VOICE_DIR)
            if (voiceDir.exists() && voiceDir.isDirectory) {
                voiceDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        addFileToZip(zos, file, "$VOICE_DIR/${file.name}")
                    }
                }
            }

            // 3. Add manifest
            val manifest = "app=DukaanDiary\nversion=1.0\ntimestamp=$timeStamp\n"
            val manifestEntry = ZipEntry("manifest.txt")
            zos.putNextEntry(manifestEntry)
            zos.write(manifest.toByteArray())
            zos.closeEntry()
        }

        return backupFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        val entry = ZipEntry(entryPath)
        entry.time = file.lastModified()
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    /**
     * Copies a local backup file to a user-selected destination Uri (Google Drive / Documents).
     */
    fun writeBackupToUri(context: Context, backupFile: File, targetUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                FileInputStream(backupFile).use { `in` ->
                    `in`.copyTo(out)
                }
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Shares the backup file via Android's native share sheet (e.g. Save to Drive, WhatsApp).
     */
    fun shareBackupFile(context: Context, backupFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, backupFile)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Dukaan Diary Backup")
            putExtra(Intent.EXTRA_TEXT, "Here is the Dukaan Diary backup file.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Save or Share Backup (Google Drive / WhatsApp)"))
    }

    /**
     * Restores the database and voice notes from a selected backup Uri.
     */
    fun restoreBackup(context: Context, backupUri: Uri): Boolean {
        val tempRestoreDir = File(context.cacheDir, "restore_temp").apply {
            deleteRecursively()
            mkdirs()
        }

        var foundDb = false

        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(backupUri) ?: return false
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    // Prevent zip slip vulnerability
                    val targetFile = File(tempRestoreDir, entryName)
                    if (!targetFile.canonicalPath.startsWith(tempRestoreDir.canonicalPath)) {
                        throw SecurityException("Invalid zip entry path: $entryName")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        if (entryName.endsWith(DB_NAME)) {
                            foundDb = true
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (!foundDb) return false

            // Close current db helper
            val dbHelper = Db(context)
            dbHelper.close()

            // 1. Restore SQLite database
            val tempDb = File(tempRestoreDir, "database/$DB_NAME")
            val targetDb = context.getDatabasePath(DB_NAME)
            if (tempDb.exists()) {
                targetDb.parentFile?.mkdirs()
                // Remove existing DB and journals
                targetDb.delete()
                File(targetDb.path + "-wal").delete()
                File(targetDb.path + "-shm").delete()
                File(targetDb.path + "-journal").delete()

                tempDb.copyTo(targetDb, overwrite = true)
            }

            // 2. Restore voice notes
            val tempVoiceDir = File(tempRestoreDir, VOICE_DIR)
            val targetVoiceDir = File(context.filesDir, VOICE_DIR).apply { mkdirs() }
            if (tempVoiceDir.exists() && tempVoiceDir.isDirectory) {
                tempVoiceDir.listFiles()?.forEach { voiceFile ->
                    val dest = File(targetVoiceDir, voiceFile.name)
                    voiceFile.copyTo(dest, overwrite = true)
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            tempRestoreDir.deleteRecursively()
        }
    }
}

