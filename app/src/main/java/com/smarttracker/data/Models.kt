package com.smarttracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxType(val label: String) {
    MANUAL("Manual"),
    SCREENSHOT("Screenshot"),
}

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["txnId"], unique = true),
        Index(value = ["imageHash"], unique = true), // blocks the same screenshot being added twice
        Index(value = ["timestamp"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: String,
    val amount: Double,
    /** [TxType] name */
    val type: String,
    /** epoch millis of the transaction (manual: time added; screenshot: read from image or upload time) */
    val timestamp: Long,
    val description: String = "",
    /** file name inside filesDir/screenshots (null for manual entries) */
    val imageName: String? = null,
    /** SHA-256 of the screenshot bytes (null for manual entries) */
    val imageHash: String? = null,
) {
    val isScreenshot: Boolean get() = type == TxType.SCREENSHOT.name
    val typeLabel: String get() = if (isScreenshot) TxType.SCREENSHOT.label else TxType.MANUAL.label
}
