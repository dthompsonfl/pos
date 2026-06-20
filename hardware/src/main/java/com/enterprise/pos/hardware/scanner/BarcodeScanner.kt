package com.enterprise.pos.hardware.scanner

import android.view.KeyEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Abstraction over barcode scanners — handles both USB HID scanners (keyboard wedge) and
 *  Bluetooth/serial scanners. */
interface BarcodeScanner {
    val scans: Flow<String>
    suspend fun close()
}

/**
 * Most rugged POS barcode scanners present as a USB keyboard and inject keypresses with a
 * terminating ENTER. We hook Activity.dispatchKeyEvent / a custom OnKeyListener to capture
 * scans. The activity must forward key events via [onKeyEvent].
 */
@Suppress("unused")
class KeyboardWedgeScanner : BarcodeScanner {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val scans: Flow<String> = _scans.asSharedFlow()

    private val buffer = StringBuilder()
    private var lastKeyTime = 0L
    private val burstThresholdMs = 50L

    /** Forward each KeyEvent.KEYDOWN from the host Activity. */
    fun onKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastKeyTime > burstThresholdMs) buffer.clear()
        lastKeyTime = now

        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                if (buffer.isNotEmpty()) {
                    val s = buffer.toString()
                    buffer.clear()
                    _scans.tryEmit(s)
                }
                true
            }
            in 0..255 -> {
                val ch = event.unicodeChar
                if (ch != 0) buffer.append(ch.toChar())
                true
            }
            else -> false
        }
    }

    override suspend fun close() { /* nothing */ }
}

/** Camera-based scanner fallback using Google's ML Kit. */
class CameraScanner : BarcodeScanner {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val scans: Flow<String> = _scans.asSharedFlow()

    @Suppress("unused")
    fun emitScan(value: String) {
        _scans.tryEmit(value)
    }

    override suspend fun close() {
        // Real: release camera, close ML Kit scanner.
    }
}
