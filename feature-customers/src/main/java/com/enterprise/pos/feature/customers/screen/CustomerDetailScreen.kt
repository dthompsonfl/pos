package com.enterprise.pos.feature.customers.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.feature.customers.state.CustomerDetailEvent
import com.enterprise.pos.feature.customers.state.CustomerDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: CustomerId,
    storeId: StoreId,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onDeleted: () -> Unit = {},
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customerId, storeId) { viewModel.load(customerId, storeId) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerDetailEvent.Deleted -> onDeleted()
                is CustomerDetailEvent.Error -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.customer?.name ?: "Customer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            )
        }
    ) { padding ->
        val customer = state.customer
        if (customer == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text(state.error ?: "Not found")
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header card with loyalty points + LTV
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(customer.name.take(1), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            customer.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                            customer.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(customer.loyaltyPoints.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("pts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            // KPI row
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpiCard("LTV", state.lifetimeValue.format(), Modifier.weight(1f))
                    KpiCard("Orders", state.totalOrders.toString(), Modifier.weight(1f))
                    KpiCard("Avg Order", state.averageOrderValue.format(), Modifier.weight(1f))
                    KpiCard("Credit", customer.storeCredit.format(), Modifier.weight(1f))
                }
            }

            // Visit history
            state.firstOrderDate?.let { first ->
                state.lastOrderDate?.let { last ->
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Visit History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Text("First visit", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.weight(1f))
                                    Text(SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(first)), fontWeight = FontWeight.SemiBold)
                                }
                                Row(Modifier.fillMaxWidth()) {
                                    Text("Last visit", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.weight(1f))
                                    Text(SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(last)), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Customer details
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        customer.address?.let { InfoRow("Address", it) }
                        customer.city?.let { InfoRow("City", it) }
                        customer.state?.let { InfoRow("State", it) }
                        customer.zip?.let { InfoRow("ZIP", it) }
                        customer.country?.let { InfoRow("Country", it) }
                        customer.loyaltyNumber?.let { InfoRow("Loyalty #", it) }
                        customer.group?.let { InfoRow("Group", it) }
                        if (customer.tags.isNotEmpty()) { InfoRow("Tags", customer.tags.joinToString(", ")) }
                        if (customer.marketingOptIn) { InfoRow("Marketing", "Opted in") }
                    }
                }
            }

            // Favorite items
            if (state.favoriteItems.isNotEmpty()) {
                item { Text("Favorite Items", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(state.favoriteItems) { (name, count) ->
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary) },
                        headlineContent = { Text(name) },
                        trailingContent = { Text("${count}x", fontWeight = FontWeight.SemiBold) }
                    )
                    HorizontalDivider()
                }
            }

            // Dietary restrictions
            if (customer.dietaryRestrictions.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("Dietary Restrictions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(customer.dietaryRestrictions.joinToString(", "), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            // Loyalty rewards
            if (state.availableRewards.isNotEmpty()) {
                item { Text("Available Rewards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                items(state.availableRewards.filter { it.pointsCost <= customer.loyaltyPoints }) { reward ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CardGiftcard, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(reward.name, fontWeight = FontWeight.SemiBold)
                                Text("${reward.pointsCost} pts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Button(onClick = { viewModel.redeemReward(customerId, reward) }) { Text("Redeem") }
                        }
                    }
                }
            }

            // Recent orders
            item { Text("Recent Orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(state.orders.take(20)) { order ->
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Receipt, null) },
                    headlineContent = { Text("Order ${order.id.value.takeLast(6).uppercase()}") },
                    supportingContent = { Text(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(order.createdAt))) },
                    trailingContent = { Text(order.grandTotal.format(), fontWeight = FontWeight.SemiBold) }
                )
                HorizontalDivider()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Customer") },
            text = { Text("This will permanently delete ${state.customer?.name ?: "this customer"}. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCustomer(customerId); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
