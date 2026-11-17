package com.smarttracker.util

import android.content.Context
import com.smarttracker.data.AppDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backup = one .zip with the Room database + every stored screenshot. */
object BackupManager {
    private const val DB_ENTRY = "smart_tracker.db"
    private const val IMG_PREFIX = "screenshots/"

    fun backup(ctx: Context, out: OutputStream) {
        // flush the write-ahead log so the .db file on disk is complete
        AppDatabase.get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            ctx.getDatabasePath(AppDatabase.NAME).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            ImageStore.dir(ctx).listFiles()?.filter { it.isFile && !it.name.startsWith("tmp_") }?.forEach { f ->
                zip.putNextEntry(ZipEntry(IMG_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Replaces ALL current data. The caller must restart the app afterwards. */
    fun restore(ctx: Context, input: InputStream) {
        val tmp = File(ctx.cacheDir, "restore_${System.nanoTime()}").apply { mkdirs() }
        try {
            val tmpDb = File(tmp, DB_ENTRY)
            val tmpImgs = File(tmp, "screenshots").apply { mkdirs() }
            var hasDb = false
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    val target: File? = when {
                        name == DB_ENTRY -> { hasDb = true; tmpDb }
                        name.startsWith(IMG_PREFIX) && !e.isDirectory -> {
                            val fn = name.removePrefix(IMG_PREFIX)
                            if (fn.isEmpty() || fn.contains('/') || fn.contains('\\') || fn.contains("..")) null
                            else File(tmpImgs, fn)
                        }
                        else -> null
                    }
                    if (target != null) FileOutputStream(target).use { zip.copyTo(it) }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
            require(hasDb) { "This file is not a Smart Transaction Tracker backup" }
            val header = tmpDb.inputStream().use { s -> ByteArray(15).also { s.read(it) } }
            require(String(header, Charsets.US_ASCII) == "SQLite format 3") { "Backup database is damaged" }

            AppDatabase.closeAndReset()
            val dbFile = ctx.getDatabasePath(AppDatabase.NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            tmpDb.copyTo(dbFile, overwrite = true)

            val imgDir = ImageStore.dir(ctx)
            imgDir.listFiles()?.forEach { it.delete() }
            tmpImgs.listFiles()?.forEach { it.copyTo(File(imgDir, it.name), overwrite = true) }
        } finally {
            tmp.deleteRecursively()
        }
    }
}
