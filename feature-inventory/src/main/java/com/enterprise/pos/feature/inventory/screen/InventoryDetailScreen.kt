package com.enterprise.pos.feature.inventory.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.*
import com.enterprise.pos.feature.inventory.state.InventoryDetailEvent
import com.enterprise.pos.feature.inventory.state.InventoryDetailUiState
import com.enterprise.pos.feature.inventory.state.InventoryDetailViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDetailScreen(
    variantId: VariantId,
    storeId: StoreId,
    onNavigateToAdjustment: (VariantId) -> Unit = {},
    onNavigateToPurchaseOrder: (Id<SupplierTag>?) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: InventoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(variantId, storeId) {
        viewModel.load(variantId, storeId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is InventoryDetailEvent.NavigateToAdjustment -> onNavigateToAdjustment(event.variantId)
                is InventoryDetailEvent.NavigateToPurchaseOrder -> onNavigateToPurchaseOrder(event.supplierId)
                is InventoryDetailEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openAdjustDialog) {
                        Icon(Icons.Default.Edit, "Adjust stock")
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
            } else if (state.item == null) {
                item {
                    EmptyState(
                        title = "Item Not Found",
                        message = "The requested inventory item could not be loaded.",
                        icon = Icons.Default.Inventory2
                    )
                }
            } else {
                val item = state.item!!
                item { InventoryHeaderCard(item) }
                item { StockSummaryCard(item) }
                item { ActionsCard(viewModel, state) }
                item {
                    Text(
                        "Stock Movement Log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.movements.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No Movements",
                            message = "No stock movements recorded for this item.",
                            icon = Icons.Default.History
                        )
                    }
                } else {
                    items(state.movements, key = { it.id }) { movement ->
                        MovementCard(movement)
                    }
                }
            }
        }
    }

    state.info?.let { info ->
        SnackbarHost(
            hostState = remember { SnackbarHostState() }.apply {
                LaunchedEffect(info) { showSnackbar(info) }
            }
        )
    }

    if (state.showReorderDialog) {
        ReorderPointDialog(
            reorderPoint = state.reorderPoint,
            reorderQuantity = state.reorderQuantity,
            onReorderPointChange = viewModel::setReorderPoint,
            onReorderQuantityChange = viewModel::setReorderQuantity,
            onDismiss = viewModel::closeReorderDialog,
            onConfirm = { viewModel.saveReorderPoint(storeId) }
        )
    }
}

@Composable
private fun InventoryHeaderCard(item: InventoryItem) {
    ElevatedPosCard(
        title = item.productName,
        subtitle = "SKU: ${item.sku}",
        icon = Icons.Default.Inventory2
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Location", item.location ?: "—", Modifier.weight(1f))
                InfoRow("Supplier", item.supplierName ?: "—", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Unit Cost", item.unitCost?.format() ?: "—", Modifier.weight(1f))
                InfoRow(
                    "Last Counted",
                    item.lastCountedAt?.let { SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(it)) } ?: "—",
                    Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StockSummaryCard(item: InventoryItem) {
    ElevatedPosCard(
        title = "Stock Levels",
        icon = Icons.Default.BarChart
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "On Hand",
                    value = item.onHand.toString(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warehouse
                )
                StatCard(
                    label = "Committed",
                    value = item.committed.toString(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Lock
                )
                StatCard(
                    label = "Available",
                    value = item.available.toString(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Reorder Point",
                    value = item.reorderPoint.toString(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Warning
                )
                StatCard(
                    label = "Reorder Qty",
                    value = item.reorderQuantity.toString(),
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Autorenew
                )
            }
            if (item.isLow) {
                InfoCard(
                    type = InfoCardType.WARNING,
                    title = "Low Stock Alert",
                    message = "Available stock is below the reorder point.",
                    icon = Icons.Default.Warning
                )
            }
        }
    }
}

@Composable
private fun ActionsCard(viewModel: InventoryDetailViewModel, state: InventoryDetailUiState) {
    ElevatedPosCard(
        title = "Actions",
        icon = Icons.Default.MoreVert
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Adjust Stock",
                    onClick = viewModel::openAdjustDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit
                )
                SecondaryButton(
                    text = "Set Reorder",
                    onClick = viewModel::openReorderDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Tune
                )
            }
            PrimaryButton(
                text = "Create Purchase Order",
                onClick = viewModel::createPurchaseOrder,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.AddShoppingCart
            )
            SecondaryButton(
                text = "View History",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Default.History
            )
        }
    }
}

@Composable
private fun MovementCard(movement: StockMovement) {
    val typeColor = when (movement.type) {
        StockMovementType.ADJUSTMENT -> MaterialTheme.colorScheme.secondary
        StockMovementType.SALE -> MaterialTheme.colorScheme.error
        StockMovementType.RECEIPT -> MaterialTheme.colorScheme.tertiary
        StockMovementType.RETURN -> MaterialTheme.colorScheme.primary
        StockMovementType.TRANSFER -> MaterialTheme.colorScheme.outline
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
                    movement.type.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor
                )
                Text(
                    movement.reason ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                movement.employeeName?.let {
                    Text(
                        "By $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (movement.quantity >= 0) "+" else ""}${movement.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (movement.quantity >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                )
                Text(
                    SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(movement.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ReorderPointDialog(
    reorderPoint: String,
    reorderQuantity: String,
    onReorderPointChange: (String) -> Unit,
    onReorderQuantityChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reorder Point") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PosTextField(
                    value = reorderPoint,
                    onValueChange = onReorderPointChange,
                    label = "Reorder Point",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
                PosTextField(
                    value = reorderQuantity,
                    onValueChange = onReorderQuantityChange,
                    label = "Reorder Quantity",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun InventoryDetailScreenPreview() {
    PosTheme {
        Surface {
            val previewState = InventoryDetailUiState(
                item = InventoryItem(
                    variantId = VariantId("v1"),
                    storeId = StoreId("s1"),
                    productName = "Organic Coffee Beans",
                    sku = "OCB-001",
                    onHand = 45,
                    committed = 5,
                    available = 40,
                    reorderPoint = 10,
                    reorderQuantity = 50,
                    location = "A-12-3",
                    supplierName = "Global Roasters Inc.",
                    unitCost = Money.of(8.50),
                    lastCountedAt = System.currentTimeMillis() - 86400000
                ),
                movements = listOf(
                    StockMovement(
                        id = "m1",
                        variantId = VariantId("v1"),
                        storeId = StoreId("s1"),
                        type = StockMovementType.RECEIPT,
                        quantity = 100,
                        reason = "Weekly restock",
                        employeeName = "John D.",
                        timestamp = System.currentTimeMillis() - 172800000
                    ),
                    StockMovement(
                        id = "m2",
                        variantId = VariantId("v1"),
                        storeId = StoreId("s1"),
                        type = StockMovementType.SALE,
                        quantity = -55,
                        timestamp = System.currentTimeMillis() - 86400000
                    )
                ),
                isLoading = false
            )
            LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { InventoryHeaderCard(previewState.item!!) }
                item { StockSummaryCard(previewState.item!!) }
                item {
                    Text(
                        "Stock Movement Log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(previewState.movements, key = { it.id }) { MovementCard(it) }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InventoryDetailScreenDarkPreview() {
    PosTheme {
        Surface {
            val previewState = InventoryDetailUiState(
                item = InventoryItem(
                    variantId = VariantId("v1"),
                    storeId = StoreId("s1"),
                    productName = "Organic Coffee Beans",
                    sku = "OCB-001",
                    onHand = 45,
                    committed = 5,
                    available = 40,
                    reorderPoint = 10,
                    reorderQuantity = 50,
                    location = "A-12-3",
                    supplierName = "Global Roasters Inc.",
                    unitCost = Money.of(8.50),
                    lastCountedAt = System.currentTimeMillis() - 86400000
                ),
                movements = listOf(
                    StockMovement(
                        id = "m1",
                        variantId = VariantId("v1"),
                        storeId = StoreId("s1"),
                        type = StockMovementType.RECEIPT,
                        quantity = 100,
                        reason = "Weekly restock",
                        employeeName = "John D.",
                        timestamp = System.currentTimeMillis() - 172800000
                    )
                ),
                isLoading = false
            )
            LazyColumn(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { InventoryHeaderCard(previewState.item!!) }
                item { StockSummaryCard(previewState.item!!) }
                item {
                    Text(
                        "Stock Movement Log",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(previewState.movements, key = { it.id }) { MovementCard(it) }
            }
        }
    }
}
