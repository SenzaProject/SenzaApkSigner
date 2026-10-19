package com.senza.apksigner.core

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.senza.apksigner.data.SignOptions
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipExtraField
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.util.Collections

data class ApkInfo(
    val label: String, val pkg: String, val versionName: String, val versionCode: Long,
    val minSdk: Int, val targetSdk: Int, val size: Long, val permissions: Int,
    val icon: ImageBitmap?
)

data class VerifyReport(
    val verified: Boolean, val v1: Boolean, val v2: Boolean, val v3: Boolean, val v4: Boolean,
    val errors: List<String>, val warnings: List<String>, val certs: List<CertInfo>
) {
    fun schemes(): String = listOfNotNull(
        "v1".takeIf { v1 }, "v2".takeIf { v2 }, "v3".takeIf { v3 }, "v4".takeIf { v4 }
    ).joinToString(" + ").ifEmpty { "-" }
}

object Inspector {
    fun info(ctx: Context, f: File): ApkInfo? {
        val pm = ctx.packageManager
        val pi = pm.getPackageArchiveInfo(f.absolutePath, PackageManager.GET_PERMISSIONS) ?: return null
        val ai = pi.applicationInfo ?: return null
        ai.sourceDir = f.absolutePath
        ai.publicSourceDir = f.absolutePath
        val label = try { ai.loadLabel(pm).toString() } catch (e: Exception) { pi.packageName }
        val icon = try { ai.loadIcon(pm).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null }
        return ApkInfo(
            label = label, pkg = pi.packageName, versionName = pi.versionName ?: "-",
            versionCode = pi.longVersionCode, minSdk = ai.minSdkVersion, targetSdk = ai.targetSdkVersion,
            size = f.length(), permissions = pi.requestedPermissions?.size ?: 0, icon = icon
        )
    }

    fun verify(f: File): VerifyReport {
        val r = ApkVerifier.Builder(f).build().verify()
        val errs = LinkedHashSet<String>()
        val warns = LinkedHashSet<String>()
        r.errors.forEach { errs.add(it.toString()) }
        r.warnings.forEach { warns.add(it.toString()) }
        r.v1SchemeSigners.forEach { s ->
            s.errors.forEach { errs.add(it.toString()) }
            s.warnings.forEach { warns.add(it.toString()) }
        }
        r.v2SchemeSigners.forEach { s ->
            s.errors.forEach { errs.add(it.toString()) }
            s.warnings.forEach { warns.add(it.toString()) }
        }
        r.v3SchemeSigners.forEach { s ->
            s.errors.forEach { errs.add(it.toString()) }
            s.warnings.forEach { warns.add(it.toString()) }
        }
        val certs = (r.signerCertificates ?: emptyList()).map { Ks.certInfo(it) }
        return VerifyReport(
            verified = r.isVerified,
            v1 = r.isVerifiedUsingV1Scheme, v2 = r.isVerifiedUsingV2Scheme,
            v3 = r.isVerifiedUsingV3Scheme, v4 = r.isVerifiedUsingV4Scheme,
            errors = errs.toList(), warnings = warns.toList(), certs = certs
        )
    }
}

object ZipAligner {
    /** Salin ZIP tanpa kompres ulang; entri STORED diratakan 4 byte (16 KB untuk .so). */
    fun align(input: File, output: File, page16k: Boolean) {
        @Suppress("DEPRECATION")
        val zf = ZipFile(input)
        try {
            ZipArchiveOutputStream(output).use { out ->
                for (e in Collections.list(zf.entriesInPhysicalOrder)) {
                    val ne = ZipArchiveEntry(e)
                    ne.setExtraFields(arrayOf<ZipExtraField>())
                    if (e.method == java.util.zip.ZipEntry.STORED && !e.isDirectory) {
                        ne.setAlignment(if (page16k && e.name.endsWith(".so")) 16384 else 4)
                    }
                    out.addRawArchiveEntry(ne, zf.getRawInputStream(e))
                }
            }
        } finally {
            zf.close()
        }
    }
}

object SignerEngine {
    fun sign(
        input: File, output: File, key: Ks.KeyData, o: SignOptions,
        tmpDir: File, log: (String) -> Unit
    ): VerifyReport? {
        if (!o.v1 && !o.v2 && !o.v3) throw IllegalArgumentException("Aktifkan minimal satu skema (v1/v2/v3)")
        if (o.v4 && !o.v2 && !o.v3) throw IllegalArgumentException("Skema v4 butuh v2 atau v3 aktif")
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        var src = input
        var aligned: File? = null
        try {
            if (o.zipAlign) {
                log("Zipalign${if (o.pageAlign16k) " (16 KB untuk .so)" else ""}…")
                aligned = File(tmpDir, "aligned_${System.nanoTime()}.apk")
                ZipAligner.align(input, aligned, o.pageAlign16k)
                src = aligned
            }
            val sc = listOfNotNull("v1".takeIf { o.v1 }, "v2".takeIf { o.v2 }, "v3".takeIf { o.v3 }, "v4".takeIf { o.v4 })
            log("Menandatangani (${sc.joinToString(" + ")})…")

            val cfg = ApkSigner.SignerConfig.Builder("CERT", key.key, key.chain).build()
            val b = ApkSigner.Builder(listOf(cfg))
                .setInputApk(src)
                .setOutputApk(output)
                .setV1SigningEnabled(o.v1)
                .setV2SigningEnabled(o.v2)
                .setV3SigningEnabled(o.v3)
                .setCreatedBy("Senza APK Signer")
            if (o.v4) {
                b.setV4SigningEnabled(true)
                b.setV4SignatureOutputFile(File(output.path + ".idsig"))
            } else {
                b.setV4SigningEnabled(false)
            }
            o.minSdk?.let { b.setMinSdkVersion(it) }
            // Jaga alignment hasil zipalign bila versi apksig mendukung (dipanggil lewat refleksi).
            runCatching {
                b.javaClass.getMethod("setAlignmentPreserved", Boolean::class.javaPrimitiveType).invoke(b, true)
            }
            b.build().sign()

            if (!o.verifyAfter) return null
            log("Verifikasi hasil…")
            val rep = Inspector.verify(output)
            if (!rep.verified) {
                output.delete()
                throw IOException("Verifikasi gagal: " + (rep.errors.firstOrNull() ?: "tidak diketahui"))
            }
            log("✓ Valid (${rep.schemes()})")
            return rep
        } finally {
            aligned?.delete()
        }
    }
}
