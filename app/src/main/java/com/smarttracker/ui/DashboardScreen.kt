package com.smarttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smarttracker.util.Fmt

@Composable
fun DashboardScreen(vm: MainViewModel) {
    val s by vm.stats.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(brush).padding(20.dp)
            ) {
                Text("Grand total of all amounts", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(Fmt.money(s.total), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${s.count} transactions",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total transactions", "${s.count}", Modifier.weight(1f), "${s.manualCount} manual • ${s.shotCount} screenshots")
                StatCard("Today's total", Fmt.money(s.today), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Manually added", Fmt.money(s.manual), Modifier.weight(1f), "${s.manualCount} entries")
                StatCard("From screenshots", Fmt.money(s.shot), Modifier.weight(1f), "${s.shotCount} entries")
            }
        }
        item { StatCard("This month's total", Fmt.money(s.month), Modifier.fillMaxWidth()) }
        item { ChartCard("Last 7 days") { BarChart(s.last7, MaterialTheme.colorScheme.primary) } }
        item { ChartCard("Last 6 months") { BarChart(s.last6Months, MaterialTheme.colorScheme.secondary) } }
        item { ChartCard("Manual vs screenshot") { DonutChart(s.manual, s.shot) } }
    }
}
