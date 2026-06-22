package com.enterprise.pos.feature.sales.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderLineId
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.OrderLine
import com.enterprise.pos.domain.model.OrderLineType
import com.enterprise.pos.feature.sales.state.CartViewModel

/** Main cart / order screen — shows line items, totals, and action buttons. */
@Composable
fun CartScreen(
    orderId: OrderId,
    requestingEmployee: EmployeeId,
    onCheckout: (OrderId) -> Unit,
    onBack: () -> Unit,
    viewModel: CartViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(orderId) { viewModel.loadOrder(orderId) }

    val order = state.order
    LaunchedEffect(state.showPaymentScreen, order?.id) {
        val readyOrder = order
        if (state.showPaymentScreen && readyOrder != null) {
            onCheckout(readyOrder.id)
            viewModel.exitPayment()
        }
    }

    if (order == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(modifier = Modifier.weight(1f)) {
                Text("Order ${order.id.value.takeLast(6).uppercase()}", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${order.diningMode.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }}" +
                        (order.tableName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (order.guestCount > 0) Text("${order.guestCount} guests", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))

        // Lines
        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (order.lines.filter { it.lineType == OrderLineType.ITEM }.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ShoppingCart, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("Add items from the menu", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(order.lines.filter { it.lineType == OrderLineType.ITEM }, key = { it.id.value }) { line ->
                        CartLineRow(
                            line = line,
                            onIncrease = { viewModel.changeQuantity(line.id, line.quantity + 1) },
                            onDecrease = { if (line.quantity > 1) viewModel.changeQuantity(line.id, line.quantity - 1) else viewModel.removeLine(line.id) },
                            onRemove = { viewModel.removeLine(line.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Totals
        val bd = state.breakdown
        if (bd != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TotalRow("Subtotal", bd.subtotal.format())
                    if (!bd.totalDiscount.isZero()) TotalRow("Discounts", "-${bd.totalDiscount.format()}")
                    if (!bd.taxes.isZero()) TotalRow("Tax", bd.taxes.format())
                    if (!bd.tip.isZero()) TotalRow("Tip", bd.tip.format())
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text("Total", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(bd.grandTotal.format(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Action row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::openDiscountSheet, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.LocalOffer, null); Spacer(Modifier.width(4.dp)); Text("Discount")
            }
            OutlinedButton(onClick = viewModel::openTipSheet, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.TipsAndUpdates, null); Spacer(Modifier.width(4.dp)); Text("Tip")
            }
            OutlinedButton(
                onClick = { viewModel.sendToKitchen() },
                enabled = !state.isSendingToKitchen && order.lines.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                if (state.isSendingToKitchen) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(4.dp)); Text("Kitchen")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::requestPayment,
            enabled = !state.isFinalizing && order.lines.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            if (state.isFinalizing) CircularProgressIndicator(strokeWidth = 2.dp)
            else { Icon(Icons.Filled.Payment, null); Spacer(Modifier.width(8.dp)); Text("Charge ${state.breakdown?.grandTotal?.format() ?: ""}", style = MaterialTheme.typography.titleLarge) }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(8.dp))
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null)
                    Spacer(Modifier.width(8.dp))
                    Text(err, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }
        }
        state.info?.let { info ->
            Spacer(Modifier.height(8.dp))
            Snackbar { Text(info) }
            LaunchedEffect(info) {
                kotlinx.coroutines.delay(2500)
                viewModel.dismissInfo()
            }
        }
    }

    if (state.showDiscountSheet) {
        DiscountSheet(
            onDismiss = viewModel::closeDiscountSheet,
            onApply = { percent -> viewModel.applyDiscount(percent, requestingEmployee) }
        )
    }
    if (state.showTipSheet) {
        TipSheet(
            subtotal = state.breakdown?.subtotal ?: Money.ZERO,
            onDismiss = viewModel::closeTipSheet,
            onApply = { amount -> viewModel.setTip(amount) }
        )
    }
}

@Composable
private fun CartLineRow(
    line: OrderLine,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            line.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!line.discount.isZero()) {
                Text("Discount: -${line.discount.format()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text("${line.unitPrice.format()} ea", style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onDecrease) { Icon(Icons.Filled.RemoveCircle, null, tint = MaterialTheme.colorScheme.primary) }
            Text(line.quantity.asInt.toString(), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onIncrease) { Icon(Icons.Filled.AddCircle, null, tint = MaterialTheme.colorScheme.primary) }
        }
        Spacer(Modifier.width(12.dp))
        Text(line.lineTotal.format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp))
        IconButton(onClick = onRemove) { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscountSheet(onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Apply Discount", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 20, 25, 50, 100).forEach { p ->
                    OutlinedButton(onClick = { onApply(p) }, modifier = Modifier.weight(1f)) { Text("$p%") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Manager approval required for >25%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipSheet(subtotal: Money, onDismiss: () -> Unit, onApply: (Money) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Add Tip", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            listOf(15, 18, 20, 25).forEach { p ->
                val amount = subtotal * java.math.BigDecimal.valueOf(p.toLong(), 2)
                ListItem(
                    headlineContent = { Text("$p%") },
                    trailingContent = { Text(amount.format(), style = MaterialTheme.typography.titleMedium) },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(Money.ZERO) }) { Text("No Tip") }
            }
        }
    }
}
