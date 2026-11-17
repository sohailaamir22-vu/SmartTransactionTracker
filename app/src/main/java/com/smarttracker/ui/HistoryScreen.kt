@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smarttracker.data.TransactionEntity
import com.smarttracker.util.Fmt
import com.smarttracker.util.ImageStore
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// column widths (dp) of the spreadsheet-style table
private const val W_ID = 150
private const val W_AMT = 112
private const val W_TYPE = 96
private const val W_DATE = 100
private const val W_TIME = 98
private const val W_PREV = 72
private const val W_DESC = 190
private const val W_ACT = 104
private const val TABLE_W = W_ID + W_AMT + W_TYPE + W_DATE + W_TIME + W_PREV + W_DESC + W_ACT

private data class Group(val key: LocalDate, val label: String, val count: Int, val total: Double, val manual: Double, val shot: Double)

private fun dayOf(tx: TransactionEntity): LocalDate = Fmt.toLocal(tx.timestamp).toLocalDate()

private fun groupRows(rows: List<TransactionEntity>, mode: Int, newestFirst: Boolean): List<Group> {
    fun keyOf(d: LocalDate): LocalDate = when (mode) {
        1 -> d
        2 -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        else -> d.withDayOfMonth(1)
    }
    fun labelOf(k: LocalDate): String = when (mode) {
        1 -> Fmt.niceDate(k)
        2 -> "Week of " + Fmt.niceDate(k)
        else -> Fmt.monthName(k)
    }
    val groups = rows.groupBy { keyOf(dayOf(it)) }.map { (k, list) ->
        Group(
            key = k,
            label = labelOf(k),
            count = list.size,
            total = Fmt.sum(list.map { it.amount }),
            manual = Fmt.sum(list.filter { !it.isScreenshot }.map { it.amount }),
            shot = Fmt.sum(list.filter { it.isScreenshot }.map { it.amount }),
        )
    }
    return if (newestFirst) groups.sortedByDescending { it.key } else groups.sortedBy { it.key }
}

@Composable
fun HistoryScreen(vm: MainViewModel, onEdit: (TransactionEntity) -> Unit, onMessage: (String) -> Unit) {
    val all by vm.transactions.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 table, 1 daily, 2 weekly, 3 monthly
    var rangeStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeName by rememberSaveable { mutableStateOf("All") }
    var showRangePicker by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewing by remember { mutableStateOf<File?>(null) }

    val filtered = remember(all, query, newestFirst, rangeStart, rangeEnd) {
        val rs = rangeStart
        val re = rangeEnd
        val q = query.trim().lowercase()
        val list = all.filter { tx ->
            (rs == null || tx.timestamp >= rs) &&
                (re == null || tx.timestamp <= re) &&
                (q.isEmpty() ||
                    tx.txnId.lowercase().contains(q) ||
                    tx.description.lowercase().contains(q) ||
                    tx.typeLabel.lowercase().contains(q) ||
                    Fmt.plain(tx.amount).contains(q) ||
                    Fmt.date(tx.timestamp).contains(q))
        }
        if (newestFirst) list.sortedWith(compareByDescending<TransactionEntity> { it.timestamp }.thenByDescending { it.id })
        else list.sortedWith(compareBy<TransactionEntity> { it.timestamp }.thenBy { it.id })
    }
    val total = Fmt.sum(filtered.map { it.amount })
    val manualTotal = Fmt.sum(filtered.filter { !it.isScreenshot }.map { it.amount })
    val shotTotal = Fmt.sum(filtered.filter { it.isScreenshot }.map { it.amount })

    fun setRange(name: String, start: Long?, end: Long?) {
        rangeName = name
        rangeStart = start
        rangeEnd = end
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search ID, amount, date, note…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = rangeName == "All", onClick = { setRange("All", null, null) }, label = { Text("All dates") })
            FilterChip(selected = rangeName == "Today", onClick = {
                val t = LocalDate.now()
                setRange("Today", Fmt.startOfDayMillis(t), Fmt.startOfDayMillis(t.plusDays(1)) - 1)
            }, label = { Text("Today") })
            FilterChip(selected = rangeName == "Week", onClick = {
                val mon = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                setRange("Week", Fmt.startOfDayMillis(mon), Fmt.startOfDayMillis(mon.plusDays(7)) - 1)
            }, label = { Text("This week") })
            FilterChip(selected = rangeName == "Month", onClick = {
                val first = LocalDate.now().withDayOfMonth(1)
                setRange("Month", Fmt.startOfDayMillis(first), Fmt.startOfDayMillis(first.plusMonths(1)) - 1)
            }, label = { Text("This month") })
            FilterChip(
                selected = rangeName.startsWith("Custom"),
                onClick = { showRangePicker = true },
                label = { Text(if (rangeName.startsWith("Custom")) rangeName else "Custom range…") },
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Table", "Daily totals", "Weekly totals", "Monthly totals").forEachIndexed { i, name ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(name) })
            }
        }
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "${filtered.size} transactions  •  Total ${Fmt.money(total)}",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Manual ${Fmt.money(manualTotal)}  •  Screenshots ${Fmt.money(shotTotal)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (filtered.isEmpty()) {
                Text(
                    "No transactions found.\nTap + to add one or use the Import tab for screenshots.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (mode == 0) {
                TxTable(
                    rows = filtered,
                    newestFirst = newestFirst,
                    onToggleSort = { newestFirst = !newestFirst },
                    onEdit = onEdit,
                    onDelete = { toDelete = it },
                    onView = { viewing = it },
                )
            } else {
                val groups = remember(filtered, mode, newestFirst) { groupRows(filtered, mode, newestFirst) }
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.key.toString() }) { g ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(g.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text(Fmt.money(g.total), fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    "${g.count} transactions  •  Manual ${Fmt.money(g.manual)}  •  Screenshots ${Fmt.money(g.shot)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRangePicker) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = st.selectedStartDateMillis
                    val e = st.selectedEndDateMillis ?: s
                    if (s != null && e != null) {
                        val sd = Fmt.utcMillisToDate(s)
                        val ed = Fmt.utcMillisToDate(e)
                        setRange(
                            "Custom: $sd → $ed",
                            Fmt.startOfDayMillis(sd),
                            Fmt.startOfDayMillis(ed.plusDays(1)) - 1,
                        )
                    }
                    showRangePicker = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showRangePicker = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = st, modifier = Modifier.height(500.dp))
        }
    }

    toDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete transaction?") },
            text = { Text("${tx.txnId}\n${Fmt.money(tx.amount)} • ${Fmt.dateTime(tx.timestamp)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(tx)
                    toDelete = null
                    onMessage("Transaction deleted")
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
    viewing?.let { ImageViewerDialog(it) { viewing = null } }
}

@Composable
private fun TxTable(
    rows: List<TransactionEntity>,
    newestFirst: Boolean,
    onToggleSort: () -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
    onView: (File) -> Unit,
) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(TABLE_W.dp).fillMaxHeight()) {
            // header
            Row(
                Modifier.fillMaxWidth().height(44.dp).background(MaterialTheme.colorScheme.primary),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCell("Transaction ID", W_ID)
                HeaderCell("Amount", W_AMT)
                HeaderCell("Type", W_TYPE)
                SortHeaderCell("Date", W_DATE, newestFirst, onToggleSort)
                SortHeaderCell("Time", W_TIME, newestFirst, onToggleSort)
                HeaderCell("Preview", W_PREV)
                HeaderCell("Description", W_DESC)
                HeaderCell("Actions", W_ACT)
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(rows, key = { _, t -> t.id }) { index, tx ->
                    val bg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).background(bg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cell(tx.txnId, W_ID, small = true)
                        Cell(Fmt.money(tx.amount), W_AMT, bold = true)
                        Cell(tx.typeLabel, W_TYPE)
                        Cell(Fmt.date(tx.timestamp), W_DATE)
                        Cell(Fmt.time(tx.timestamp), W_TIME, small = true)
                        Box(Modifier.width(W_PREV.dp), contentAlignment = Alignment.Center) {
                            val img = tx.imageName
                            if (img != null) {
                                val f = ImageStore.file(ctx, img)
                                AsyncImage(
                                    model = f,
                                    contentDescription = "Screenshot preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).clickable { onView(f) },
                                )
                            } else {
                                Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Cell(tx.description.ifBlank { "—" }, W_DESC, maxLines = 2)
                        Row(Modifier.width(W_ACT.dp), horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { onEdit(tx) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                            IconButton(onClick = { onDelete(tx) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, w: Int) {
    Text(
        text,
        modifier = Modifier.width(w.dp).padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
private fun SortHeaderCell(text: String, w: Int, newestFirst: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.width(w.dp).clickable { onClick() }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
        Icon(
            if (newestFirst) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = "Sort",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun Cell(text: String, w: Int, bold: Boolean = false, small: Boolean = false, maxLines: Int = 1) {
    Text(
        text,
        modifier = Modifier.width(w.dp).padding(horizontal = 8.dp),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
