@file:OptIn(ExperimentalMaterial3Api::class)
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint

package com.smarttracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.smarttracker.data.TransactionEntity
import com.smarttracker.util.Fmt
import com.smarttracker.util.ImageStore
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

// ------------------------------------------------------------------ cards

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// ------------------------------------------------------------------ charts

private fun short(v: Double): String = when {
    v >= 1_000_000 -> String.format(Locale.US, "%.1fM", v / 1_000_000)
    v >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1_000)
    else -> String.format(Locale.US, "%.0f", v)
}

@Composable
fun BarChart(data: List<Pair<String, Double>>, color: Color, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(data) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(800))
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textPx = with(LocalDensity.current) { 10.sp.toPx() }
    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        if (data.isEmpty()) return@Canvas
        val max = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
        val topPad = textPx * 1.8f
        val bottomPad = textPx * 2.2f
        val chartH = size.height - topPad - bottomPad
        val slot = size.width / data.size
        val barW = slot * 0.55f
        val paint = android.graphics.Paint().apply {
            this.color = labelColor
            textSize = textPx
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        data.forEachIndexed { i, (label, v) ->
            val h = ((v / max).toFloat() * chartH * anim.value).coerceAtLeast(2f)
            val x = slot * i + (slot - barW) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, topPad + chartH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(8f, 8f),
            )
            drawContext.canvas.nativeCanvas.drawText(label, slot * i + slot / 2f, size.height - textPx * 0.5f, paint)
            if (v > 0) {
                drawContext.canvas.nativeCanvas.drawText(short(v), slot * i + slot / 2f, topPad + chartH - h - 6f, paint)
            }
        }
    }
}

@Composable
fun DonutChart(manual: Double, shot: Double, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(manual, shot) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900))
    }
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = manual + shot
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = Stroke(width = 26f, cap = StrokeCap.Butt)
            val inset = 13f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            if (total > 0) {
                val manualSweep = (manual / total).toFloat() * 360f * anim.value
                val shotSweep = (shot / total).toFloat() * 360f * anim.value
                drawArc(c1, -90f, manualSweep, false, Offset(inset, inset), arcSize, style = stroke)
                drawArc(c2, -90f + manualSweep, shotSweep, false, Offset(inset, inset), arcSize, style = stroke)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendRow(c1, "Manual", manual, total)
            LegendRow(c2, "Screenshot", shot, total)
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: Double, total: Double) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            val pct = if (total > 0) String.format(Locale.US, " (%.0f%%)", value / total * 100) else ""
            Text(Fmt.money(value) + pct, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------ pickers & dialogs

/** Button that opens a date picker followed by a time picker. [millis] == null shows [autoLabel]. */
@Composable
fun DateTimeField(
    millis: Long?,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    autoLabel: String = "Pick date & time",
) {
    var stage by remember { mutableIntStateOf(0) } // 0 closed, 1 date, 2 time
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    val base = millis ?: System.currentTimeMillis()
    val label = if (millis == null) autoLabel else Fmt.dateTime(millis)

    OutlinedButton(onClick = { stage = 1 }, modifier = modifier) {
        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }

    if (stage == 1) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = Fmt.dateToUtcMillis(Fmt.toLocal(base).toLocalDate())
        )
        DatePickerDialog(
            onDismissRequest = { stage = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val sel = dateState.selectedDateMillis
                    if (sel != null) {
                        pickedDate = Fmt.utcMillisToDate(sel)
                        stage = 2
                    } else {
                        stage = 0
                    }
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
    if (stage == 2) {
        val local = Fmt.toLocal(base)
        val timeState = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text("Select time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val d = pickedDate
                    if (d != null) onChange(Fmt.toMillis(d, LocalTime.of(timeState.hour, timeState.minute)))
                    stage = 0
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("Cancel") } },
        )
    }
}

/** Add (initial == null) or edit a manual / screenshot transaction. */
@Composable
fun TransactionDialog(
    initial: TransactionEntity?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, description: String, timestamp: Long) -> Unit,
) {
    val ctx = LocalContext.current
    var amount by remember { mutableStateOf(initial?.let { Fmt.amountField(it.amount) } ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var ts by remember { mutableStateOf(initial?.timestamp) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add transaction" else "Edit transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial != null) {
                    Text("ID: ${initial.txnId}  •  ${initial.typeLabel}", style = MaterialTheme.typography.labelMedium)
                }
                val errText = error
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v ->
                        amount = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
                        error = null
                    },
                    label = { Text("Amount (Rs)") },
                    singleLine = true,
                    isError = errText != null,
                    supportingText = if (errText != null) ({ Text(errText) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                DateTimeField(
                    millis = ts,
                    onChange = { ts = it },
                    autoLabel = "Now – recorded automatically",
                    modifier = Modifier.fillMaxWidth(),
                )
                val img = initial?.imageName
                if (img != null) {
                    AsyncImage(
                        model = ImageStore.file(ctx, img),
                        contentDescription = "Screenshot",
                        modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Fmt.parseAmount(amount)
                if (v == null) error = "Enter a valid amount greater than 0"
                else onSave(v, desc.trim(), ts ?: System.currentTimeMillis())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ImageViewerDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Screenshot",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
