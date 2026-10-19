package com.senza.apksigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.senza.apksigner.core.FileUtil
import com.senza.apksigner.core.Fmt
import java.io.File

@Composable
fun HistoryTab(vm: AppVM) {
    val ctx = LocalContext.current
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (vm.history.isEmpty()) {
            item { Text("Belum ada APK yang ditandatangani.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            item { TextButton(onClick = { vm.clearHistory() }) { Text("Hapus semua riwayat dan file") } }
        }
        items(vm.history, key = { it.id }) { h ->
            val f = File(h.path)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(f.name, fontWeight = FontWeight.SemiBold)
                    KV("Paket", "${h.pkg} · v${h.version}")
                    KV("Keystore · skema", "${h.profileName} · ${h.schemes}")
                    KV("Waktu · ukuran", "${Fmt.date(h.time)} · ${Fmt.size(h.size)}")
                    Row {
                        TextButton(onClick = {
                            if (!FileUtil.install(ctx, f)) vm.toast("Izinkan \"pasang aplikasi\" untuk Senza APK Signer, lalu coba lagi")
                        }) { Text("Pasang") }
                        TextButton(onClick = { FileUtil.share(ctx, f) }) { Text("Bagikan") }
                        TextButton(onClick = { vm.saveToDownloads(f) }) { Text("Simpan") }
                        TextButton(onClick = { vm.deleteHistory(h) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item {
            SectionCard("Tentang") {
                Text("Senza APK Signer 1.0.0", fontWeight = FontWeight.SemiBold)
                Text(
                    "Menandatangani dengan pustaka apksig resmi Android (v1/v2/v3/v4), zipalign bawaan, " +
                        "dan verifikasi otomatis. Keystore tersimpan di penyimpanan privat aplikasi.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
