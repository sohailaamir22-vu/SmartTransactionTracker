@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttracker.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarttracker.data.TransactionEntity
import com.smarttracker.util.BackupManager
import com.smarttracker.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoreScreen(
    vm: MainViewModel,
    themeMode: String,
    onThemeChange: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    fun export(uri: Uri?, produce: (OutputStream, List<TransactionEntity>) -> Unit) {
        if (uri == null) return
        val data = vm.transactions.value
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write file")
                    out.use { produce(it, data) }
                }.isSuccess
            }
            busy = false
            onMessage(if (ok) "File saved (${data.size} transactions)" else "Could not save the file")
        }
    }

    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> export(uri) { out, data -> out.write(Exporter.xlsx(data)) } }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> export(uri) { out, data -> out.write(Exporter.csv(data)) } }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> export(uri) { out, data -> Exporter.pdf(out, data) } }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write file")
                        out.use { BackupManager.backup(ctx, it) }
                    }.isSuccess
                }
                busy = false
                onMessage(if (ok) "Backup saved" else "Backup failed")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        SectionCard("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    FilterChip(selected = themeMode == key, onClick = { onThemeChange(key) }, label = { Text(label) })
                }
            }
        }

        SectionCard("Export all transactions") {
            Button(onClick = { xlsxLauncher.launch("transactions_${stamp()}.xlsx") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export to Excel (.xlsx)")
            }
            OutlinedButton(onClick = { csvLauncher.launch("transactions_${stamp()}.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export to CSV")
            }
            OutlinedButton(onClick = { pdfLauncher.launch("transaction_report_${stamp()}.pdf") }, modifier = Modifier.fillMaxWidth()) {
                Text("Export PDF report")
            }
        }

        SectionCard("Backup & restore") {
            Text(
                "A backup is one .zip file with all transactions and every saved screenshot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { backupLauncher.launch("smart_tracker_backup_${stamp()}.zip") }, modifier = Modifier.fillMaxWidth()) {
                Text("Create backup")
            }
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restore from backup") }
        }

        SectionCard("About") {
            Text(
                "Smart Transaction Tracker 1.0\nAll data and OCR processing stay on your phone (works offline). " +
                    "Amounts are shown in rupees (Rs).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = { Text("This replaces ALL current transactions and screenshots with the backup. The app will restart afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        busy = true
                        val err = withContext(Dispatchers.IO) {
                            runCatching {
                                val ins = ctx.contentResolver.openInputStream(uri) ?: error("Cannot read file")
                                ins.use { BackupManager.restore(ctx, it) }
                            }.exceptionOrNull()
                        }
                        busy = false
                        if (err != null) {
                            onMessage(err.message ?: "Restore failed")
                        } else {
                            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                            if (launch != null) ctx.startActivity(Intent.makeRestartActivityTask(launch.component))
                            (ctx as? Activity)?.finishAffinity()
                            Runtime.getRuntime().exit(0)
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
