package com.enterprise.pos.hardware.printer

import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Payment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Receipt renderer — formats an [Order] (with its [Payment]s) as ESC/POS byte stream. */
class ReceiptRenderer(
    private val storeName: String = "Enterprise POS",
    private val storeAddress: String = "123 Main St",
    private val storePhone: String = "(555) 010-2025",
    private val footer: String = "Thank you for your business!"
) {
    /** Returns ESC/POS bytes ready to send to a thermal printer. */
    fun render(order: Order, payments: List<Payment>, includeTipLine: Boolean = true): ByteArray {
        val out = mutableListOf<Byte>()
        fun txt(s: String) { s.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }; out.add(0x0A.toByte()) }
        fun raw(bytes: ByteArray) { bytes.forEach { out.add(it) } }
        fun line() { txt("".padEnd(42, '-')) }
        fun center(s: String) {
            val pad = ((42 - s.length) / 2).coerceAtLeast(0)
            txt(" ".repeat(pad) + s)
        }

        // ESC @ — initialize
        raw(byteArrayOf(0x1B.toByte(), 0x40.toByte()))
        // ESC a 1 — center
        raw(byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x01.toByte()))
        txt(storeName)
        txt(storeAddress)
        txt(storePhone)
        raw(byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x00.toByte())) // left
        line()

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(order.createdAt))
        txt("Order: ${order.id.value.takeLast(8).uppercase()}")
        txt("Date: $date")
        txt("Server: ${order.employeeId.value.takeLast(4).uppercase()}")
        order.tableName?.let { txt("Table: $it") }
        txt("Mode: ${order.diningMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }}")
        if (order.guestCount > 0) txt("Guests: ${order.guestCount}")
        line()

        for (line0 in order.lines.filter { it.lineType == OrderLineType.ITEM }) {
            val qty = line0.quantity.asInt.toString().padStart(3)
            val name = line0.name.take(28).padEnd(28)
            val total = line0.lineTotal.format().padStart(11)
            txt("$qty $name$total")
            line0.notes?.takeIf { it.isNotBlank() }?.let { txt("    * $it") }
        }

        line()
        val subtotal = order.subtotal.format().padStart(42 - "Subtotal".length)
        txt("Subtotal$subtotal")
        if (!order.totalDiscount.isZero()) {
            val disc = "-" + order.totalDiscount.format().padStart(42 - "Discount".length - 1)
            txt("Discount$disc")
        }
        for (tax in order.taxLines) {
            val taxLine = tax.amount.format().padStart(42 - tax.name.length)
            txt("${tax.name}$taxLine")
        }
        if (includeTipLine && !order.tip.isZero()) {
            val tip = order.tip.format().padStart(42 - "Tip".length)
            txt("Tip$tip")
        }
        line()
        val grand = order.grandTotal.format()
        val totalPad = " ".repeat((42 - "TOTAL".length - grand.length).coerceAtLeast(0))
        txt("TOTAL$totalPad$grand")
        line()

        if (payments.isNotEmpty()) {
            txt("Payment:")
            for (p in payments) {
                val prov = p.provider.padEnd(15)
                val amt = p.amount.format().padStart(42 - 15)
                txt("  $prov$amt")
                p.cardBrand?.let { txt("    $it •••• ${p.last4 ?: ""}") }
            }
            line()
        }

        raw(byteArrayOf(0x1B.toByte(), 0x61.toByte(), 0x01.toByte())) // center
        txt("")
        center(footer)
        txt("")
        // Kick the drawer: ESC pm t1 t2
        raw(byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte()))
        // Cut paper: GS V 1
        raw(byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x01.toByte()))

        return out.toByteArray()
    }

    /** Render an order-only kitchen ticket — no prices, just routing + items + notes. */
    fun renderKitchenTicket(order: Order): ByteArray {
        val out = mutableListOf<Byte>()
        fun txt(s: String) { s.toByteArray(Charsets.US_ASCII).forEach { out.add(it) }; out.add(0x0A.toByte()) }
        fun raw(bytes: ByteArray) { bytes.forEach { out.add(it) } }

        raw(byteArrayOf(0x1B.toByte(), 0x40.toByte()))
        raw(byteArrayOf(0x1B.toByte(), 0x21.toByte(), 0x30.toByte())) // double height + width
        txt("ORDER ${order.id.value.takeLast(6).uppercase()}")
        raw(byteArrayOf(0x1B.toByte(), 0x21.toByte(), 0x00.toByte())) // reset
        txt("Table: ${order.tableName ?: order.diningMode.name}")
        txt("Guests: ${order.guestCount}")
        txt(SimpleDateFormat("HH:mm", Locale.US).format(Date(order.createdAt)))
        txt("".padEnd(42, '-'))
        for (line0 in order.lines.filter { it.lineType == OrderLineType.ITEM }) {
            txt("${line0.quantity.asInt}x  ${line0.name}")
            line0.notes?.takeIf { it.isNotBlank() }?.let { txt("    >> $it") }
        }
        txt("".padEnd(42, '-'))
        raw(byteArrayOf(0x1D.toByte(), 0x56.toByte(), 0x01.toByte())) // cut
        return out.toByteArray()
    }
}

/** Abstraction over a printer transport. */
interface PrinterDriver {
    val displayName: String
    val isConnected: Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun print(data: ByteArray): Result<Unit>
}

/** USB printer driver — uses Android USB Host API. */
class UsbPrinterDriver(
    override val displayName: String,
    private val usbDeviceId: String
) : PrinterDriver {
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Result<Unit> = Result.catching {
        // Real: UsbManager.openDevice(device) → claim bulk OUT endpoint.
        isConnected = true
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        // Real: releaseInterface, close.
        isConnected = false
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        require(isConnected) { "Printer not connected" }
        // Real: connection.bulkTransfer(endpoint, data, data.size, TIMEOUT)
    }
}

/** Network printer driver — sends raw bytes over TCP port 9100. */
class NetworkPrinterDriver(
    override val displayName: String,
    private val host: String,
    private val port: Int = 9100
) : PrinterDriver {
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Result<Unit> = Result.catching {
        // Real: java.net.Socket(host, port)
        isConnected = true
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        isConnected = false
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        require(isConnected) { "Printer not connected" }
        // Real: socket.outputStream.write(data); flush
    }
}

/** Bluetooth printer driver — uses RFCOMM SPP. */
class BluetoothPrinterDriver(
    override val displayName: String,
    private val macAddress: String
) : PrinterDriver {
    override var isConnected: Boolean = false
        private set

    override suspend fun connect(): Result<Unit> = Result.catching {
        // Real: BluetoothSocket.createRfcommSocketToServiceRecord(SPP_UUID); connect()
        isConnected = true
    }

    override suspend fun disconnect(): Result<Unit> = Result.catching {
        isConnected = false
    }

    override suspend fun print(data: ByteArray): Result<Unit> = Result.catching {
        require(isConnected) { "Printer not connected" }
        // Real: outputStream.write(data); flush
    }
}

class PrinterManager(
    private val drivers: MutableList<PrinterDriver> = mutableListOf()
) {
    fun register(driver: PrinterDriver) { drivers.add(driver) }
    fun connectedDrivers(): List<PrinterDriver> = drivers.filter { it.isConnected }

    suspend fun printReceipt(renderer: ReceiptRenderer, order: Order, payments: List<Payment>): Result<Unit> {
        if (drivers.isEmpty()) return Result.success(Unit) // No-op when no printer registered.
        val data = renderer.render(order, payments)
        var lastError: Result<Unit>? = null
        for (d in drivers.filter { it.isConnected }) {
            val r = d.print(data)
            if (r is Result.Success) return r
            lastError = r
        }
        return lastError ?: Result.failure(com.enterprise.pos.core.AppError.Hardware("Printer", "No connected printers"))
    }

    suspend fun printKitchenTicket(renderer: ReceiptRenderer, order: Order): Result<Unit> {
        val data = renderer.renderKitchenTicket(order)
        return drivers.filter { it.isConnected }.firstOrNull()?.print(data)
            ?: Result.success(Unit)
    }
}
