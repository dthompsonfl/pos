@file:Suppress("unused")

package com.enterprise.pos.hardware.di

import android.content.Context
import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.hardware.display.CustomerDisplayManager
import com.enterprise.pos.hardware.display.SimulatedCustomerDisplayManager
import com.enterprise.pos.hardware.display.UsbPoleDisplayManager
import com.enterprise.pos.hardware.drawer.CashDrawerManager
import com.enterprise.pos.hardware.drawer.PrinterTriggerCashDrawerManager
import com.enterprise.pos.hardware.drawer.SimulatedCashDrawerManager
import com.enterprise.pos.hardware.escpos.EscPosReceiptBuilder
import com.enterprise.pos.hardware.escpos.SimulatedEscPosPrinter
import com.enterprise.pos.hardware.kds.InMemoryKitchenDisplayManager
import com.enterprise.pos.hardware.kds.KitchenDisplayManager
import com.enterprise.pos.hardware.kds.SimulatedKitchenDisplayManager
import com.enterprise.pos.hardware.printer.EscPosReceiptPrinterManager
import com.enterprise.pos.hardware.printer.ReceiptPrinterManager
import com.enterprise.pos.hardware.printer.SimulatedReceiptPrinterManager
import com.enterprise.pos.hardware.scanner.BarcodeScannerManager
import com.enterprise.pos.hardware.scanner.CameraBarcodeScannerManager
import com.enterprise.pos.hardware.scanner.SimulatedBarcodeScannerManager
import com.enterprise.pos.hardware.scanner.UsbHidBarcodeScannerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing all hardware peripherals for the POS system.
 *
 * By default, simulated implementations are bound so the app can run in
 * development without physical hardware. In production builds, override this
 * module to provide real USB, Bluetooth, and network drivers.
 */
@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    @Provides
    @Singleton
    fun provideEscPosReceiptBuilder(): EscPosReceiptBuilder = EscPosReceiptBuilder()

    @Provides
    @Singleton
    fun provideReceiptPrinterManager(
        receiptBuilder: EscPosReceiptBuilder,
        logger: Logger
    ): ReceiptPrinterManager {
        val manager = EscPosReceiptPrinterManager(receiptBuilder, logger)
        // Register a simulated printer so basic receipt operations work out of the box.
        manager.registerPrinter(SimulatedEscPosPrinter(logger))
        return manager
    }

    @Provides
    @Singleton
    fun provideBarcodeScannerManager(): BarcodeScannerManager {
        // Default to simulated scanner for broad compatibility.
        return SimulatedBarcodeScannerManager()
    }

    @Provides
    @Singleton
    fun provideUsbHidBarcodeScannerManager(): UsbHidBarcodeScannerManager = UsbHidBarcodeScannerManager()

    @Provides
    @Singleton
    fun provideCameraBarcodeScannerManager(): CameraBarcodeScannerManager = CameraBarcodeScannerManager()

    @Provides
    @Singleton
    fun provideCashDrawerManager(
        printerManager: ReceiptPrinterManager,
        logger: Logger
    ): CashDrawerManager {
        // Default to printer-triggered drawer for common POS setups.
        return PrinterTriggerCashDrawerManager(printerManager)
    }

    @Provides
    @Singleton
    fun provideCustomerDisplayManager(
        logger: Logger
    ): CustomerDisplayManager {
        // Default to simulated display; override with UsbPoleDisplayManager for real hardware.
        return SimulatedCustomerDisplayManager(logger)
    }

    @Provides
    @Singleton
    fun provideKitchenDisplayManager(
        @ApplicationContext context: Context,
        logger: Logger
    ): KitchenDisplayManager {
        return InMemoryKitchenDisplayManager(context, logger)
    }
}
