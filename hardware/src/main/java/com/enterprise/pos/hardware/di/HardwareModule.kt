package com.enterprise.pos.hardware.di

import com.enterprise.pos.core.Logger
import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.hardware.drawer.CashDrawerManager
import com.enterprise.pos.hardware.drawer.NoopSecondaryDisplay
import com.enterprise.pos.hardware.drawer.SecondaryDisplayController
import com.enterprise.pos.hardware.printer.PrinterManager
import com.enterprise.pos.hardware.printer.ReceiptRenderer
import com.enterprise.pos.hardware.scanner.BarcodeScanner
import com.enterprise.pos.hardware.scanner.CameraScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HardwareModule {

    @Provides @Singleton
    fun provideReceiptRenderer(): ReceiptRenderer = ReceiptRenderer()

    @Provides @Singleton
    fun providePrinterManager(): PrinterManager = PrinterManager()

    @Provides @Singleton
    fun provideCashDrawerManager(): CashDrawerManager = CashDrawerManager()

    @Provides @Singleton
    fun provideSecondaryDisplay(): SecondaryDisplayController = NoopSecondaryDisplay()

    @Provides @Singleton
    fun provideBarcodeScanner(): BarcodeScanner = CameraScanner()
}
