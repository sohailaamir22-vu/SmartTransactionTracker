package com.smarttracker.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** Runs the on-device ML Kit recogniser (works fully offline) and rebuilds text rows. */
class ScreenshotProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readText(file: File): String = withContext(Dispatchers.Default) {
        val bitmap = decode(file) ?: return@withContext ""
        try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val lines = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.bottom) }
            }
            if (lines.isEmpty()) result.text else OcrParser.rowsFromLines(lines)
        } finally {
            bitmap.recycle()
        }
    }

    fun close() = recognizer.close()

    private fun decode(file: File, maxSide: Int = 2600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
