package com.enterprise.pos.feature.inventory.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.PurchaseOrder
import com.enterprise.pos.domain.model.PurchaseOrderStatus
import com.enterprise.pos.domain.model.Supplier
import com.enterprise.pos.domain.model.SupplierPerformance
import com.enterprise.pos.feature.inventory.state.SupplierDetailEvent
import com.enterprise.pos.feature.inventory.state.SupplierDetailUiState
import com.enterprise.pos.feature.inventory.state.SupplierDetailViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierDetailScreen(
    supplierId: Id<com.enterprise.pos.domain.model.SupplierTag>,
    storeId: StoreId,
    onNavigateToPurchaseOrder: (Id<com.enterprise.pos.domain.model.SupplierTag>?) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: SupplierDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(supplierId, storeId) {
        viewModel.load(supplierId, storeId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SupplierDetailEvent.NavigateToPurchaseOrder -> onNavigateToPurchaseOrder(event.supplierId)
                is SupplierDetailEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Supplier Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openDeleteDialog) {
                        Icon(Icons.Default.Delete, "Delete supplier")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoading) {
                item { LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp)) }
            } else if (state.supplier == null) {
                item {
                    EmptyState(
                        title = "Supplier Not Found",
                        message = "The requested supplier could not be loaded.",
                        icon = Icons.Default.Business
                    )
                }
            } else {
                val supplier = state.supplier!!
                item { SupplierHeaderCard(supplier) }
                item { ContactInfoCard(supplier) }
                item { PerformanceMetricsCard(state.performance) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrimaryButton(
                            text = "Create PO",
                            onClick = viewModel::createPurchaseOrder,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.AddShoppingCart
                        )
                        SecondaryButton(
                            text = "View POs",
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.List
                        )
                    }
                }
                item {
                    Text(
                        "Purchase Order History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.orders.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Orders",
                            message = "No purchase orders found for this supplier.",
                            icon = Icons.Default.ReceiptLong
                        )
                    }
                } else {
                    items(state.orders, key = { it.id.value }) { order ->
                        PurchaseOrderHistoryCard(order)
                    }
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        PosAlertDialog(
            title = "Delete Supplier",
            message = "Are you sure you want to delete ${state.supplier?.name}? This action cannot be undone.",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::closeDeleteDialog,
            confirmText = "Delete",
            dismissText = "Cancel"
        )
    }
}

@Composable
private fun SupplierHeaderCard(supplier: Supplier) {
    ElevatedPosCard(
        title = supplier.name,
        icon = Icons.Default.Business
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    status = if (supplier.active) StatusType.ACTIVE else StatusType.INACTIVE,
                    label = if (supplier.active) "Active" else "Inactive"
                )
                supplier.paymentTerms?.let {
                    FilterChip(label = "Terms: $it", selected = false, onClick = {})
                }
            }
        }
    }
}

@Composable
private fun ContactInfoCard(supplier: Supplier) {
    ElevatedPosCard(
        title = "Contact Information",
        icon = Icons.Default.ContactPage
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Contact Person", supplier.contactPerson ?: "—")
            InfoRow("Email", supplier.email ?: "—")
            InfoRow("Phone", supplier.phone ?: "—")
            InfoRow("Address", supplier.address ?: "—")
            InfoRow("Lead Time", "${supplier.leadTimeDays} days")
        }
    }
}

@Composable
private fun PerformanceMetricsCard(performance: SupplierPerformance?) {
    ElevatedPosCard(
        title = "Performance Metrics",
        icon = Icons.Default.Assessment
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (performance == null) {
                Text(
                    "No performance data available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = "Total Orders",
                        value = performance.totalOrders.toString(),
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Receipt
                    )
                    StatCard(
                        label = "On-Time %",
                        value = "${(performance.onTimeDeliveryRate * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule
                    )
                    StatCard(
                        label = "Quality",
                        value = "${(performance.qualityRating * 100).toInt()}%",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Star
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                StatCard(
                    label = "Avg Lead Time",
                    value = "${performance.averageLeadTimeDays.toInt()} days",
                    icon = Icons.Default.Timelapse
                )
            }
        }
    }
}

@Composable
private fun PurchaseOrderHistoryCard(order: PurchaseOrder) {
    val statusColor = when (order.status) {
        PurchaseOrderStatus.DRAFT -> MaterialTheme.colorScheme.outline
        PurchaseOrderStatus.SENT -> MaterialTheme.colorScheme.primary
        PurchaseOrderStatus.PARTIAL -> MaterialTheme.colorScheme.secondary
        PurchaseOrderStatus.RECEIVED -> MaterialTheme.colorScheme.tertiary
        PurchaseOrderStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }
    OutlinedPosCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PO #${order.id.value.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${order.lines.size} items · ${order.total.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(order.orderDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    order.status.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SupplierDetailScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupplierHeaderCard(
                    Supplier(
                        id = Id("sup1"),
                        name = "Global Roasters Inc.",
                        contactPerson = "Jane Smith",
                        email = "jane@globalroasters.com",
                        phone = "+1-555-123-4567",
                        address = "123 Bean St, Seattle, WA",
                        paymentTerms = "Net 30",
                        leadTimeDays = 7
                    )
                )
                ContactInfoCard(
                    Supplier(
                        id = Id("sup1"),
                        name = "Global Roasters Inc.",
                        contactPerson = "Jane Smith",
                        email = "jane@globalroasters.com",
                        phone = "+1-555-123-4567",
                        address = "123 Bean St, Seattle, WA",
                        paymentTerms = "Net 30",
                        leadTimeDays = 7
                    )
                )
                PerformanceMetricsCard(
                    SupplierPerformance(
                        supplierId = Id("sup1"),
                        totalOrders = 42,
                        onTimeDeliveryRate = 0.95,
                        qualityRating = 0.88,
                        averageLeadTimeDays = 6.5
                    )
                )
                PurchaseOrderHistoryCard(
                    PurchaseOrder(
                        id = Id("po1"),
                        storeId = StoreId("s1"),
                        supplierId = Id("sup1"),
                        supplierName = "Global Roasters Inc.",
                        orderDate = System.currentTimeMillis() - 86400000,
                        status = PurchaseOrderStatus.RECEIVED,
                        lines = listOf(
                            PurchaseOrderLine(
                                id = "l1",
                                productId = com.enterprise.pos.core.ProductId("p1"),
                                productName = "Coffee Beans",
                                quantity = 50,
                                unitCost = Money.of(8.50)
                            )
                        )
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SupplierDetailScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupplierHeaderCard(
                    Supplier(
                        id = Id("sup1"),
                        name = "Global Roasters Inc.",
                        contactPerson = "Jane Smith",
                        email = "jane@globalroasters.com",
                        phone = "+1-555-123-4567",
                        address = "123 Bean St, Seattle, WA",
                        paymentTerms = "Net 30",
                        leadTimeDays = 7
                    )
                )
                ContactInfoCard(
                    Supplier(
                        id = Id("sup1"),
                        name = "Global Roasters Inc.",
                        contactPerson = "Jane Smith",
                        email = "jane@globalroasters.com",
                        phone = "+1-555-123-4567",
                        address = "123 Bean St, Seattle, WA",
                        paymentTerms = "Net 30",
                        leadTimeDays = 7
                    )
                )
            }
        }
    }
}
