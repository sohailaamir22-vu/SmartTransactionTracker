@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smarttracker.util.Fmt
import com.smarttracker.util.ImageStore
import java.io.File

@Composable
fun ImportScreen(vm: ImportViewModel, onMessage: (String) -> Unit) {
    val ui by vm.ui.collectAsState()
    var viewing by remember { mutableStateOf<File?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.process(uris)
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            onMessage(it)
            vm.consumeMessage()
        }
    }

    val chosen = ui.items.filter { it.include }
    val chosenTotal = Fmt.sum(chosen.mapNotNull { Fmt.parseAmount(it.amountText) })

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !ui.processing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Select transaction screenshots (up to 100 at once)") }

            if (ui.processing) {
                LinearProgressIndicator(
                    progress = { if (ui.total == 0) 0f else ui.done.toFloat() / ui.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Reading screenshots… ${ui.done} / ${ui.total}", style = MaterialTheme.typography.labelMedium)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (ui.items.isEmpty() && !ui.processing) {
                Text(
                    "Pick one or many payment screenshots.\n\nThe amount, date and time are read automatically (offline). " +
                        "You can correct anything before saving. If a screenshot has no date, the upload time is used. " +
                        "The same screenshot cannot be added twice.",
                    modifier = Modifier.align(Alignment.Center).padding(28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.items, key = { it.key }) { item ->
                        ReviewRow(
                            item = item,
                            onAmount = { vm.setAmount(item.key, it) },
                            onTime = { vm.setTime(item.key, it) },
                            onInclude = { vm.setInclude(item.key, it) },
                            onRemove = { vm.remove(item.key) },
                            onView = { viewing = it },
                        )
                    }
                }
            }
        }

        if (ui.items.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${chosen.size} of ${ui.items.size} selected  •  Sum: ${Fmt.money(chosenTotal)}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.clear() }, enabled = !ui.processing) { Text("Discard all") }
                        Button(
                            onClick = { vm.saveAll() },
                            enabled = !ui.processing && chosen.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Save ${chosen.size} verified transactions") }
                    }
                }
            }
        }
    }
    viewing?.let { ImageViewerDialog(it) { viewing = null } }
}

@Composable
private fun ReviewRow(
    item: ReviewItem,
    onAmount: (String) -> Unit,
    onTime: (Long) -> Unit,
    onInclude: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onView: (File) -> Unit,
) {
    val ctx = LocalContext.current
    val file = ImageStore.file(ctx, item.imageName)
    var text by remember(item.key) { mutableStateOf(item.amountText) }
    val invalid = Fmt.parseAmount(text) == null

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = file,
                contentDescription = "Screenshot (tap to enlarge)",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 76.dp, height = 120.dp).clip(RoundedCornerShape(8.dp)).clickable { onView(file) },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
                        onAmount(text)
                    },
                    label = { Text("Amount (Rs)") },
                    singleLine = true,
                    isError = invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(millis = item.timestamp, onChange = onTime, modifier = Modifier.fillMaxWidth())
                val note = when {
                    invalid -> "Amount not detected – please type it"
                    !item.confident -> "Low confidence – please check the amount"
                    !item.dateFound -> "No date on screenshot – upload time used"
                    !item.timeFound -> "Date read from screenshot – time not found"
                    else -> "Date & time read from screenshot"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (invalid || !item.confident) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = item.include, onCheckedChange = onInclude)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            }
        }
    }
}
