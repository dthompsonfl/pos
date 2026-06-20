package com.enterprise.pos.hardware.escpos

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.Charset

class EscPosCommandsTest {

    @Test
    fun `INIT command is correct bytes`() {
        assertThat(EscPosCommands.INIT).asList().containsExactly(0x1B.toByte(), 0x40.toByte()).inOrder()
    }

    @Test
    fun `ALIGN_CENTER command is correct bytes`() {
        assertThat(EscPosCommands.ALIGN_CENTER).asList().containsExactly(0x1B.toByte(), 0x61.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `BOLD_ON command is correct bytes`() {
        assertThat(EscPosCommands.BOLD_ON).asList().containsExactly(0x1B.toByte(), 0x45.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `CUT_FULL command is correct bytes`() {
        assertThat(EscPosCommands.CUT_FULL).asList().containsExactly(0x1D.toByte(), 0x56.toByte(), 0x01.toByte()).inOrder()
    }

    @Test
    fun `OPEN_DRAWER command is correct bytes`() {
        assertThat(EscPosCommands.OPEN_DRAWER).asList().containsExactly(0x1B.toByte(), 0x70.toByte(), 0x00.toByte(), 0x19.toByte(), 0xFA.toByte()).inOrder()
    }

    @Test
    fun `textLine appends line feed`() {
        val result = EscPosCommands.textLine("Hello")
        assertThat(result.last()).isEqualTo(0x0A.toByte())
        assertThat(result.size).isEqualTo(6) // 5 chars + LF
    }

    @Test
    fun `centeredLine includes alignment and padding`() {
        val result = EscPosCommands.centeredLine("Hi", width = 10)
        // Should start with ALIGN_CENTER and end with ALIGN_LEFT after content
        assertThat(result[0]).isEqualTo(0x1B.toByte())
        assertThat(result[1]).isEqualTo(0x61.toByte())
        assertThat(result[2]).isEqualTo(0x01.toByte())
    }

    @Test
    fun `rightAlignedLine formats correctly`() {
        val result = EscPosCommands.rightAlignedLine("Left", "Right", width = 20)
        val text = result.dropLast(1).toByteArray().toString(Charset.defaultCharset())
        assertThat(text).startsWith("Left")
        assertThat(text).endsWith("Right")
    }

    @Test
    fun `separator produces repeated character`() {
        val result = EscPosCommands.separator(width = 10, char = '-')
        val text = result.dropLast(1).toByteArray().toString(Charset.defaultCharset())
        assertThat(text).isEqualTo("----------")
    }

    @Test
    fun `feedLines produces correct command`() {
        val result = EscPosCommands.feedLines(3)
        assertThat(result).asList().containsExactly(0x1B.toByte(), 0x64.toByte(), 0x03.toByte()).inOrder()
    }

    @Test
    fun `feedPaper produces correct command`() {
        val result = EscPosCommands.feedPaper(5)
        assertThat(result).asList().containsExactly(0x1B.toByte(), 0x4A.toByte(), 0x05.toByte()).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `feedLines rejects negative`() {
        EscPosCommands.feedLines(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `feedLines rejects over 255`() {
        EscPosCommands.feedLines(256)
    }

    @Test
    fun `barcodeEan13 requires 13 digits`() {
        val result = EscPosCommands.barcodeEan13("1234567890123")
        assertThat(result.size).isGreaterThan(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `barcodeEan13 rejects wrong length`() {
        EscPosCommands.barcodeEan13("123")
    }

    @Test
    fun `barcodeCode128 accepts valid ASCII`() {
        val result = EscPosCommands.barcodeCode128("ABC-123")
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `qrCode produces valid command`() {
        val result = EscPosCommands.qrCode("https://example.com", size = 3)
        assertThat(result.size).isGreaterThan(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `qrCode rejects invalid size`() {
        EscPosCommands.qrCode("test", size = 0)
    }

    @Test
    fun `DOUBLE_WIDTH_HEIGHT command bytes`() {
        assertThat(EscPosCommands.DOUBLE_WIDTH_HEIGHT).asList().containsExactly(0x1D.toByte(), 0x21.toByte(), 0x11.toByte()).inOrder()
    }

    @Test
    fun `TRIPLE_WIDTH_HEIGHT command bytes`() {
        assertThat(EscPosCommands.TRIPLE_WIDTH_HEIGHT).asList().containsExactly(0x1D.toByte(), 0x21.toByte(), 0x22.toByte()).inOrder()
    }

    @Test
    fun `NORMAL_SIZE command bytes`() {
        assertThat(EscPosCommands.NORMAL_SIZE).asList().containsExactly(0x1D.toByte(), 0x21.toByte(), 0x00.toByte()).inOrder()
    }
}

class EscPosReceiptBuilderTest {

    private val builder = EscPosReceiptBuilder(
        storeName = "Test Cafe",
        storeAddress = "123 Main St",
        storePhone = "(555) 010-2025",
        footer = "Thank you!"
    )

    private fun createOrder(): com.enterprise.pos.domain.model.Order {
        return com.enterprise.pos.domain.model.Order(
            id = com.enterprise.pos.core.OrderId("order-abc-123"),
            storeId = com.enterprise.pos.core.StoreId("store-1"),
            registerId = com.enterprise.pos.core.RegisterId("reg-1"),
            employeeId = com.enterprise.pos.core.EmployeeId("emp-1"),
            diningMode = com.enterprise.pos.domain.model.DiningMode.DINE_IN_HOST_SEATED,
            tableName = "Table 5",
            guestCount = 2,
            status = com.enterprise.pos.domain.model.OrderStatus.PAID,
            lines = listOf(
                com.enterprise.pos.domain.model.OrderLine(
                    id = com.enterprise.pos.core.OrderLineId("line-1"),
                    lineType = com.enterprise.pos.domain.model.OrderLineType.ITEM,
                    productId = com.enterprise.pos.core.ProductId("prod-1"),
                    name = "Burger",
                    quantity = com.enterprise.pos.core.Quantity.of(1),
                    unitPrice = com.enterprise.pos.core.Money.of(12.50)
                )
            ),
            taxLines = listOf(
                com.enterprise.pos.domain.model.TaxLine(
                    name = "Sales Tax",
                    rate = com.enterprise.pos.core.Percent.of(8.25),
                    amount = com.enterprise.pos.core.Money.of(1.03)
                )
            ),
            tip = com.enterprise.pos.core.Money.of(2.50),
            createdAt = 1700000000000L
        )
    }

    @Test
    fun `buildReceipt produces non-empty byte array`() {
        val order = createOrder()
        val payments = listOf(
            com.enterprise.pos.domain.model.Payment(
                id = com.enterprise.pos.core.PaymentId("pay-1"),
                orderId = order.id,
                provider = "CASH",
                providerTransactionId = "txn-1",
                amount = com.enterprise.pos.core.Money.of(16.03),
                capturedAt = 1700000000000L
            )
        )
        val result = builder.buildReceipt(order, payments)
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildReceipt contains store name`() {
        val order = createOrder()
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Test Cafe")
    }

    @Test
    fun `buildReceipt contains order id suffix`() {
        val order = createOrder()
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("ORDER")
    }

    @Test
    fun `buildReceipt contains item name`() {
        val order = createOrder()
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Burger")
    }

    @Test
    fun `buildReceipt contains total`() {
        val order = createOrder()
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("TOTAL")
    }

    @Test
    fun `buildKitchenChit produces non-empty bytes`() {
        val order = createOrder()
        val result = builder.buildKitchenChit(order)
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildKitchenChit contains order id`() {
        val order = createOrder()
        val result = builder.buildKitchenChit(order)
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("ORDER")
    }

    @Test
    fun `buildKitchenChit does not contain prices`() {
        val order = createOrder()
        val result = builder.buildKitchenChit(order)
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).doesNotContain("$12.50")
    }

    @Test
    fun `buildReport produces non-empty bytes`() {
        val result = builder.buildReport("TEST REPORT", listOf("Line 1", "Line 2"))
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildXReport produces report`() {
        val payments = listOf(
            com.enterprise.pos.domain.model.Payment(
                id = com.enterprise.pos.core.PaymentId("pay-1"),
                orderId = com.enterprise.pos.core.OrderId("order-1"),
                provider = "CASH",
                providerTransactionId = "txn-1",
                amount = com.enterprise.pos.core.Money.of(100.00),
                capturedAt = 1700000000000L
            )
        )
        val result = builder.buildXReport(1700000000000L, 1700003600000L, payments)
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildZReport produces report with shift closed`() {
        val payments = listOf(
            com.enterprise.pos.domain.model.Payment(
                id = com.enterprise.pos.core.PaymentId("pay-1"),
                orderId = com.enterprise.pos.core.OrderId("order-1"),
                provider = "CASH",
                providerTransactionId = "txn-1",
                amount = com.enterprise.pos.core.Money.of(100.00),
                capturedAt = 1700000000000L
            )
        )
        val result = builder.buildZReport(1700000000000L, 1700003600000L, payments)
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("SHIFT CLOSED")
    }

    @Test
    fun `buildReceipt handles empty payments gracefully`() {
        val order = createOrder()
        val result = builder.buildReceipt(order, emptyList())
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildReceipt handles notes on items`() {
        val order = createOrder().copy(
            lines = listOf(
                com.enterprise.pos.domain.model.OrderLine(
                    id = com.enterprise.pos.core.OrderLineId("line-1"),
                    lineType = com.enterprise.pos.domain.model.OrderLineType.ITEM,
                    productId = com.enterprise.pos.core.ProductId("prod-1"),
                    name = "Burger",
                    quantity = com.enterprise.pos.core.Quantity.of(1),
                    unitPrice = com.enterprise.pos.core.Money.of(12.50),
                    notes = "No onions"
                )
            )
        )
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("No onions")
    }

    @Test
    fun `buildReceipt handles modifiers`() {
        val order = createOrder().copy(
            lines = listOf(
                com.enterprise.pos.domain.model.OrderLine(
                    id = com.enterprise.pos.core.OrderLineId("line-1"),
                    lineType = com.enterprise.pos.domain.model.OrderLineType.ITEM,
                    productId = com.enterprise.pos.core.ProductId("prod-1"),
                    name = "Burger",
                    quantity = com.enterprise.pos.core.Quantity.of(1),
                    unitPrice = com.enterprise.pos.core.Money.of(12.50),
                    modifiers = listOf(
                        com.enterprise.pos.domain.model.OrderLine(
                            id = com.enterprise.pos.core.OrderLineId("mod-1"),
                            lineType = com.enterprise.pos.domain.model.OrderLineType.MODIFIER,
                            name = "Extra Cheese",
                            quantity = com.enterprise.pos.core.Quantity.of(1),
                            unitPrice = com.enterprise.pos.core.Money.of(1.50)
                        )
                    )
                )
            )
        )
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Extra Cheese")
    }

    @Test
    fun `buildReceipt handles zero guest count`() {
        val order = createOrder().copy(guestCount = 0)
        val result = builder.buildReceipt(order, emptyList())
        assertThat(result.size).isGreaterThan(0)
    }

    @Test
    fun `buildReceipt handles discount`() {
        val order = createOrder().copy(
            orderLevelDiscount = com.enterprise.pos.core.Money.of(2.00)
        )
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Discount")
    }

    @Test
    fun `buildReceipt handles service charges`() {
        val order = createOrder().copy(
            serviceCharges = com.enterprise.pos.core.Money.of(1.50)
        )
        val result = builder.buildReceipt(order, emptyList())
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Service")
    }

    @Test
    fun `buildReceipt handles card payment with last4`() {
        val order = createOrder()
        val payments = listOf(
            com.enterprise.pos.domain.model.Payment(
                id = com.enterprise.pos.core.PaymentId("pay-1"),
                orderId = order.id,
                provider = "STRIPE",
                providerTransactionId = "txn-1",
                amount = com.enterprise.pos.core.Money.of(16.03),
                cardBrand = "Visa",
                last4 = "4242",
                capturedAt = 1700000000000L
            )
        )
        val result = builder.buildReceipt(order, payments)
        val text = result.toString(Charsets.UTF_8)
        assertThat(text).contains("Visa")
    }
}
