package com.smarttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smarttracker.data.Stats
import com.smarttracker.data.TransactionEntity
import com.smarttracker.data.TransactionRepository
import com.smarttracker.data.computeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TransactionRepository(app)

    val transactions: StateFlow<List<TransactionEntity>> =
        repo.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Recomputed instantly whenever a transaction is added, edited or deleted. */
    val stats: StateFlow<Stats> =
        transactions.map { computeStats(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, computeStats(emptyList()))

    fun addManual(amount: Double, description: String, timestamp: Long) {
        viewModelScope.launch { repo.addManual(amount, description, timestamp) }
    }

    fun update(tx: TransactionEntity) {
        viewModelScope.launch { repo.update(tx) }
    }

    fun delete(tx: TransactionEntity) {
        viewModelScope.launch { repo.delete(tx) }
    }
}
