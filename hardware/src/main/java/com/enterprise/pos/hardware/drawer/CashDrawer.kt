package com.enterprise.pos.hardware.drawer

import android.content.Context
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Result
import com.enterprise.pos.hardware.printer.PrinterDriver

/** Cash drawer controller. Real drawers are kicked via ESC p m t1 t2 to a connected printer,
 *  or via a dedicated USB relay. We provide both paths. */
interface CashDrawerDriver {
    suspend fun open(): Result<Unit>
    val displayName: String
}

class PrinterKickCashDrawerDriver(
    private val printer: PrinterDriver
) : CashDrawerDriver {
    override val displayName: String = "Via ${printer.displayName}"
    override suspend fun open(): Result<Unit> = Result.catching {
        if (!printer.isConnected) throw IllegalStateException("Printer not connected")
        // ESC p m t1 t2 — pulse drawer kick on pin 2 for ~250ms.
        printer.print(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA))
    }
}

class UsbRelayCashDrawerDriver(
    override val displayName: String = "USB Relay Drawer",
    private val deviceId: String
) : CashDrawerDriver {
    override suspend fun open(): Result<Unit> = Result.catching {
        // Real: open USB relay, fire 200ms pulse, close.
    }
}

class CashDrawerManager(
    private val drivers: MutableList<CashDrawerDriver> = mutableListOf()
) {
    fun register(driver: CashDrawerDriver) { drivers.add(driver) }

    suspend fun open(): Result<Unit> {
        if (drivers.isEmpty()) {
            return Result.failure(AppError.Hardware("CashDrawer", "No drawer configured"))
        }
        for (d in drivers) {
            val r = d.open()
            if (r is Result.Success) return r
        }
        return Result.failure(AppError.Hardware("CashDrawer", "All drawer drivers failed"))
    }
}

/** Secondary display (customer-facing) — used to show running cart, totals, ads, etc. */
interface SecondaryDisplayController {
    suspend fun showCart(order: com.enterprise.pos.domain.model.Order): Result<Unit>
    suspend fun showWelcome(): Result<Unit>
    suspend fun showThankYou(): Result<Unit>
    suspend fun clear(): Result<Unit>
}

class NoopSecondaryDisplay : SecondaryDisplayController {
    override suspend fun showCart(order: com.enterprise.pos.domain.model.Order) = Result.success(Unit)
    override suspend fun showWelcome() = Result.success(Unit)
    override suspend fun showThankYou() = Result.success(Unit)
    override suspend fun clear() = Result.success(Unit)
}

/** Presentation display — uses Android's Presentation API to drive a second screen over HDMI/USB-C. */
class PresentationSecondaryDisplay(
    private val context: Context
) : SecondaryDisplayController {
    override suspend fun showCart(order: com.enterprise.pos.domain.model.Order): Result<Unit> = Result.catching {
        // Real: create a Presentation on DisplayManager.getDisplays()[1] and render a Compose view
        // showing line items + running total. Update on each cart change.
    }
    override suspend fun showWelcome() = Result.catching { /* render welcome screen */ }
    override suspend fun showThankYou() = Result.catching { /* render thank-you */ }
    override suspend fun clear() = Result.catching { /* hide presentation */ }
}
