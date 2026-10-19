package com.senza.apksigner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.senza.apksigner.core.CertInfo
import com.senza.apksigner.core.Fmt

@Composable
fun VerifyTab(vm: AppVM) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.verify(u)
    }
    val rep = vm.verifyReport
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("Pilih APK untuk diverifikasi")
            }
        }
        if (rep == null) {
            item {
                Text(
                    "Cek skema tanda tangan (v1–v4), sidik jari sertifikat, serta error dan peringatan dari APK mana pun.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                SectionCard(vm.verifyName) {
                    Text(
                        if (rep.verified) "✓ Tanda tangan valid" else "✗ Tanda tangan tidak valid",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = if (rep.verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("v1", rep.v1); Pill("v2", rep.v2); Pill("v3", rep.v3); Pill("v4", rep.v4)
                    }
                }
            }
            if (rep.errors.isNotEmpty()) {
                item {
                    SectionCard("Error") {
                        rep.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            if (rep.warnings.isNotEmpty()) {
                item {
                    SectionCard("Peringatan") {
                        rep.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            rep.certs.forEachIndexed { i, c -> item { CertBlock(vm, "Penanda tangan ${i + 1}", c) } }
        }
    }
}

@Composable
fun CertBlock(vm: AppVM, title: String, c: CertInfo) {
    val clip = LocalClipboardManager.current
    SectionCard(title) {
        KV("Subjek", c.subject)
        KV("Penerbit", c.issuer)
        KV("Serial", c.serial)
        KV("Berlaku", "${Fmt.day(c.notBefore)} – ${Fmt.day(c.notAfter)}")
        KV("Kunci", "${c.keyAlg} ${c.keyBits} bit · ${c.sigAlg}")
        KV("SHA-256", c.sha256)
        KV("SHA-1", c.sha1)
        KV("MD5", c.md5)
        TextButton(onClick = { clip.setText(AnnotatedString(c.sha256)); vm.toast("SHA-256 disalin") }) { Text("Salin SHA-256") }
    }
}
