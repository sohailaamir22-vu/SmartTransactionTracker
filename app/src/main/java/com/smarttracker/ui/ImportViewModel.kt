package com.smarttracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smarttracker.data.TransactionRepository
import com.smarttracker.ocr.OcrParser
import com.smarttracker.ocr.ScreenshotProcessor
import com.smarttracker.util.Fmt
import com.smarttracker.util.ImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class ReviewItem(
    val key: Long,
    val imageName: String,
    val hash: String,
    val amountText: String,
    val timestamp: Long,
    val dateFound: Boolean,
    val timeFound: Boolean,
    val confident: Boolean,
    val include: Boolean,
)

data class ImportUi(
    val processing: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val items: List<ReviewItem> = emptyList(),
    val message: String? = null,
)

class ImportViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val repo = TransactionRepository(app)
    private val processor = ScreenshotProcessor()
    private var nextKey = 1L

    private val _ui = MutableStateFlow(ImportUi())
    val ui: StateFlow<ImportUi> = _ui

    /** OCR every picked screenshot (one by one) and add it to the review list. */
    fun process(uris: List<Uri>) {
        if (_ui.value.processing) {
            _ui.update { it.copy(message = "Still processing – please wait") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(processing = true, done = 0, total = uris.size) }
            var duplicates = 0
            var failed = 0
            var added = 0
            for (uri in uris) {
                try {
                    val stored = ImageStore.copyIn(ctx, uri)
                    val known = _ui.value.items.any { it.hash == stored.hash } || repo.hashExists(stored.hash)
                    if (known) {
                        duplicates++
                    } else {
                        val text = processor.readText(ImageStore.file(ctx, stored.name))
                        val r = OcrParser.parse(text)
                        val ts = if (r.date != null) {
                            Fmt.toMillis(r.date, r.time ?: LocalTime.now())
                        } else {
                            System.currentTimeMillis() // no date on the screenshot -> upload time
                        }
                        val item = ReviewItem(
                            key = nextKey++,
                            imageName = stored.name,
                            hash = stored.hash,
                            amountText = r.amount?.let { Fmt.amountField(it) } ?: "",
                            timestamp = ts,
                            dateFound = r.date != null,
                            timeFound = r.date != null && r.time != null,
                            confident = r.amount != null && r.confident,
                            include = true,
                        )
                        _ui.update { it.copy(items = it.items + item) }
                        added++
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                }
                _ui.update { it.copy(done = it.done + 1) }
            }
            val msg = buildString {
                append("$added screenshot(s) ready for review")
                if (duplicates > 0) append(", $duplicates duplicate(s) skipped")
                if (failed > 0) append(", $failed could not be read")
            }
            _ui.update { it.copy(processing = false, message = msg) }
        }
    }

    fun setAmount(key: Long, text: String) = change(key) { it.copy(amountText = text, confident = true) }
    fun setTime(key: Long, ms: Long) = change(key) { it.copy(timestamp = ms, dateFound = true, timeFound = true) }
    fun setInclude(key: Long, on: Boolean) = change(key) { it.copy(include = on) }

    private fun change(key: Long, f: (ReviewItem) -> ReviewItem) {
        _ui.update { s -> s.copy(items = s.items.map { if (it.key == key) f(it) else it }) }
    }

    fun remove(key: Long) {
        val item = _ui.value.items.firstOrNull { it.key == key } ?: return
        _ui.update { s -> s.copy(items = s.items.filterNot { it.key == key }) }
        discardFile(item)
    }

    fun clear() {
        val items = _ui.value.items
        _ui.update { it.copy(items = emptyList()) }
        items.forEach { discardFile(it) }
    }

    /** Saves every ticked row with a valid amount in one go. */
    fun saveAll() {
        viewModelScope.launch {
            val chosen = _ui.value.items.filter { it.include }
            if (chosen.isEmpty()) {
                _ui.update { it.copy(message = "Nothing selected to save") }
                return@launch
            }
            val bad = chosen.count { Fmt.parseAmount(it.amountText) == null }
            if (bad > 0) {
                _ui.update { it.copy(message = "$bad selected row(s) have an invalid amount – please correct them") }
                return@launch
            }
            val savedKeys = mutableSetOf<Long>()
            for (item in chosen) {
                val amount = Fmt.parseAmount(item.amountText) ?: continue
                if (repo.addScreenshot(amount, item.timestamp, item.imageName, item.hash)) savedKeys += item.key
            }
            _ui.update { s ->
                s.copy(
                    items = s.items.filterNot { it.key in savedKeys },
                    message = "Saved ${savedKeys.size} transaction(s)",
                )
            }
        }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    private fun discardFile(item: ReviewItem) {
        viewModelScope.launch(Dispatchers.IO) {
            // keep the file if a saved transaction already uses this screenshot
            if (!repo.hashExists(item.hash)) ImageStore.delete(ctx, item.imageName)
        }
    }

    override fun onCleared() {
        processor.close()
    }
}
