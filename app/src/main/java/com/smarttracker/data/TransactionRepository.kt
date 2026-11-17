package com.smarttracker.data

import android.content.Context
import com.smarttracker.util.Fmt
import com.smarttracker.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepository(context: Context) {
    private val ctx = context.applicationContext
    private val dao get() = AppDatabase.get(ctx).dao()

    val all: Flow<List<TransactionEntity>> get() = dao.observeAll()

    suspend fun addManual(amount: Double, description: String, timestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            insertWithRetry(
                TransactionEntity(
                    txnId = "", amount = amount, type = TxType.MANUAL.name,
                    timestamp = timestamp, description = description,
                )
            )
        }

    /** @return false when this screenshot is already stored (duplicate) */
    suspend fun addScreenshot(amount: Double, timestamp: Long, imageName: String, imageHash: String): Boolean =
        withContext(Dispatchers.IO) {
            insertWithRetry(
                TransactionEntity(
                    txnId = "", amount = amount, type = TxType.SCREENSHOT.name,
                    timestamp = timestamp, imageName = imageName, imageHash = imageHash,
                )
            )
        }

    suspend fun update(tx: TransactionEntity) = withContext(Dispatchers.IO) { dao.update(tx) }

    suspend fun delete(tx: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(tx)
        tx.imageName?.let { ImageStore.delete(ctx, it) }
    }

    suspend fun hashExists(hash: String): Boolean = withContext(Dispatchers.IO) { dao.countByHash(hash) > 0 }

    private suspend fun insertWithRetry(base: TransactionEntity): Boolean {
        repeat(5) {
            val rowId = dao.insert(base.copy(txnId = Fmt.newTxnId(base.timestamp)))
            if (rowId != -1L) return true
            val hash = base.imageHash
            if (hash != null && dao.countByHash(hash) > 0) return false // genuine duplicate screenshot
        }
        return false
    }
}
