package com.enterprise.pos.hardware.scanner

import android.graphics.Bitmap
import android.view.KeyEvent
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Abstract interface for barcode scanners supporting USB HID keyboard emulation,
 * Bluetooth serial, and camera-based scanning via ML Kit.
 */
interface BarcodeScannerManager {
    val scans: Flow<BarcodeScan>
    suspend fun initialize(): Result<Unit>
    suspend fun close(): Result<Unit>
    fun isSupported(format: BarcodeFormat): Boolean
}

/** A single barcode scan event with decoded value and detected format. */
data class BarcodeScan(
    val value: String,
    val format: BarcodeFormat,
    val timestamp: Long = System.currentTimeMillis()
)

enum class BarcodeFormat {
    UPC_A, UPC_E, EAN_8, EAN_13, CODE_39, CODE_128, QR_CODE, DATA_MATRIX, PDF_417, UNKNOWN
}

/**
 * USB HID keyboard-emulation scanner manager.
 *
 * Most rugged POS barcode scanners present as a USB keyboard and inject keypresses with a
 * terminating ENTER key. The host Activity forwards key events via [onKeyEvent].
 */
class UsbHidBarcodeScannerManager : BarcodeScannerManager {

    private val _scans = MutableSharedFlow<BarcodeScan>(extraBufferCapacity = 16)
    override val scans: Flow<BarcodeScan> = _scans.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastKeyTime = 0L
    private val burstThresholdMs = 50L

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)
    override suspend fun close(): Result<Unit> = Result.success(Unit)
    override fun isSupported(format: BarcodeFormat): Boolean = true

    /**
     * Forward each [KeyEvent.ACTION_DOWN] from the host Activity.
     * Returns true if the event was consumed (i.e., was part of a barcode scan).
     */
    fun onKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastKeyTime > burstThresholdMs) buffer.clear()
        lastKeyTime = now

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                if (buffer.isNotEmpty()) {
                    val value = buffer.toString()
                    buffer.clear()
                    val format = detectFormat(value)
                    _scans.tryEmit(BarcodeScan(value, format))
                }
                true
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9,
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
                val ch = event.unicodeChar
                if (ch != 0) buffer.append(ch.toChar())
                true
            }
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MINUS,
            KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_LEFT_BRACKET,
            KeyEvent.KEYCODE_RIGHT_BRACKET -> {
                val ch = event.unicodeChar
                if (ch != 0) buffer.append(ch.toChar())
                true
            }
            else -> false
        }
    }

    private fun detectFormat(value: String): BarcodeFormat {
        return when {
            value.length == 12 && value.all { it.isDigit() } -> BarcodeFormat.UPC_A
            value.length == 8 && value.all { it.isDigit() } -> BarcodeFormat.EAN_8
            value.length == 13 && value.all { it.isDigit() } -> BarcodeFormat.EAN_13
            value.startsWith("http://") || value.startsWith("https://") -> BarcodeFormat.QR_CODE
            value.length > 20 && value.all { it.isLetterOrDigit() } -> BarcodeFormat.CODE_128
            value.length > 10 && value.all { it.isLetterOrDigit() } -> BarcodeFormat.CODE_39
            else -> BarcodeFormat.UNKNOWN
        }
    }
}

/**
 * Camera-based barcode scanner using Google ML Kit.
 *
 * The host app must feed camera frames via [processImage]. The manager handles
 * barcode detection and emits decoded values on the [scans] Flow.
 */
class CameraBarcodeScannerManager : BarcodeScannerManager {

    private val _scans = MutableSharedFlow<BarcodeScan>(extraBufferCapacity = 16)
    override val scans: Flow<BarcodeScan> = _scans.asSharedFlow()

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417
            )
            .build()
    )

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun isSupported(format: BarcodeFormat): Boolean = true

    /** Process a camera frame bitmap. Call this from the host apps camera preview. */
    fun processImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { barcode ->
                    barcode.rawValue?.let { value ->
                        val format = mapMlKitFormat(barcode.format)
                        _scans.tryEmit(BarcodeScan(value, format))
                    }
                }
            }
            .addOnFailureListener { /* silently drop frame errors */ }
    }

    override suspend fun close(): Result<Unit> = Result.catching {
        scanner.close()
    }

    private fun mapMlKitFormat(format: Int): BarcodeFormat {
        return when (format) {
            Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
            Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
            Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
            Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
            Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
            Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
            Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
            Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
            else -> BarcodeFormat.UNKNOWN
        }
    }
}

/**
 * Simulated barcode scanner for development and testing.
 * Emits manually injected scan values.
 */
class SimulatedBarcodeScannerManager : BarcodeScannerManager {

    private val _scans = MutableSharedFlow<BarcodeScan>(replay = 1, extraBufferCapacity = 16)
    override val scans: Flow<BarcodeScan> = _scans.asSharedFlow()

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)
    override suspend fun close(): Result<Unit> = Result.success(Unit)
    override fun isSupported(format: BarcodeFormat): Boolean = true

    /** Emit a simulated scan for testing or debugging. */
    fun emitScan(value: String, format: BarcodeFormat = BarcodeFormat.UNKNOWN) {
        _scans.tryEmit(BarcodeScan(value, format))
    }
}
