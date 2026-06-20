package com.enterprise.pos.hardware.escpos

import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.domain.model.Payment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds ESC/POS byte streams for receipts, kitchen chits, and shift reports.
 *
 * Supports common 42-column thermal printers (Epson TM-T88, Star TSP100, Bixolon SRP-350).
 * Uses [EscPosCommands] for raw byte generation.
 */
class EscPosReceiptBuilder(
    private val storeName: String = "Enterprise POS",
    private val storeAddress: String = "123 Main St",
    private val storePhone: String = "(555) 010-2025",
    private val footer: String = "Thank you for your business!"
) {

    companion object {
        const val LINE_WIDTH = 42
    }

    /** Returns ESC/POS bytes for a full customer receipt. */
    fun buildReceipt(order: Order, payments: List<Payment>): ByteArray {
        val out = mutableListOf<Byte>()
        fun append(bytes: ByteArray) { bytes.forEach { out.add(it) } }

        append(EscPosCommands.INIT)
        append(EscPosCommands.ALIGN_CENTER)
        append(EscPosCommands.BOLD_ON)
        append(EscPosCommands.textLine(storeName))
        append(EscPosCommands.BOLD_OFF)
        append(EscPosCommands.textLine(storeAddress))
        append(EscPosCommands.textLine(storePhone))
        append(EscPosCommands.ALIGN_LEFT)
        append(EscPosCommands.separator(LINE_WIDTH))

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(order.createdAt))
        append(EscPosCommands.textLine("Order: ${order.id.value.takeLast(8).uppercase()}"))
        append(EscPosCommands.textLine("Date: $date"))
        append(EscPosCommands.textLine("Server: ${order.employeeId.value.takeLast(4).uppercase()}"))
        order.tableName?.let { append(EscPosCommands.textLine("Table: $it")) }
        append(EscPosCommands.textLine("Mode: ${order.diningMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }}"))
        if (order.guestCount > 0) append(EscPosCommands.textLine("Guests: ${order.guestCount}"))
        append(EscPosCommands.separator(LINE_WIDTH))

        // Items
        for (line in order.lines.filter { it.lineType == OrderLineType.ITEM }) {
            val qty = line.quantity.asInt.toString().padStart(3)
            val name = line.name.take(28).padEnd(28)
            val total = line.lineTotal.format().padStart(11)
            append(EscPosCommands.textLine("$qty $name$total"))
            line.notes?.takeIf { it.isNotBlank() }?.let {
                append(EscPosCommands.textLine("    * $it"))
            }
            for (mod in line.modifiers) {
                val modQty = mod.quantity.asInt.toString().padStart(3)
                val modName = "  -> ${mod.name.take(26)}".padEnd(28)
                val modTotal = mod.lineTotal.format().padStart(11)
                append(EscPosCommands.textLine("$modQty $modName$modTotal"))
            }
        }

        append(EscPosCommands.separator(LINE_WIDTH))

        // Totals
        val subtotal = order.subtotal.format().padStart(LINE_WIDTH - "Subtotal".length)
        append(EscPosCommands.textLine("Subtotal$subtotal"))
        if (!order.totalDiscount.isZero()) {
            val disc = "-" + order.totalDiscount.format().padStart(LINE_WIDTH - "Discount".length - 1)
            append(EscPosCommands.textLine("Discount$disc"))
        }
        for (tax in order.taxLines) {
            val taxLine = tax.amount.format().padStart(LINE_WIDTH - tax.name.length)
            append(EscPosCommands.textLine("${tax.name}$taxLine"))
        }
        if (!order.tip.isZero()) {
            val tip = order.tip.format().padStart(LINE_WIDTH - "Tip".length)
            append(EscPosCommands.textLine("Tip$tip"))
        }
        if (!order.serviceCharges.isZero()) {
            val svc = order.serviceCharges.format().padStart(LINE_WIDTH - "Service".length)
            append(EscPosCommands.textLine("Service$svc"))
        }
        append(EscPosCommands.separator(LINE_WIDTH))

        val grand = order.grandTotal.format()
        val totalPad = " ".repeat((LINE_WIDTH - "TOTAL".length - grand.length).coerceAtLeast(0))
        append(EscPosCommands.BOLD_ON)
        append(EscPosCommands.textLine("TOTAL$totalPad$grand"))
        append(EscPosCommands.BOLD_OFF)
        append(EscPosCommands.separator(LINE_WIDTH))

        // Payments
        if (payments.isNotEmpty()) {
            append(EscPosCommands.textLine("Payment:"))
            for (p in payments) {
                val prov = p.provider.padEnd(15)
                val amt = p.amount.format().padStart(LINE_WIDTH - 15)
                append(EscPosCommands.textLine("  $prov$amt"))
                p.cardBrand?.let { append(EscPosCommands.textLine("    $it **** ${p.last4 ?: ""}")) }
            }
            append(EscPosCommands.separator(LINE_WIDTH))
        }

        // Footer
        append(EscPosCommands.ALIGN_CENTER)
        append(EscPosCommands.textLine(""))
        append(EscPosCommands.textLine(footer))
        append(EscPosCommands.textLine(""))
        append(EscPosCommands.ALIGN_LEFT)
        append(EscPosCommands.feedLines(3))
        append(EscPosCommands.CUT_FULL)

        return out.toByteArray()
    }

    /** Returns ESC/POS bytes for a kitchen ticket without prices. */
    fun buildKitchenChit(order: Order): ByteArray {
        val out = mutableListOf<Byte>()
        fun append(bytes: ByteArray) { bytes.forEach { out.add(it) } }

        append(EscPosCommands.INIT)
        append(EscPosCommands.DOUBLE_WIDTH_HEIGHT)
        append(EscPosCommands.textLine("ORDER ${order.id.value.takeLast(6).uppercase()}"))
        append(EscPosCommands.NORMAL_SIZE)
        append(EscPosCommands.textLine("Table: ${order.tableName ?: order.diningMode.name}"))
        append(EscPosCommands.textLine("Guests: ${order.guestCount}"))
        append(EscPosCommands.textLine(SimpleDateFormat("HH:mm", Locale.US).format(Date(order.createdAt))))
        order.notes?.takeIf { it.isNotBlank() }?.let { append(EscPosCommands.textLine("Notes: $it")) }
        append(EscPosCommands.separator(LINE_WIDTH))

        for (line in order.lines.filter { it.lineType == OrderLineType.ITEM }) {
            append(EscPosCommands.BOLD_ON)
            append(EscPosCommands.textLine("${line.quantity.asInt}x  ${line.name}"))
            append(EscPosCommands.BOLD_OFF)
            line.notes?.takeIf { it.isNotBlank() }?.let {
                append(EscPosCommands.textLine("    >> $it"))
            }
            for (mod in line.modifiers) {
                append(EscPosCommands.textLine("      + ${mod.name}"))
            }
        }

        append(EscPosCommands.separator(LINE_WIDTH))
        append(EscPosCommands.feedLines(3))
        append(EscPosCommands.CUT_FULL)

        return out.toByteArray()
    }

    /** Returns ESC/POS bytes for a generic text report. */
    fun buildReport(title: String, lines: List<String>): ByteArray {
        val out = mutableListOf<Byte>()
        fun append(bytes: ByteArray) { bytes.forEach { out.add(it) } }

        append(EscPosCommands.INIT)
        append(EscPosCommands.ALIGN_CENTER)
        append(EscPosCommands.BOLD_ON)
        append(EscPosCommands.textLine(title))
        append(EscPosCommands.BOLD_OFF)
        append(EscPosCommands.ALIGN_LEFT)
        append(EscPosCommands.separator(LINE_WIDTH))
        for (line in lines) {
            append(EscPosCommands.textLine(line))
        }
        append(EscPosCommands.separator(LINE_WIDTH))
        append(EscPosCommands.feedLines(3))
        append(EscPosCommands.CUT_FULL)

        return out.toByteArray()
    }

    /** Returns ESC/POS bytes for an X-report (current shift read-only). */
    fun buildXReport(openTime: Long, closeTime: Long, payments: List<Payment>): ByteArray {
        val lines = mutableListOf<String>()
        lines.add("X-REPORT (READ-ONLY)")
        lines.add("Opened: ${formatTime(openTime)}")
        lines.add("Closed: ${formatTime(closeTime)}")
        lines.add("")
        lines.add("Payment Summary:")

        val grouped = payments.groupBy { it.provider }
        for ((provider, list) in grouped) {
            val total = list.fold(com.enterprise.pos.core.Money.ZERO) { acc, p -> acc + p.amount }
            lines.add("  ${provider.padEnd(20)} ${total.format().padStart(LINE_WIDTH - 24)}")
        }
        val grandTotal = payments.fold(com.enterprise.pos.core.Money.ZERO) { acc, p -> acc + p.amount }
        lines.add("")
        lines.add("TOTAL${grandTotal.format().padStart(LINE_WIDTH - 5)}")
        lines.add("Transactions: ${payments.size}")

        return buildReport("X-REPORT", lines)
    }

    /** Returns ESC/POS bytes for a Z-report (end-of-shift that resets). */
    fun buildZReport(openTime: Long, closeTime: Long, payments: List<Payment>): ByteArray {
        val lines = mutableListOf<String>()
        lines.add("Z-REPORT (END OF SHIFT)")
        lines.add("Opened: ${formatTime(openTime)}")
        lines.add("Closed: ${formatTime(closeTime)}")
        lines.add("")
        lines.add("Payment Summary:")

        val grouped = payments.groupBy { it.provider }
        for ((provider, list) in grouped) {
            val total = list.fold(com.enterprise.pos.core.Money.ZERO) { acc, p -> acc + p.amount }
            lines.add("  ${provider.padEnd(20)} ${total.format().padStart(LINE_WIDTH - 24)}")
        }
        val grandTotal = payments.fold(com.enterprise.pos.core.Money.ZERO) { acc, p -> acc + p.amount }
        lines.add("")
        lines.add("TOTAL${grandTotal.format().padStart(LINE_WIDTH - 5)}")
        lines.add("Transactions: ${payments.size}")
        lines.add("")
        lines.add("SHIFT CLOSED")

        return buildReport("Z-REPORT", lines)
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))
    }
}
