package com.enterprise.pos.feature.inventory.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.InventoryItem
import com.enterprise.pos.domain.model.Money
import com.enterprise.pos.feature.inventory.state.StockAdjustmentEvent
import com.enterprise.pos.feature.inventory.state.StockAdjustmentUiState
import com.enterprise.pos.feature.inventory.state.StockAdjustmentViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAdjustmentScreen(
    variantId: VariantId,
    storeId: StoreId,
    employeeId: EmployeeId,
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: StockAdjustmentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(variantId, storeId) {
        viewModel.load(variantId, storeId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StockAdjustmentEvent.Saved -> onSaved()
                is StockAdjustmentEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Adjustment", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isSaving && state.product == null) {
                LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp))
            } else if (state.product == null) {
                EmptyState(
                    title = "Product Not Found",
                    message = "Unable to load product details.",
                    icon = Icons.Default.Inventory2
                )
            } else {
                ProductSummaryCard(state.product!!)
                FormSection(title = "Adjustment Details") {
                    AdjustmentForm(state, viewModel)
                }
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "Save Adjustment",
                    onClick = { viewModel.saveAdjustment(storeId, employeeId) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    icon = Icons.Default.Save,
                    enabled = !state.isSaving
                )
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissResult()
                onSaved()
            },
            title = { Text("Success") },
            text = { Text(result) },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissResult()
                    onSaved()
                }) { Text("OK") }
            }
        )
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = viewModel::dismissError) { Text("OK") }
            }
        )
    }
}

@Composable
private fun ProductSummaryCard(product: InventoryItem) {
    ElevatedPosCard(
        title = product.productName,
        subtitle = "SKU: ${product.sku}",
        icon = Icons.Default.Inventory2
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                InfoColumn("Current Quantity", product.onHand.toString())
                InfoColumn("Available", product.available.toString())
                InfoColumn("Location", product.location ?: "—")
            }
        }
    }
}

@Composable
private fun AdjustmentForm(state: StockAdjustmentUiState, viewModel: StockAdjustmentViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosTextField(
            value = state.form.adjustmentQuantity,
            onValueChange = viewModel::setAdjustmentQuantity,
            label = "Adjustment Quantity",
            helperText = "Use negative for stock reduction",
            isError = state.validationErrors.containsKey("quantity"),
            errorText = state.validationErrors["quantity"] ?: "",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            leadingIcon = Icons.Default.CompareArrows
        )

        PosTextField(
            value = state.form.newQuantity.toString(),
            onValueChange = {},
            label = "New Quantity",
            readOnly = true,
            enabled = false,
            leadingIcon = Icons.Default.Calculate
        )

        Text(
            "Reason",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.reasons.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { reason ->
                        FilterChip(
                            label = reason.name.replace('_', ' ').lowercase()
                                .replaceFirstChar { it.titlecase() },
                            selected = state.form.reason == reason,
                            onClick = { viewModel.setReason(reason) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        PosTextField(
            value = state.form.notes,
            onValueChange = viewModel::setNotes,
            label = "Notes",
            placeholder = "Optional notes about this adjustment",
            singleLine = false,
            leadingIcon = Icons.Default.Notes
        )
    }
}

@Composable
private fun InfoColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Preview(showBackground = true)
@Composable
private fun StockAdjustmentScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ProductSummaryCard(
                    InventoryItem(
                        variantId = com.enterprise.pos.core.VariantId("v1"),
                        storeId = com.enterprise.pos.core.StoreId("s1"),
                        productName = "Organic Coffee Beans",
                        sku = "OCB-001",
                        onHand = 45,
                        available = 40,
                        location = "A-12-3"
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                FormSection(title = "Adjustment Details") {
                    AdjustmentForm(
                        state = StockAdjustmentUiState(
                            form = com.enterprise.pos.feature.inventory.state.StockAdjustmentForm(
                                productName = "Organic Coffee Beans",
                                currentQuantity = 45,
                                adjustmentQuantity = "-5",
                                newQuantity = 40
                            )
                        ),
                        viewModel = object : StockAdjustmentViewModel(
                            inventoryRepo = object : com.enterprise.pos.domain.repository.InventoryManagementRepository {
                                override fun observeLowStock(storeId: com.enterprise.pos.core.StoreId) = kotlinx.coroutines.flow.emptyFlow()
                                override fun observeAdjustments(storeId: com.enterprise.pos.core.StoreId, variantId: com.enterprise.pos.core.VariantId?) = kotlinx.coroutines.flow.emptyFlow()
                                override fun observeTransfers(storeId: com.enterprise.pos.core.StoreId) = kotlinx.coroutines.flow.emptyFlow()
                                override suspend fun adjust(adjustment: com.enterprise.pos.domain.model.InventoryAdjustment) = com.enterprise.pos.core.Result.success(adjustment)
                                override suspend fun createTransfer(transfer: com.enterprise.pos.domain.model.InventoryTransfer) = com.enterprise.pos.core.Result.success(transfer)
                                override suspend fun receiveTransfer(transferId: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.InventoryTransferTag>, receivedLines: List<com.enterprise.pos.domain.model.TransferLine>) = com.enterprise.pos.core.Result.success(transfer)
                                override suspend fun reorder(variantId: com.enterprise.pos.core.VariantId, storeId: com.enterprise.pos.core.StoreId, qty: Int) = com.enterprise.pos.core.Result.success(Unit)
                                override suspend fun reorderAll(storeId: com.enterprise.pos.core.StoreId) = com.enterprise.pos.core.Result.success(0)
                                override suspend fun valuation(storeId: com.enterprise.pos.core.StoreId) = com.enterprise.pos.core.Result.success(Money.ZERO)
                                override suspend fun getInventoryItem(variantId: com.enterprise.pos.core.VariantId, storeId: com.enterprise.pos.core.StoreId) = com.enterprise.pos.core.Result.success(null)
                                override fun observeStockMovements(storeId: com.enterprise.pos.core.StoreId, variantId: com.enterprise.pos.core.VariantId) = kotlinx.coroutines.flow.emptyFlow()
                                override suspend fun setReorderPoint(variantId: com.enterprise.pos.core.VariantId, storeId: com.enterprise.pos.core.StoreId, point: Int, qty: Int) = com.enterprise.pos.core.Result.success(Unit)
                                override suspend fun getSupplier(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.SupplierTag>) = com.enterprise.pos.core.Result.success(null)
                                override fun observeSuppliers(storeId: com.enterprise.pos.core.StoreId) = kotlinx.coroutines.flow.emptyFlow()
                                override suspend fun getSupplierPerformance(supplierId: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.SupplierTag>) = com.enterprise.pos.core.Result.success(null)
                                override suspend fun observePurchaseOrders(storeId: com.enterprise.pos.core.StoreId, supplierId: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.SupplierTag>?) = kotlinx.coroutines.flow.emptyFlow()
                                override suspend fun getPurchaseOrder(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.PurchaseOrderTag>) = com.enterprise.pos.core.Result.success(null)
                                override suspend fun upsertPurchaseOrder(po: com.enterprise.pos.domain.model.PurchaseOrder) = com.enterprise.pos.core.Result.success(po)
                                override suspend fun sendPurchaseOrder(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.PurchaseOrderTag>) = com.enterprise.pos.core.Result.success(null)
                                override suspend fun receivePurchaseOrder(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.PurchaseOrderTag>) = com.enterprise.pos.core.Result.success(null)
                                override suspend fun cancelPurchaseOrder(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.PurchaseOrderTag>) = com.enterprise.pos.core.Result.success(null)
                            }
                        ) {}
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton(
                    text = "Save Adjustment",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    icon = Icons.Default.Save
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StockAdjustmentScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                ProductSummaryCard(
                    InventoryItem(
                        variantId = com.enterprise.pos.core.VariantId("v1"),
                        storeId = com.enterprise.pos.core.StoreId("s1"),
                        productName = "Organic Coffee Beans",
                        sku = "OCB-001",
                        onHand = 45,
                        available = 40,
                        location = "A-12-3"
                    )
                )
            }
        }
    }
}
