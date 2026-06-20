package com.enterprise.pos.hardware.escpos

/**
 * Raw ESC/POS byte commands for common thermal printer operations.
 * Supports Epson, Star, Bixolon, and most generic ESC/POS-compatible printers.
 *
 * All byte arrays are constructed with explicit [toByte()] calls to ensure
 * correct signed-byte representation for Kotlin.
 */
object EscPosCommands {

    // Initialization and line control
    val INIT = byteArrayOf(0x1B.toByte(), 0x40.toByte())
    val LINE_FEED = byteArrayOf(0x0A.toByte())
    val CARRIAGE_RETURN = byteArrayOf(0x0D.toByte())
    val FORM_FEED = byteArrayOf(0x0C.toByte())
    val BACKSPACE = byteArrayOf(0x08.toByte())
    val HORIZONTAL_TAB = byteArrayOf(0x09.toByte())

    // Alignment
    val ALIGN_LEFT = byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x00.toByte())
    val ALIGN_CENTER = byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x01.toByte())
    val ALIGN_RIGHT = byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x02.toByte())

    // Character formatting
    val BOLD_ON = byteArrayOf(0x1B.toByte(), 0x45.toByte(), 0x01.toByte())
    val BOLD_OFF = byteArrayOf(0x1B.toByte(), 0x45.toByte(), 0x00.toByte())
    val UNDERLINE_ON = byteArrayOf(0x1B.toByte(), 0x2D.toByte(), 0x01.toByte())
    val UNDERLINE_OFF = byteArrayOf(0x1B.toByte(), 0x2D.toByte(), 0x00.toByte())
    val FONT_A = byteArrayOf(0x1B.toByte(), 0x4D.toByte(), 0x00.toByte())
    val FONT_B = byteArrayOf(0x1B.toByte(), 0x4D.toByte(), 0x01.toByte())
    val FONT_C = byteArrayOf(0x1B.toByte(), 0x4D.toByte(), 0x02.toByte())

    // Size control (GS ! n)
    val NORMAL_SIZE = byteArrayOf(0x1D.toByte(), 0x21.toByte(), 0x00.toByte())
    val DOUBLE_WIDTH = byteArrayOf(0x1D.toByte(), 0x21.toByte(), 0x10.toByte())
    val DOUBLE_HEIGHT = byteArrayOf(0x1D.toByte(), 0x21.toByte(), 0x01.toByte())
    val DOUBLE_WIDTH_HEIGHT = byteArrayOf(0x1D.toByte(), 0x21.toByte(), 0x11.toByte())
    val TRIPLE_WIDTH_HEIGHT = byteArrayOf(0x1D.toByte(), 0x21.toByte(), 0x22.toByte())

    // Paper cutting
    val CUT_FULL = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x01.toByte())
    val CUT_PARTIAL = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x00.toByte())
    val CUT_FULL_FEED = byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x41.toByte(), 0x00.toByte())

    // Cash drawer kick
    val OPEN_DRAWER = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte())
    val OPEN_DRAWER_2 = byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x01.toByte(), 0x19.toByte(), 0xFA.toByte())

    // Barcode
    fun barcodeEan13(data: String): ByteArray {
        val clean = data.filter { it.isDigit() }
        require(clean.length == 13) { "EAN-13 must be exactly 13 digits" }
        return byteArrayOf(0x1D.toByte(), 0x6B.toByte(), 0x02.toByte()) +
            clean.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x00.toByte())
    }

    fun barcodeCode128(data: String): ByteArray {
        val clean = data.filter { it.code in 0..127 }
        return byteArrayOf(0x1D.toByte(), 0x6B.toByte(), 0x49.toByte(), clean.length.toByte()) +
            clean.toByteArray(Charsets.US_ASCII)
    }

    // QR Code (GS ( k)
    fun qrCode(data: String, size: Int = 3): ByteArray {
        require(size in 1..16) { "QR code size must be 1..16" }
        val bytes = data.toByteArray(Charsets.UTF_8)
        val pL = (bytes.size + 3) % 256
        val pH = (bytes.size + 3) / 256

        return byteArrayOf(
            // Store QR code data
            0x1D.toByte(), 0x28.toByte(), 0x6B.toByte(),
            pL.toByte(), pH.toByte(), 0x31.toByte(), 0x50.toByte(), 0x30.toByte()
        ) + bytes +
            byteArrayOf(
                // Set QR code size
                0x1D.toByte(), 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(),
                0x31.toByte(), 0x43.toByte(), size.toByte(),
                // Set QR code error correction level
                0x1D.toByte(), 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(),
                0x31.toByte(), 0x45.toByte(), 0x31.toByte(),
                // Print QR code
                0x1D.toByte(), 0x28.toByte(), 0x6B.toByte(), 0x03.toByte(), 0x00.toByte(),
                0x31.toByte(), 0x51.toByte(), 0x30.toByte()
            )
    }

    // Feed control
    fun feedLines(n: Int): ByteArray {
        require(n in 0..255) { "Feed lines must be 0..255" }
        return byteArrayOf(0x1B.toByte(), 0x64.toByte(), n.toByte())
    }

    fun feedPaper(n: Int): ByteArray {
        require(n in 0..255) { "Feed paper must be 0..255" }
        return byteArrayOf(0x1B.toByte(), 0x4A.toByte(), n.toByte())
    }

    // Line builder
    fun textLine(s: String, charset: java.nio.charset.Charset = Charsets.US_ASCII): ByteArray {
        return s.toByteArray(charset) + LINE_FEED
    }

    fun centeredLine(s: String, width: Int = 42): ByteArray {
        val pad = ((width - s.length) / 2).coerceAtLeast(0)
        return ALIGN_CENTER + textLine(" ".repeat(pad) + s) + ALIGN_LEFT
    }

    fun rightAlignedLine(left: String, right: String, width: Int = 42): ByteArray {
        val available = (width - left.length - right.length).coerceAtLeast(0)
        return textLine(left + " ".repeat(available) + right)
    }

    fun separator(width: Int = 42, char: Char = '-'): ByteArray {
        return textLine("".padEnd(width, char))
    }
}
