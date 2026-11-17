package com.smarttracker.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class StoredImage(val name: String, val hash: String)

/** Keeps every uploaded screenshot in app-private storage, named after its SHA-256 hash. */
object ImageStore {
    fun dir(ctx: Context): File = File(ctx.filesDir, "screenshots").also { it.mkdirs() }

    fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    fun delete(ctx: Context, name: String) {
        file(ctx, name).delete()
    }

    /** Copies the picked image into app storage and returns its name + content hash. */
    fun copyIn(ctx: Context, uri: Uri): StoredImage {
        val tmp = File(dir(ctx), "tmp_${System.nanoTime()}")
        val md = MessageDigest.getInstance("SHA-256")
        val input = ctx.contentResolver.openInputStream(uri) ?: error("Cannot open image")
        input.use { ins ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        val name = hash.take(40) + ".img"
        val dest = File(dir(ctx), name)
        if (dest.exists()) {
            tmp.delete()
        } else if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        return StoredImage(name, hash)
    }
}
