package com.senza.apksigner.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun hex(b: ByteArray): String = b.joinToString(":") { "%02X".format(it) }

    fun size(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "%.1f KB".format(Locale.US, n / 1024.0)
        else -> "%.2f MB".format(Locale.US, n / 1048576.0)
    }

    fun date(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(ms))

    fun day(ms: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(ms))
}
