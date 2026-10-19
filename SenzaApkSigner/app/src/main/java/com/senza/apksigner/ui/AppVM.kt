package com.senza.apksigner.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senza.apksigner.core.ApkInfo
import com.senza.apksigner.core.FileUtil
import com.senza.apksigner.core.Inspector
import com.senza.apksigner.core.CertGen
import com.senza.apksigner.core.Ks
import com.senza.apksigner.core.KsImport
import com.senza.apksigner.core.SecretStore
import com.senza.apksigner.core.SignerEngine
import com.senza.apksigner.core.VerifyReport
import com.senza.apksigner.data.HistoryItem
import com.senza.apksigner.data.KeyProfile
import com.senza.apksigner.data.NewKeyRequest
import com.senza.apksigner.data.Repo
import com.senza.apksigner.data.SignOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.util.UUID

enum class Status { PENDING, RUNNING, DONE, FAILED }

class ApkItem(val id: String, val name: String, val file: File, val info: ApkInfo?) {
    var status by mutableStateOf(Status.PENDING)
    var output by mutableStateOf<File?>(null)
    var note by mutableStateOf("")
}

class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val repo = Repo(ctx)
    private val ksDir = File(ctx.filesDir, "keystores").apply { mkdirs() }
    private val signedDir = File(ctx.filesDir, "signed").apply { mkdirs() }
    private val inDir = File(ctx.cacheDir, "in").apply { mkdirs() }

    var tab by mutableIntStateOf(0)
    var busy by mutableStateOf(false)
    var busyText by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    val profiles = mutableStateListOf<KeyProfile>()
    var selectedId by mutableStateOf<String?>(null)
    val apks = mutableStateListOf<ApkItem>()
    var options by mutableStateOf(SignOptions())
    val history = mutableStateListOf<HistoryItem>()
    val log = mutableStateListOf<String>()

    var verifyName by mutableStateOf("")
    var verifyReport by mutableStateOf<VerifyReport?>(null)

    init {
        profiles.addAll(repo.loadProfiles().filter { File(ksDir, it.file).exists() })
        selectedId = repo.selectedId?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        options = repo.loadOptions()
        history.addAll(repo.loadHistory().filter { File(it.path).exists() })
    }

    fun toast(m: String) { message = m }

    private fun setBusy(b: Boolean, t: String = "") { busy = b; busyText = t }

    fun friendly(e: Throwable): String = when {
        e is UnrecoverableKeyException -> "Kata sandi kunci salah"
        e is IOException && (e.message ?: "").contains("password", ignoreCase = true) ->
            "Kata sandi keystore salah atau file rusak"
        e is IOException && (e.message ?: "").contains("integrity", ignoreCase = true) ->
            "Kata sandi keystore salah atau file rusak"
        else -> e.message ?: e.javaClass.simpleName
    }

    fun updateOptions(o: SignOptions) { options = o; repo.saveOptions(o) }

    fun select(id: String) { selectedId = id; repo.selectedId = id }

    fun selectedProfile(): KeyProfile? = profiles.firstOrNull { it.id == selectedId }

    fun needsPassword(): Boolean {
        val p = selectedProfile() ?: return false
        return p.storePassEnc == null || p.keyPassEnc == null ||
            SecretStore.decrypt(p.storePassEnc) == null
    }

    // ---------- APK ----------
    fun addApks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            setBusy(true, "Membaca APK…")
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    try {
                        val name = FileUtil.displayName(ctx, u)
                        val f = File(inDir, UUID.randomUUID().toString() + ".apk")
                        FileUtil.copy(ctx, u, f)
                        val item = ApkItem(f.name, name, f, Inspector.info(ctx, f))
                        if (item.info == null) {
                            item.status = Status.FAILED
                            item.note = "Bukan APK yang valid"
                        }
                        item
                    } catch (e: Exception) { null }
                }
            }
            apks.addAll(items)
            if (items.size < uris.size) toast("Sebagian file gagal dibaca")
            setBusy(false)
            tab = 0
        }
    }

    fun removeApk(a: ApkItem) { apks.remove(a); a.file.delete() }

    fun clearApks() { apks.forEach { it.file.delete() }; apks.clear(); log.clear() }

    // ---------- Sign ----------
    fun sign(storePwOverride: String?, keyPwOverride: String?) {
        val p = selectedProfile() ?: return toast("Pilih keystore dulu")
        val todo = apks.filter { it.status != Status.DONE && it.info != null }
        if (todo.isEmpty()) return toast("Tidak ada APK untuk ditandatangani")
        val sp = storePwOverride ?: p.storePassEnc?.let { SecretStore.decrypt(it) }
        val kp = keyPwOverride ?: p.keyPassEnc?.let { SecretStore.decrypt(it) } ?: sp
        if (sp == null || kp == null) return toast("Kata sandi keystore diperlukan")
        val opts = options

        viewModelScope.launch {
            setBusy(true, "Menyiapkan kunci…")
            log.clear()
            withContext(Dispatchers.Default) {
                val key = try {
                    Ks.loadKey(File(ksDir, p.file), p.type, sp.toCharArray(), p.alias, kp.toCharArray())
                } catch (e: Exception) {
                    toast(friendly(e)); return@withContext
                }
                for (item in todo) {
                    item.status = Status.RUNNING
                    item.note = ""
                    busyText = "Menandatangani ${item.name}"
                    log.add("▶ ${item.name}")
                    try {
                        val out = File(signedDir, outName(item.name, opts.suffix))
                        val rep = SignerEngine.sign(item.file, out, key, opts, ctx.cacheDir) { log.add("  $it") }
                        item.output = out
                        item.status = Status.DONE
                        val inf = item.info
                        val scheme = rep?.schemes() ?: listOfNotNull(
                            "v1".takeIf { opts.v1 }, "v2".takeIf { opts.v2 },
                            "v3".takeIf { opts.v3 }, "v4".takeIf { opts.v4 }
                        ).joinToString(" + ")
                        addHistory(
                            HistoryItem(
                                UUID.randomUUID().toString(), item.name, inf?.pkg ?: "", inf?.versionName ?: "",
                                out.absolutePath, out.length(), System.currentTimeMillis(), p.name, scheme
                            )
                        )
                        if (opts.autoSave) {
                            val ok = FileUtil.saveToDownloads(ctx, out, out.name)
                            log.add(if (ok) "  Disimpan ke Download/SenzaSigner/${out.name}" else "  ! Gagal menyimpan ke Download")
                        }
                    } catch (e: Exception) {
                        item.status = Status.FAILED
                        item.note = friendly(e)
                        log.add("  ✗ ${friendly(e)}")
                    }
                }
            }
            setBusy(false)
        }
    }

    private fun outName(name: String, suffix: String): String {
        val base = name.removeSuffix(".apk").removeSuffix(".APK")
        val safe = suffix.filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
        return "$base$safe.apk"
    }

    private fun addHistory(h: HistoryItem) {
        history.removeAll { it.path == h.path }
        history.add(0, h)
        repo.saveHistory(history.toList())
    }

    fun deleteHistory(h: HistoryItem) {
        File(h.path).delete()
        File(h.path + ".idsig").delete()
        history.remove(h)
        repo.saveHistory(history.toList())
    }

    fun clearHistory() {
        history.forEach { File(it.path).delete(); File(it.path + ".idsig").delete() }
        history.clear()
        repo.saveHistory(emptyList())
    }

    fun saveToDownloads(f: File) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { FileUtil.saveToDownloads(ctx, f, f.name) }
            toast(if (ok) "Disimpan ke Download/SenzaSigner" else "Gagal menyimpan")
        }
    }

    // ---------- Keystore ----------
    private fun addProfile(p: KeyProfile) {
        profiles.add(p)
        repo.saveProfiles(profiles.toList())
        select(p.id)
    }

    fun createKey(r: NewKeyRequest, remember: Boolean) {
        viewModelScope.launch {
            setBusy(true, "Membuat kunci ${r.algo}… (RSA 4096 bisa sampai 1 menit)")
            val res = withContext(Dispatchers.Default) { runCatching { CertGen.generate(r, ksDir, remember) } }
            setBusy(false)
            res.onSuccess { addProfile(it); toast("Keystore \"${it.name}\" dibuat") }
                .onFailure { toast("Gagal membuat kunci: ${friendly(it)}") }
        }
    }

    /** Kunci uji sekali tap (RSA 2048, sandi acak tersimpan terenkripsi). Jangan dipakai untuk rilis. */
    fun createQuickKey() {
        val chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val pw = (1..20).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        createKey(
            NewKeyRequest("Kunci Uji", "senza", "RSA-2048", 30, "Senza Test Key", "", "Senza Project", "", "", "ID", pw),
            true
        )
    }

    fun probeKeystore(uri: Uri, storePw: String, cb: (List<String>?, String?) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { KsImport.probe(FileUtil.readBytes(ctx, uri), storePw.toCharArray(), ctx.cacheDir) }
            }
            r.onSuccess { cb(it, null) }.onFailure { cb(null, friendly(it)) }
        }
    }

    fun importKey(
        uri: Uri, name: String, storePw: String, keyPw: String, alias: String,
        remember: Boolean, cb: (String?) -> Unit
    ) {
        viewModelScope.launch {
            setBusy(true, "Mengimpor keystore…")
            val r = withContext(Dispatchers.IO) {
                runCatching { KsImport.import(FileUtil.readBytes(ctx, uri), storePw, keyPw, alias, name, ksDir, remember) }
            }
            setBusy(false)
            r.onSuccess { addProfile(it); toast("Keystore \"${it.name}\" diimpor"); cb(null) }
                .onFailure { cb(friendly(it)) }
        }
    }

    fun deleteProfile(p: KeyProfile) {
        File(ksDir, p.file).delete()
        profiles.remove(p)
        repo.saveProfiles(profiles.toList())
        if (selectedId == p.id) {
            selectedId = profiles.firstOrNull()?.id
            repo.selectedId = selectedId
        }
    }

    fun exportKeystore(p: KeyProfile, dest: Uri) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { FileUtil.writeTo(ctx, File(ksDir, p.file), dest) } }
            toast(if (r.isSuccess) "Keystore diekspor" else "Gagal mengekspor")
        }
    }

    // ---------- Verify ----------
    fun verify(uri: Uri) {
        viewModelScope.launch {
            setBusy(true, "Memverifikasi…")
            verifyReport = null
            val name = withContext(Dispatchers.IO) { FileUtil.displayName(ctx, uri) }
            val res = withContext(Dispatchers.Default) {
                runCatching {
                    val f = File(inDir, "verify_${System.nanoTime()}.apk")
                    try {
                        FileUtil.copy(ctx, uri, f)
                        Inspector.verify(f)
                    } finally { f.delete() }
                }
            }
            setBusy(false)
            verifyName = name
            res.onSuccess { verifyReport = it }.onFailure { toast("Gagal memverifikasi: ${friendly(it)}") }
        }
    }
}
