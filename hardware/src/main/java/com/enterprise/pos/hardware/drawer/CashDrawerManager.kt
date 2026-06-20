package com.enterprise.pos.hardware.drawer

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract interface for cash drawer controllers supporting USB relay, serial,
 * and printer-triggered (ESC/POS kick) mechanisms.
 *
 * Some drawers support a hardware switch that reports open/closed state.
 */
interface CashDrawerManager {
    val status: StateFlow<DrawerStatus>
    suspend fun open(): Result<Unit>
    suspend fun isOpen(): Boolean
    suspend fun close(): Result<Unit>
}

enum class DrawerStatus {
    CLOSED,
    OPEN,
    UNKNOWN,
    ERROR
}

/** Drawer opened by sending the ESC/POS kick command through a connected printer. */
class PrinterTriggerCashDrawerManager(
    private val printer: com.enterprise.pos.hardware.printer.ReceiptPrinterManager
) : CashDrawerManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DrawerStatus.CLOSED)
    override val status: StateFlow<DrawerStatus> = _status.asStateFlow()

    override suspend fun open(): Result<Unit> = Result.catching {
        val result = printer.openCashDrawer()
        if (result is Result.Success) {
            _status.value = DrawerStatus.OPEN
        } else if (result is Result.Failure) {
            _status.value = DrawerStatus.ERROR
            throw result.error.toException()
        }
    }

    override suspend fun isOpen(): Boolean = false

    override suspend fun close(): Result<Unit> = Result.catching {
        _status.value = DrawerStatus.CLOSED
    }
}

/** USB relay or serial cash drawer controller. */
class UsbCashDrawerManager(
    private val deviceId: String,
    private val logger: com.enterprise.pos.core.Logger
) : CashDrawerManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DrawerStatus.UNKNOWN)
    override val status: StateFlow<DrawerStatus> = _status.asStateFlow()

    override suspend fun open(): Result<Unit> = Result.catching {
        if (deviceId.isBlank()) {
            throw IllegalArgumentException("USB device id is required")
        }
        logger.i(TAG, "Opening USB cash drawer: $deviceId")
        // Real implementation would use UsbManager to send open command
        _status.value = DrawerStatus.OPEN
    }

    override suspend fun isOpen(): Boolean {
        // Real implementation would read GPIO or USB status endpoint
        return _status.value == DrawerStatus.OPEN
    }

    override suspend fun close(): Result<Unit> = Result.catching {
        _status.value = DrawerStatus.CLOSED
    }

    companion object {
        private const val TAG = "UsbCashDrawerManager"
    }
}

/** Simulated cash drawer for development without hardware. */
class SimulatedCashDrawerManager(
    private val logger: com.enterprise.pos.core.Logger
) : CashDrawerManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DrawerStatus.CLOSED)
    override val status: StateFlow<DrawerStatus> = _status.asStateFlow()

    override suspend fun open(): Result<Unit> = Result.catching {
        logger.i(TAG, "[SIMULATED] Cash drawer opened")
        _status.value = DrawerStatus.OPEN
    }

    override suspend fun isOpen(): Boolean = _status.value == DrawerStatus.OPEN

    override suspend fun close(): Result<Unit> = Result.catching {
        logger.i(TAG, "[SIMULATED] Cash drawer closed")
        _status.value = DrawerStatus.CLOSED
    }

    companion object {
        private const val TAG = "SimulatedCashDrawerManager"
    }
}
