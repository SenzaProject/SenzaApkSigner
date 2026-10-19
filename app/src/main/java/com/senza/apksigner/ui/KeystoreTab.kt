package com.senza.apksigner.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.senza.apksigner.core.Fmt
import com.senza.apksigner.data.KeyProfile
import com.senza.apksigner.data.NewKeyRequest

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit,
    password: Boolean = false, number: Boolean = false
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
fun KeystoreTab(vm: AppVM) {
    var showCreate by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var toDelete by remember { mutableStateOf<KeyProfile?>(null) }
    var toExport by remember { mutableStateOf<KeyProfile?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) importUri = u
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        val p = toExport
        if (u != null && p != null) vm.exportKeystore(p, u)
        toExport = null
    }
    val clip = LocalClipboardManager.current

    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) { Text("Buat baru") }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text("Impor")
                }
            }
        }
        item {
            Text(
                "Format impor: PKCS12 (.p12/.pfx), JKS (.jks/.keystore), BKS. JKS otomatis dikonversi ke PKCS12. " +
                    "Cadangkan keystore Anda — kalau hilang, update aplikasi tidak bisa dipasang di atas versi lama.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (vm.profiles.isEmpty()) {
            item { Text("Belum ada keystore.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(vm.profiles, key = { it.id }) { p ->
            val selected = p.id == vm.selectedId
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    KV("Alias · Jenis", "${p.alias} · ${p.type} · ${p.algo}")
                    KV("Pemilik", p.subject)
                    KV("Berlaku sampai", Fmt.day(p.validTo))
                    KV("SHA-256", p.sha256)
                    Text(
                        if (p.storePassEnc != null) "Kata sandi tersimpan (terenkripsi Android Keystore)" else "Kata sandi diminta setiap sign",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row {
                        TextButton(onClick = { vm.select(p.id) }, enabled = !selected) { Text(if (selected) "Dipakai" else "Pakai") }
                        TextButton(onClick = { clip.setText(AnnotatedString(p.sha256)); vm.toast("SHA-256 disalin") }) { Text("Salin") }
                        TextButton(onClick = { toExport = p; exportPicker.launch(p.name.replace(' ', '_') + ".p12") }) { Text("Ekspor") }
                        TextButton(onClick = { toDelete = p }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (showCreate) CreateKeyDialog(vm) { showCreate = false }
    importUri?.let { u -> ImportKeyDialog(vm, u) { importUri = null } }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Hapus keystore?") },
            text = { Text("\"${p.name}\" akan dihapus dari aplikasi. Tanpa cadangan, kunci ini tidak bisa dipulihkan.") },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(p); toDelete = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun CreateKeyDialog(vm: AppVM, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("key0") }
    var algo by remember { mutableStateOf("RSA-2048") }
    var years by remember { mutableStateOf("30") }
    var cn by remember { mutableStateOf("") }
    var ou by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var st by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("ID") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var keepPw by remember { mutableStateOf(true) }

    val y = years.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && alias.isNotBlank() && cn.isNotBlank() && y in 1..100 &&
        pw.length >= 6 && pw == pw2 && (country.isBlank() || country.length == 2)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Buat keystore baru") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Nama keystore", name, { name = it })
                Field("Alias kunci", alias, { alias = it.filter { c -> !c.isWhitespace() } })
                Text("Algoritma", style = MaterialTheme.typography.labelLarge)
                listOf("RSA-2048", "RSA-4096", "EC-P256").forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = algo == a, onClick = { algo = a })
                        Text(a)
                    }
                }
                Field("Masa berlaku (tahun)", years, { years = it.filter { c -> c.isDigit() }.take(3) }, number = true)
                Field("Nama (CN)", cn, { cn = it })
                Field("Unit organisasi (OU)", ou, { ou = it })
                Field("Organisasi (O)", org, { org = it })
                Field("Kota (L)", city, { city = it })
                Field("Provinsi (ST)", st, { st = it })
                Field("Kode negara (2 huruf)", country, { country = it.take(2) })
                Field("Kata sandi (min. 6)", pw, { pw = it }, password = true)
                Field("Ulangi kata sandi", pw2, { pw2 = it }, password = true)
                Text(
                    "Kata sandi kunci disamakan dengan keystore (aturan PKCS12).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keepPw, onCheckedChange = { keepPw = it })
                    Text("Ingat kata sandi (terenkripsi)")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                vm.createKey(NewKeyRequest(name.trim(), alias, algo, y, cn, ou, org, city, st, country, pw), keepPw)
                onClose()
            }) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

@Composable
private fun ImportKeyDialog(vm: AppVM, uri: Uri, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var sp by remember { mutableStateOf("") }
    var kp by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var alias by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var keepPw by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Impor keystore") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Nama keystore", name, { name = it })
                Field("Kata sandi keystore", sp, { sp = it; aliases = emptyList() }, password = true)
                OutlinedButton(enabled = sp.isNotEmpty(), onClick = {
                    error = null
                    vm.probeKeystore(uri, sp) { list, err ->
                        error = err
                        if (list != null) { aliases = list; alias = list.first() }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Baca alias") }
                aliases.forEach { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = alias == a, onClick = { alias = a })
                        Text(a)
                    }
                }
                if (aliases.isNotEmpty()) {
                    Field("Kata sandi kunci (kosong = sama)", kp, { kp = it }, password = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepPw, onCheckedChange = { keepPw = it })
                        Text("Ingat kata sandi (terenkripsi)")
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = aliases.isNotEmpty() && alias.isNotEmpty(), onClick = {
                val keyPw = kp.ifEmpty { sp }
                val nm = name.ifBlank { alias }
                vm.importKey(uri, nm, sp, keyPw, alias, keepPw) { err ->
                    if (err == null) onClose() else error = err
                }
            }) { Text("Impor") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}
