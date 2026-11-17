@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarttracker.data.TransactionEntity
import kotlinx.coroutines.launch

private data class Tab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("Dashboard", Icons.Filled.Home),
    Tab("History", Icons.Filled.List),
    Tab("Import", Icons.Filled.AddCircle),
    Tab("More", Icons.Filled.Settings),
)

@Composable
fun TrackerApp(themeMode: String, onThemeChange: (String) -> Unit) {
    val vm: MainViewModel = viewModel()
    val importVm: ImportViewModel = viewModel()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(if (tab == 0) "Smart Transaction Tracker" else TABS[tab].title, fontWeight = FontWeight.Bold) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (tab == 0 || tab == 1) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = tab, label = "tabs") { t ->
                when (t) {
                    0 -> DashboardScreen(vm)
                    1 -> HistoryScreen(vm, onEdit = { editing = it }, onMessage = toast)
                    2 -> ImportScreen(importVm, toast)
                    else -> MoreScreen(vm, themeMode, onThemeChange, toast)
                }
            }
        }
    }

    if (showAdd) {
        TransactionDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { amount, desc, ts ->
                vm.addManual(amount, desc, ts)
                showAdd = false
                toast("Transaction added")
            },
        )
    }
    editing?.let { tx ->
        TransactionDialog(
            initial = tx,
            onDismiss = { editing = null },
            onSave = { amount, desc, ts ->
                vm.update(tx.copy(amount = amount, description = desc, timestamp = ts))
                editing = null
                toast("Transaction updated")
            },
        )
    }
}
