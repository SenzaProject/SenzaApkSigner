package com.senza.apksigner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.senza.apksigner.core.FileUtil
import com.senza.apksigner.core.Fmt

@Composable
fun SignTab(vm: AppVM) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addApks(uris)
    }
    var askPass by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val o = vm.options
    val profile = vm.profiles.firstOrNull { it.id == vm.selectedId }
    val canSign = !vm.busy && profile != null &&
        vm.apks.any { it.status != Status.DONE && it.info != null }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard("1 · File APK") {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih APK (bisa banyak)")
                }
                if (vm.apks.isEmpty()) {
                    Text(
                        "Atau buka APK dari file manager dengan \"Buka dengan\" → Senza APK Signer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    TextButton(onClick = { vm.clearApks() }, enabled = !vm.busy) { Text("Kosongkan daftar") }
                }
            }
        }
        items(vm.apks, key = { it.id }) { ApkRow(vm, it) }

        item {
            SectionCard("2 · Keystore") {
                if (vm.profiles.isEmpty()) {
                    Text(
                        "Belum ada keystore. Buat atau impor di tab Keystore, atau pakai kunci uji sekali tap.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.tab = 1 }) { Text("Buka Keystore") }
                        OutlinedButton(onClick = { vm.createQuickKey() }) { Text("Kunci uji") }
                    }
                } else {
                    Box {
                        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(profile?.name ?: "Pilih keystore", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            vm.profiles.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.name} · ${p.alias}") },
                                    onClick = { vm.select(p.id); menu = false }
                                )
                            }
                        }
                    }
                    if (profile != null) {
                        KV("Sertifikat", profile.subject)
                        KV("Berlaku sampai", Fmt.day(profile.validTo))
                        KV("SHA-256", profile.sha256)
                    }
                }
            }
        }

        item {
            SectionCard("3 · Opsi tanda tangan") {
                SwitchRow("Skema v1 (JAR)", "Android 7 ke bawah", o.v1) { vm.updateOptions(o.copy(v1 = it)) }
                SwitchRow("Skema v2", "Android 7+", o.v2) { vm.updateOptions(o.copy(v2 = it)) }
                SwitchRow("Skema v3", "Android 9+, mendukung rotasi kunci", o.v3) { vm.updateOptions(o.copy(v3 = it)) }
                SwitchRow("Skema v4 (.idsig)", "Android 11+, instalasi inkremental", o.v4) { vm.updateOptions(o.copy(v4 = it)) }
                SwitchRow("Zipalign", "Ratakan entri sebelum menandatangani", o.zipAlign) { vm.updateOptions(o.copy(zipAlign = it)) }
                SwitchRow("Alignment 16 KB untuk .so", "Disarankan untuk Android 15+", o.pageAlign16k) { vm.updateOptions(o.copy(pageAlign16k = it)) }
                SwitchRow("Verifikasi hasil", "Cek tanda tangan setelah selesai", o.verifyAfter) { vm.updateOptions(o.copy(verifyAfter = it)) }
                SwitchRow("Simpan otomatis", "Salin ke Download/SenzaSigner", o.autoSave) { vm.updateOptions(o.copy(autoSave = it)) }
                OutlinedTextField(
                    value = o.minSdk?.toString() ?: "",
                    onValueChange = { s -> vm.updateOptions(o.copy(minSdk = s.filter { c -> c.isDigit() }.take(2).toIntOrNull())) },
                    label = { Text("Min SDK (kosong = otomatis dari manifest)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = o.suffix,
                    onValueChange = { vm.updateOptions(o.copy(suffix = it.take(24))) },
                    label = { Text("Akhiran nama file") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            Button(
                onClick = { if (vm.needsPassword()) askPass = true else vm.sign(null, null) },
                enabled = canSign, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Create, null)
                Spacer(Modifier.width(8.dp))
                val n = vm.apks.count { it.status != Status.DONE && it.info != null }
                Text(if (n > 1) "Tanda tangani $n APK" else "Tanda tangani APK")
            }
        }

        if (vm.log.isNotEmpty()) {
            item {
                SectionCard("Log") {
                    Text(
                        vm.log.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )
                }
            }
        }
    }

    if (askPass) {
        var sp by remember { mutableStateOf("") }
        var kp by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPass = false },
            title = { Text("Kata sandi keystore") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        sp, { sp = it }, label = { Text("Kata sandi keystore") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        kp, { kp = it }, label = { Text("Kata sandi kunci (kosong = sama)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askPass = false
                    vm.sign(sp, kp.ifBlank { null })
                }, enabled = sp.isNotEmpty()) { Text("Tanda tangani") }
            },
            dismissButton = { TextButton(onClick = { askPass = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun ApkRow(vm: AppVM, a: ApkItem) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ic = a.info?.icon
                if (ic != null) {
                    Image(ic, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                } else {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.info?.label ?: a.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val inf = a.info
                    Text(
                        if (inf != null) "${inf.pkg} · v${inf.versionName} · ${Fmt.size(inf.size)}" else a.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (inf != null) {
                        Text(
                            "SDK ${inf.minSdk}–${inf.targetSdk} · ${inf.permissions} izin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { vm.removeApk(a) }, enabled = !vm.busy) { Icon(Icons.Default.Close, "Hapus") }
            }
            when (a.status) {
                Status.PENDING -> Text("Menunggu", style = MaterialTheme.typography.labelMedium)
                Status.RUNNING -> Text("Memproses…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Status.FAILED -> Text("Gagal: ${a.note}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                Status.DONE -> {
                    val out = a.output
                    Text(
                        "Selesai · ${out?.name ?: ""} (${Fmt.size(out?.length() ?: 0)})",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
                    )
                    if (out != null) {
                        Row {
                            TextButton(onClick = {
                                if (!FileUtil.install(ctx, out)) vm.toast("Izinkan \"pasang aplikasi\" untuk Senza APK Signer, lalu coba lagi")
                            }) { Text("Pasang") }
                            TextButton(onClick = { FileUtil.share(ctx, out) }) { Text("Bagikan") }
                            TextButton(onClick = { vm.saveToDownloads(out) }) { Text("Simpan") }
                        }
                    }
                }
            }
        }
    }
}
