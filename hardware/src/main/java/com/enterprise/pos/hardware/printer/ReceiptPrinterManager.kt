package com.enterprise.pos.hardware.printer

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.Payment
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract interface for receipt printers supporting USB, Bluetooth, and network transports.
 * Manages printer discovery, connection, and all receipt printing operations.
 */
interface ReceiptPrinterManager {
    val status: StateFlow<PrinterStatus>
    val connectedPrinters: List<PrinterInfo>

    suspend fun discoverPrinters(): Result<List<PrinterInfo>>
    suspend fun connectPrinter(printer: PrinterInfo): Result<Unit>
    suspend fun disconnectPrinter(printerId: String): Result<Unit>

    suspend fun printReceipt(order: Order, payments: List<Payment>): Result<Unit>
    suspend fun printKitchenChit(order: Order): Result<Unit>
    suspend fun printReport(title: String, lines: List<String>): Result<Unit>
    suspend fun printXReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit>
    suspend fun printZReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit>
    suspend fun openCashDrawer(): Result<Unit>

    suspend fun disconnectAll(): Result<Unit>
}

/** Metadata describing a discovered or configured printer. */
data class PrinterInfo(
    val id: String,
    val name: String,
    val model: String,
    val connectionType: PrinterConnectionType,
    val address: String?,
    val isConnected: Boolean
)

enum class PrinterConnectionType { USB, BLUETOOTH, NETWORK }

enum class PrinterStatus {
    IDLE,
    DISCOVERING,
    CONNECTING,
    PRINTING,
    ERROR
}

/**
 * Production implementation using ESC/POS thermal printers.
 * Supports multiple simultaneous transports; prints to the first connected printer.
 */
class EscPosReceiptPrinterManager(
    private val receiptBuilder: EscPosReceiptBuilder,
    private val logger: com.enterprise.pos.core.Logger
) : ReceiptPrinterManager {

    private val printers = mutableMapOf<String, EscPosPrinter>()
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(PrinterStatus.IDLE)
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val connectedPrinters: List<PrinterInfo>
        get() = printers.values.filter { it.isConnected }.map { it.info }

    fun registerPrinter(printer: EscPosPrinter) {
        printers[printer.info.id] = printer
    }

    override suspend fun discoverPrinters(): Result<List<PrinterInfo>> = Result.catching {
        _status.value = PrinterStatus.DISCOVERING
        try {
            val discovered = mutableListOf<PrinterInfo>()

            // USB discovery via UsbManager
            // Network discovery via mDNS or broadcast (placeholder)
            // Bluetooth discovery via BluetoothAdapter

            // For now, return registered printers that are not yet connected
            discovered.addAll(printers.values.filter { !it.isConnected }.map { it.info })
            discovered
        } finally {
            _status.value = PrinterStatus.IDLE
        }
    }

    override suspend fun connectPrinter(printer: PrinterInfo): Result<Unit> = Result.catching {
        _status.value = PrinterStatus.CONNECTING
        try {
            val p = printers[printer.id]
                ?: throw IllegalStateException("Printer ${printer.id} not registered")
            val result = p.connect()
            if (result is Result.Failure) {
                throw result.error.toException()
            }
            logger.i(TAG, "Printer connected: ${printer.name}")
        } finally {
            _status.value = PrinterStatus.IDLE
        }
    }

    override suspend fun disconnectPrinter(printerId: String): Result<Unit> = Result.catching {
        val p = printers[printerId] ?: return Result.success(Unit)
        val result = p.disconnect()
        if (result is Result.Failure) {
            throw result.error.toException()
        }
        logger.i(TAG, "Printer disconnected: $printerId")
    }

    override suspend fun printReceipt(order: Order, payments: List<Payment>): Result<Unit> =
        printWithFirstConnected { printer ->
            val data = receiptBuilder.buildReceipt(order, payments)
            printer.print(data)
        }

    override suspend fun printKitchenChit(order: Order): Result<Unit> =
        printWithFirstConnected { printer ->
            val data = receiptBuilder.buildKitchenChit(order)
            printer.print(data)
        }

    override suspend fun printReport(title: String, lines: List<String>): Result<Unit> =
        printWithFirstConnected { printer ->
            val data = receiptBuilder.buildReport(title, lines)
            printer.print(data)
        }

    override suspend fun printXReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit> =
        printWithFirstConnected { printer ->
            val data = receiptBuilder.buildXReport(openTime, closeTime, payments)
            printer.print(data)
        }

    override suspend fun printZReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit> =
        printWithFirstConnected { printer ->
            val data = receiptBuilder.buildZReport(openTime, closeTime, payments)
            printer.print(data)
        }

    override suspend fun openCashDrawer(): Result<Unit> =
        printWithFirstConnected { printer ->
            printer.print(EscPosCommands.OPEN_DRAWER)
        }

    override suspend fun disconnectAll(): Result<Unit> = Result.catching {
        for ((id, printer) in printers) {
            runCatching { printer.disconnect() }
            logger.i(TAG, "Disconnected printer: $id")
        }
    }

    private suspend fun printWithFirstConnected(
        action: suspend (EscPosPrinter) -> Result<Unit>
    ): Result<Unit> {
        val connected = printers.values.filter { it.isConnected }
        if (connected.isEmpty()) {
            return Result.failure(AppError.Hardware("Printer", "No connected receipt printer"))
        }
        _status.value = PrinterStatus.PRINTING
        return try {
            var lastResult: Result<Unit> = Result.failure(AppError.Hardware("Printer", "All printers failed"))
            for (printer in connected) {
                lastResult = action(printer)
                if (lastResult is Result.Success) {
                    logger.i(TAG, "Print succeeded on ${printer.info.name}")
                    return lastResult
                }
                logger.w(TAG, "Print failed on ${printer.info.name}")
            }
            lastResult
        } finally {
            _status.value = PrinterStatus.IDLE
        }
    }

    companion object {
        private const val TAG = "EscPosPrinterManager"
    }
}

/**
 * Simulated printer manager for development and testing without physical hardware.
 * Logs all operations but does not send data to any device.
 */
class SimulatedReceiptPrinterManager(
    private val logger: com.enterprise.pos.core.Logger
) : ReceiptPrinterManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(PrinterStatus.IDLE)
    override val status: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val connectedPrinters: List<PrinterInfo>
        get() = listOf(
            PrinterInfo(
                id = "sim-printer",
                name = "Simulated Thermal Printer",
                model = "generic-escpos",
                connectionType = PrinterConnectionType.NETWORK,
                address = "127.0.0.1",
                isConnected = true
            )
        )

    override suspend fun discoverPrinters(): Result<List<PrinterInfo>> = Result.success(connectedPrinters)

    override suspend fun connectPrinter(printer: PrinterInfo): Result<Unit> = Result.success(Unit)
    override suspend fun disconnectPrinter(printerId: String): Result<Unit> = Result.success(Unit)

    override suspend fun printReceipt(order: Order, payments: List<Payment>): Result<Unit> =
        logAndSucceed("Print receipt for order ${order.id.value}")

    override suspend fun printKitchenChit(order: Order): Result<Unit> =
        logAndSucceed("Print kitchen chit for order ${order.id.value}")

    override suspend fun printReport(title: String, lines: List<String>): Result<Unit> =
        logAndSucceed("Print report: $title (${lines.size} lines)")

    override suspend fun printXReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit> =
        logAndSucceed("Print X-report (${payments.size} transactions)")

    override suspend fun printZReport(openTime: Long, closeTime: Long, payments: List<Payment>): Result<Unit> =
        logAndSucceed("Print Z-report (${payments.size} transactions)")

    override suspend fun openCashDrawer(): Result<Unit> =
        logAndSucceed("Open cash drawer")

    override suspend fun disconnectAll(): Result<Unit> = Result.success(Unit)

    private fun logAndSucceed(message: String): Result<Unit> {
        logger.i(TAG, "[SIMULATED] $message")
        return Result.success(Unit)
    }

    companion object {
        private const val TAG = "SimulatedReceiptPrinterManager"
    }
}
