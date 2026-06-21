package com.enterprise.pos.feature.sales.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.payment.model.PaymentEvent
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.feature.sales.state.CheckoutViewModel

@Composable
fun CheckoutScreen(
    orderId: OrderId,
    employeeId: EmployeeId,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(orderId) { viewModel.loadOrder(orderId) }

    LaunchedEffect(state.result) {
        if (state.result != null) {
            // Auto-advance after success — show success animation briefly.
            kotlinx.coroutines.delay(1500)
            onComplete()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Take Payment", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Amount Due", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                Text(state.amountDue.format(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (state.result == null) {
            Text("Choose Payment Method", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PaymentProviderList(
                available = state.availableProviders,
                selected = state.selectedProvider,
                amountDue = state.amountDue,
                cashTenderedInput = state.cashTenderedInput,
                cashChangeDue = state.cashChangeDue,
                isProcessing = state.isProcessing,
                onSelect = viewModel::selectProvider,
                onCashTenderedChange = viewModel::setCashTendered,
                onPay = { viewModel.startPayment(orderId, employeeId) }
            )
        } else {
            SuccessCard(amount = state.result!!.amount, provider = state.result!!.provider.name)
        }

        state.currentEvent?.let { event ->
            AnimatedVisibility(visible = true) {
                PaymentEventBanner(event)
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null)
                    Spacer(Modifier.width(8.dp))
                    Text(err, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (state.isProcessing) {
            Button(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.Cancel, null); Spacer(Modifier.width(8.dp)); Text("Cancel Payment")
            }
        } else if (state.result == null) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Back to Order") }
        }
    }
}

@Composable
private fun PaymentProviderList(
    available: Set<PaymentProviderId>,
    selected: PaymentProviderId?,
    amountDue: Money,
    cashTenderedInput: String,
    cashChangeDue: Money,
    isProcessing: Boolean,
    onSelect: (PaymentProviderId) -> Unit,
    onCashTenderedChange: (String) -> Unit,
    onPay: () -> Unit
) {
    val providers = available.sortedBy { it.ordinal }
    Column {
        providers.forEach { p ->
            val icon = when (p) {
                PaymentProviderId.STRIPE -> Icons.Filled.CreditCard
                PaymentProviderId.SQUARE -> Icons.Filled.QrCode2
                PaymentProviderId.SHOPIFY -> Icons.Filled.ShoppingBag
                PaymentProviderId.CASH -> Icons.Filled.AttachMoney
                PaymentProviderId.MANUAL -> Icons.Filled.Edit
                PaymentProviderId.EXTERNAL -> Icons.Filled.OpenInNew
            }
            val label = when (p) {
                PaymentProviderId.STRIPE -> "Stripe Terminal"
                PaymentProviderId.SQUARE -> "Square Reader"
                PaymentProviderId.SHOPIFY -> "Shopify POS"
                PaymentProviderId.CASH -> "Cash"
                PaymentProviderId.MANUAL -> "Manual Card Entry"
                PaymentProviderId.EXTERNAL -> "External"
            }
            val sub = when (p) {
                PaymentProviderId.STRIPE -> "Insert, tap, or swipe — reader required"
                PaymentProviderId.SQUARE -> "Square contactless + chip reader"
                PaymentProviderId.SHOPIFY -> "Hand off to Shopify POS app"
                PaymentProviderId.CASH -> "Open drawer, accept cash"
                PaymentProviderId.MANUAL -> "Type card number"
                PaymentProviderId.EXTERNAL -> "Process outside POS"
            }
            ListItem(
                headlineContent = { Text(label) },
                supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Icon(icon, null) },
                trailingContent = {
                    RadioButton(selected = selected == p, onClick = { onSelect(p) })
                },
                modifier = Modifier.padding(vertical = 2.dp)
            )
            HorizontalDivider()
        }
    }
    AnimatedVisibility(visible = selected == PaymentProviderId.CASH) {
        Column {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cashTenderedInput,
                onValueChange = onCashTenderedChange,
                label = { Text("Cash tendered") },
                leadingIcon = { Icon(Icons.Filled.AttachMoney, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (!cashChangeDue.isZero()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Change due ${cashChangeDue.format()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onPay,
        enabled = selected != null && !isProcessing,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        if (isProcessing) CircularProgressIndicator(strokeWidth = 2.dp)
        else { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Charge ${amountDue.format()}") }
    }
}

@Composable
private fun SuccessCard(amount: Money, provider: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            Text("Approved", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(amount.format(), style = MaterialTheme.typography.titleLarge)
            Text("via $provider", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PaymentEventBanner(event: PaymentEvent) {
    val (icon, text, color) = when (event) {
        is PaymentEvent.InsertCard -> Triple(Icons.Filled.CreditCard, event.message, MaterialTheme.colorScheme.primary)
        is PaymentEvent.ReadingCard -> Triple(Icons.Filled.Nfc, event.message, MaterialTheme.colorScheme.primary)
        is PaymentEvent.Processing -> Triple(Icons.Filled.HourglassEmpty, event.message, MaterialTheme.colorScheme.primary)
        is PaymentEvent.WaitingForSignature -> Triple(Icons.Filled.Draw, event.message, MaterialTheme.colorScheme.secondary)
        is PaymentEvent.WaitingForPin -> Triple(Icons.Filled.Dialpad, event.message, MaterialTheme.colorScheme.secondary)
        is PaymentEvent.Success -> Triple(Icons.Filled.CheckCircle, event.message, MaterialTheme.colorScheme.tertiary)
        is PaymentEvent.Declined -> Triple(Icons.Filled.Cancel, "Declined: ${event.reason}", MaterialTheme.colorScheme.error)
        is PaymentEvent.Error -> Triple(Icons.Filled.Error, event.message, MaterialTheme.colorScheme.error)
        is PaymentEvent.Cancelled -> Triple(Icons.Filled.Cancel, event.message, MaterialTheme.colorScheme.outline)
        is PaymentEvent.ReaderMessage -> Triple(Icons.Filled.Info, event.message, MaterialTheme.colorScheme.primary)
        is PaymentEvent.OfflineQueued -> Triple(Icons.Filled.CloudQueue, event.message, MaterialTheme.colorScheme.secondary)
    }
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, fontWeight = FontWeight.Medium)
        }
    }
}
