package com.senza.apksigner.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object FileUtil {
    const val APK_MIME = "application/vnd.android.package-archive"

    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) { }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file.apk"
    }

    fun copy(ctx: Context, uri: Uri, dest: File) {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Tidak bisa membuka file")
        input.use { i -> dest.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
    }

    fun readBytes(ctx: Context, uri: Uri): ByteArray {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Tidak bisa membuka file")
        return input.use { it.readBytes() }
    }

    fun writeTo(ctx: Context, src: File, uri: Uri) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Tidak bisa menulis file")
        out.use { o -> src.inputStream().use { it.copyTo(o, 1 shl 16) } }
    }

    /** Simpan ke Download/SenzaSigner lewat MediaStore (tanpa izin penyimpanan). */
    fun saveToDownloads(ctx: Context, src: File, name: String): Boolean = try {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SenzaSigner")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) false else {
            writeTo(ctx, src, uri)
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, done, null, null)
            true
        }
    } catch (e: Exception) { false }

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun share(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = APK_MIME
            putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Bagikan APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Return false kalau izin "pasang aplikasi tidak dikenal" belum diberikan (halaman pengaturan dibuka). */
    fun install(ctx: Context, f: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(ctx, f), APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
        return true
    }
}
