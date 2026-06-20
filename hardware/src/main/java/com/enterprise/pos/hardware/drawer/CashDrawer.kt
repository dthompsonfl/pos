package com.enterprise.pos.hardware.drawer

import android.content.Context
import android.hardware.display.DisplayManager
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.enterprise.pos.hardware.printer.PrinterDriver

interface CashDrawerDriver {
    suspend fun open(): Result<Unit>
    val displayName: String
}

@Suppress("unused")
class PrinterKickCashDrawerDriver(
    private val printer: PrinterDriver
) : CashDrawerDriver {
    override val displayName: String = "Via ${printer.displayName}"

    override suspend fun open(): Result<Unit> {
        if (!printer.isConnected) {
            return Result.failure(AppError.Hardware("CashDrawer", "Printer is not connected"))
        }
        return printer.print(byteArrayOf(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte()))
    }
}

@Suppress("unused")
class UsbRelayCashDrawerDriver(
    deviceId: String,
    override val displayName: String = "USB Relay Drawer ($deviceId)"
) : CashDrawerDriver {
    override suspend fun open(): Result<Unit> =
        Result.failure(AppError.Hardware("CashDrawer", "USB relay drawer transport is not implemented yet"))
}

class CashDrawerManager(
    private val drivers: MutableList<CashDrawerDriver> = mutableListOf()
) {
    @Suppress("unused")
    fun register(driver: CashDrawerDriver) { drivers.add(driver) }

    @Suppress("unused")
    suspend fun open(): Result<Unit> {
        if (drivers.isEmpty()) {
            return Result.failure(AppError.Hardware("CashDrawer", "No drawer configured"))
        }
        var lastError: AppError? = null
        for (driver in drivers) {
            val result = driver.open()
            if (result is Result.Success) return result
            if (result is Result.Failure) lastError = result.error
        }
        return Result.failure(lastError ?: AppError.Hardware("CashDrawer", "All drawer drivers failed"))
    }
}

interface SecondaryDisplayController {
    suspend fun showCart(order: com.enterprise.pos.domain.model.Order): Result<Unit>
    suspend fun showWelcome(): Result<Unit>
    suspend fun showThankYou(): Result<Unit>
    suspend fun clear(): Result<Unit>
}

class NoopSecondaryDisplay : SecondaryDisplayController {
    override suspend fun showCart(order: com.enterprise.pos.domain.model.Order) = unavailable()
    override suspend fun showWelcome() = unavailable()
    override suspend fun showThankYou() = unavailable()
    override suspend fun clear() = unavailable()

    private fun unavailable(): Result<Unit> =
        Result.failure(AppError.Hardware("CustomerDisplay", "No customer display configured"))
}

@Suppress("unused")
class PresentationSecondaryDisplay(
    context: Context
) : SecondaryDisplayController {
    private val appContext = context.applicationContext

    private fun hasSecondaryDisplay(): Boolean {
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return (displayManager?.displays?.size ?: 0) > 1
    }

    override suspend fun showCart(order: com.enterprise.pos.domain.model.Order): Result<Unit> = ensureDisplayAvailable()
    override suspend fun showWelcome(): Result<Unit> = ensureDisplayAvailable()
    override suspend fun showThankYou(): Result<Unit> = ensureDisplayAvailable()
    override suspend fun clear(): Result<Unit> = ensureDisplayAvailable()

    private fun ensureDisplayAvailable(): Result<Unit> =
        if (hasSecondaryDisplay()) {
            Result.success(Unit)
        } else {
            Result.failure(AppError.Hardware("CustomerDisplay", "No secondary display detected"))
        }
}
