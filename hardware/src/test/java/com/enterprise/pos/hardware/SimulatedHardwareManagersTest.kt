package com.enterprise.pos.hardware

import com.enterprise.pos.core.NoopLogger
import com.enterprise.pos.hardware.drawer.SimulatedCashDrawerManager
import com.enterprise.pos.hardware.display.SimulatedCustomerDisplayManager
import com.enterprise.pos.hardware.printer.SimulatedReceiptPrinterManager
import com.enterprise.pos.hardware.scanner.SimulatedBarcodeScannerManager
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class SimulatedHardwareManagersTest {

    @Test
    fun `SimulatedReceiptPrinterManager discovers simulated printer`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val result = manager.discoverPrinters()
        assertThat(result.isSuccess()).isTrue()
        val printers = result.getOrThrow()
        assertThat(printers).hasSize(1)
        assertThat(printers[0].name).isEqualTo("Simulated Thermal Printer")
        assertThat(printers[0].isConnected).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager connect succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val printer = manager.discoverPrinters().getOrThrow()[0]
        val result = manager.connectPrinter(printer)
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager print receipt succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val order = com.enterprise.pos.domain.model.Order(
            id = com.enterprise.pos.core.OrderId("order-1"),
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            status = com.enterprise.pos.domain.model.OrderStatus.OPEN,
            createdAt = 0L
        )
        val result = manager.printReceipt(order, emptyList())
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager print kitchen chit succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val order = com.enterprise.pos.domain.model.Order(
            id = com.enterprise.pos.core.OrderId("order-1"),
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            status = com.enterprise.pos.domain.model.OrderStatus.OPEN,
            createdAt = 0L
        )
        val result = manager.printKitchenChit(order)
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager print report succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val result = manager.printReport("X-Report", listOf("Line 1", "Line 2"))
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager open cash drawer succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val result = manager.openCashDrawer()
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedReceiptPrinterManager disconnect all succeeds`() = runBlocking {
        val manager = SimulatedReceiptPrinterManager(NoopLogger)
        val result = manager.disconnectAll()
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedCashDrawerManager opens and reports status`() = runBlocking {
        val manager = SimulatedCashDrawerManager(NoopLogger)
        assertThat(manager.isOpen()).isFalse()
        val result = manager.open()
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.isOpen()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.drawer.DrawerStatus.OPEN)
    }

    @Test
    fun `SimulatedCashDrawerManager closes`() = runBlocking {
        val manager = SimulatedCashDrawerManager(NoopLogger)
        manager.open()
        val result = manager.close()
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.isOpen()).isFalse()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.drawer.DrawerStatus.CLOSED)
    }

    @Test
    fun `SimulatedBarcodeScannerManager emits scans`() = runBlocking {
        val manager = SimulatedBarcodeScannerManager()
        val result = manager.initialize()
        assertThat(result.isSuccess()).isTrue()

        val scan = async { manager.scans.first() }
        manager.emitScan("1234567890123", com.enterprise.pos.hardware.scanner.BarcodeFormat.EAN_13)
        val emitted = withTimeout(1_000) { scan.await() }

        assertThat(emitted.value).isEqualTo("1234567890123")
        assertThat(emitted.format).isEqualTo(com.enterprise.pos.hardware.scanner.BarcodeFormat.EAN_13)
    }

    @Test
    fun `SimulatedBarcodeScannerManager supports all formats`() = runBlocking {
        val manager = SimulatedBarcodeScannerManager()
        com.enterprise.pos.hardware.scanner.BarcodeFormat.entries.forEach { format ->
            assertThat(manager.isSupported(format)).isTrue()
        }
    }

    @Test
    fun `SimulatedBarcodeScannerManager close succeeds`() = runBlocking {
        val manager = SimulatedBarcodeScannerManager()
        val result = manager.close()
        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `SimulatedCustomerDisplayManager shows welcome`() = runBlocking {
        val manager = SimulatedCustomerDisplayManager(NoopLogger)
        val result = manager.showWelcome()
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.display.DisplayStatus.SHOWING_TOTAL)
    }

    @Test
    fun `SimulatedCustomerDisplayManager shows item`() = runBlocking {
        val manager = SimulatedCustomerDisplayManager(NoopLogger)
        val result = manager.showItem("Burger", "$12.50")
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.display.DisplayStatus.SHOWING_ITEM)
    }

    @Test
    fun `SimulatedCustomerDisplayManager shows running total`() = runBlocking {
        val manager = SimulatedCustomerDisplayManager(NoopLogger)
        val result = manager.showRunningTotal("$10.00", "$0.82", "$10.82")
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.display.DisplayStatus.SHOWING_TOTAL)
    }

    @Test
    fun `SimulatedCustomerDisplayManager shows thank you`() = runBlocking {
        val manager = SimulatedCustomerDisplayManager(NoopLogger)
        val result = manager.showThankYou()
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.display.DisplayStatus.SHOWING_THANK_YOU)
    }

    @Test
    fun `SimulatedCustomerDisplayManager clears`() = runBlocking {
        val manager = SimulatedCustomerDisplayManager(NoopLogger)
        manager.showWelcome()
        val result = manager.clear()
        assertThat(result.isSuccess()).isTrue()
        assertThat(manager.status.value).isEqualTo(com.enterprise.pos.hardware.display.DisplayStatus.IDLE)
    }
}
