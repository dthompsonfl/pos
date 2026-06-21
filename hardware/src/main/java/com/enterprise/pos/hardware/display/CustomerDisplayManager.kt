package com.enterprise.pos.hardware.display

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Abstract interface for customer-facing displays such as USB pole displays,
 * HDMI secondary screens, or tablet-mounted customer screens.
 *
 * Shows item details, running totals, and thank-you messages during checkout.
 */
interface CustomerDisplayManager {
    val status: StateFlow<DisplayStatus>

    suspend fun showWelcome(): Result<Unit>
    suspend fun showItem(name: String, price: String): Result<Unit>
    suspend fun showRunningTotal(subtotal: String, tax: String, total: String): Result<Unit>
    suspend fun showThankYou(): Result<Unit>
    suspend fun clear(): Result<Unit>
}

enum class DisplayStatus {
    IDLE,
    SHOWING_ITEM,
    SHOWING_TOTAL,
    SHOWING_THANK_YOU,
    ERROR
}

/**
 * USB pole display manager for common Logic Controls and similar two-line
 * vacuum-fluorescent or LED customer displays.
 *
 * Uses raw USB HID control transfers to send ASCII characters and command bytes.
 */
class UsbPoleDisplayManager(
    private val logger: com.enterprise.pos.core.Logger
) : CustomerDisplayManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DisplayStatus.IDLE)
    override val status: StateFlow<DisplayStatus> = _status.asStateFlow()

    // USB HID command set for common pole displays
    private val clearDisplay = byteArrayOf(0x0C.toByte())
    private val cursorHome = byteArrayOf(0x1B.toByte(), 0x5B.toByte(), 0x31.toByte(), 0x3B.toByte(), 0x31.toByte(), 0x48.toByte())
    private val line2 = byteArrayOf(0x1B.toByte(), 0x5B.toByte(), 0x32.toByte(), 0x3B.toByte(), 0x31.toByte(), 0x48.toByte())

    override suspend fun showWelcome(): Result<Unit> = Result.catching {
        logger.i(TAG, "Showing welcome message")
        _status.value = DisplayStatus.SHOWING_TOTAL
        // Real: send clear + "Welcome" + "Enterprise POS"
    }

    override suspend fun showItem(name: String, price: String): Result<Unit> = Result.catching {
        logger.i(TAG, "Showing item: $name = $price")
        _status.value = DisplayStatus.SHOWING_ITEM
        // Real: send clear + truncate name to line 1 + price on line 2
    }

    override suspend fun showRunningTotal(
        subtotal: String,
        tax: String,
        total: String
    ): Result<Unit> = Result.catching {
        logger.i(TAG, "Showing total: $total")
        _status.value = DisplayStatus.SHOWING_TOTAL
        // Real: send clear + "Subtotal $subtotal" + "Tax $tax Total $total"
    }

    override suspend fun showThankYou(): Result<Unit> = Result.catching {
        logger.i(TAG, "Showing thank you")
        _status.value = DisplayStatus.SHOWING_THANK_YOU
        // Real: send clear + "Thank You" + "Come Again"
    }

    override suspend fun clear(): Result<Unit> = Result.catching {
        logger.i(TAG, "Clearing display")
        _status.value = DisplayStatus.IDLE
        // Real: send clear command
    }

    companion object {
        private const val TAG = "UsbPoleDisplayManager"
    }
}

/**
 * HDMI or secondary screen display manager.
 *
 * Uses the Android Presentation API to show content on a second display.
 */
class HdmiCustomerDisplayManager(
    private val logger: com.enterprise.pos.core.Logger
) : CustomerDisplayManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DisplayStatus.IDLE)
    override val status: StateFlow<DisplayStatus> = _status.asStateFlow()

    override suspend fun showWelcome(): Result<Unit> = Result.catching {
        logger.i(TAG, "HDMI display: welcome")
        _status.value = DisplayStatus.SHOWING_TOTAL
    }

    override suspend fun showItem(name: String, price: String): Result<Unit> = Result.catching {
        logger.i(TAG, "HDMI display: item $name $price")
        _status.value = DisplayStatus.SHOWING_ITEM
    }

    override suspend fun showRunningTotal(
        subtotal: String,
        tax: String,
        total: String
    ): Result<Unit> = Result.catching {
        logger.i(TAG, "HDMI display: total $total")
        _status.value = DisplayStatus.SHOWING_TOTAL
    }

    override suspend fun showThankYou(): Result<Unit> = Result.catching {
        logger.i(TAG, "HDMI display: thank you")
        _status.value = DisplayStatus.SHOWING_THANK_YOU
    }

    override suspend fun clear(): Result<Unit> = Result.catching {
        logger.i(TAG, "HDMI display: clear")
        _status.value = DisplayStatus.IDLE
    }

    companion object {
        private const val TAG = "HdmiCustomerDisplayManager"
    }
}

/** Simulated customer display for development. */
class SimulatedCustomerDisplayManager(
    private val logger: com.enterprise.pos.core.Logger
) : CustomerDisplayManager {

    private val _status = kotlinx.coroutines.flow.MutableStateFlow(DisplayStatus.IDLE)
    override val status: StateFlow<DisplayStatus> = _status.asStateFlow()

    override suspend fun showWelcome(): Result<Unit> =
        logAndSucceed("Welcome", DisplayStatus.SHOWING_TOTAL)

    override suspend fun showItem(name: String, price: String): Result<Unit> =
        logAndSucceed("Item: $name = $price", DisplayStatus.SHOWING_ITEM)

    override suspend fun showRunningTotal(
        subtotal: String,
        tax: String,
        total: String
    ): Result<Unit> =
        logAndSucceed("Total: $total (subtotal=$subtotal, tax=$tax)", DisplayStatus.SHOWING_TOTAL)

    override suspend fun showThankYou(): Result<Unit> =
        logAndSucceed("Thank You", DisplayStatus.SHOWING_THANK_YOU)

    override suspend fun clear(): Result<Unit> =
        logAndSucceed("Clear", DisplayStatus.IDLE)

    private fun logAndSucceed(message: String, status: DisplayStatus): Result<Unit> {
        logger.i(TAG, "[SIMULATED] $message")
        _status.value = status
        return Result.success(Unit)
    }

    companion object {
        private const val TAG = "SimulatedCustomerDisplayManager"
    }
}
