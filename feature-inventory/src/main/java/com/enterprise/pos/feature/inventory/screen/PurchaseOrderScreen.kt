package com.enterprise.pos.feature.inventory.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.PurchaseOrder
import com.enterprise.pos.domain.model.PurchaseOrderLine
import com.enterprise.pos.domain.model.PurchaseOrderStatus
import com.enterprise.pos.domain.model.Supplier
import com.enterprise.pos.feature.inventory.state.PurchaseOrderEvent
import com.enterprise.pos.feature.inventory.state.PurchaseOrderLineForm
import com.enterprise.pos.feature.inventory.state.PurchaseOrderUiState
import com.enterprise.pos.feature.inventory.state.PurchaseOrderViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseOrderScreen(
    orderId: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>? = null,
    preselectedSupplierId: Id<com.enterprise.pos.domain.model.SupplierTag>? = null,
    storeId: StoreId,
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: PurchaseOrderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(orderId, storeId) {
        if (orderId != null) {
            viewModel.loadExisting(orderId, storeId)
        } else {
            viewModel.loadNew(storeId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PurchaseOrderEvent.Saved -> onSaved()
                is PurchaseOrderEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (orderId == null) "New Purchase Order" else "Edit Purchase Order", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            if (state.isLoading) {
                LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp))
            } else {
                FormSection(title = "Order Information") {
                    OrderInfoSection(state, viewModel)
                }
                FormSection(title = "Line Items") {
                    LineItemsSection(state, viewModel)
                }
                FormSection(title = "Totals") {
                    TotalsSection(state, viewModel)
                }
                FormSection(title = "Notes") {
                    PosTextField(
                        value = state.form.notes,
                        onValueChange = viewModel::setNotes,
                        label = "Order Notes",
                        singleLine = false
                    )
                }
                ActionButtonsSection(state, viewModel, orderId, storeId)
            }
        }
    }

    state.result?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::dismissResult,
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
private fun OrderInfoSection(state: PurchaseOrderUiState, viewModel: PurchaseOrderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosDropdownField(
            selectedItem = state.suppliers.find { it.id == state.form.supplierId },
            items = state.suppliers,
            onItemSelected = { viewModel.setSupplier(it) },
            label = "Supplier",
            itemText = { it.name }
        )
        if (state.validationErrors.containsKey("supplier")) {
            Text(
                state.validationErrors["supplier"] ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosDateTimeField(
                value = Date(state.form.orderDate),
                onValueChange = { viewModel.setOrderDate(it.time) },
                label = "Order Date",
                modifier = Modifier.weight(1f)
            )
            PosDateTimeField(
                value = state.form.expectedDelivery?.let { Date(it) },
                onValueChange = { viewModel.setExpectedDelivery(it?.time) },
                label = "Expected Delivery",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LineItemsSection(state: PurchaseOrderUiState, viewModel: PurchaseOrderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.lineForms.isEmpty()) {
            OutlinedPosCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No items added. Add products to the order.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            state.lineForms.forEachIndexed { index, line ->
                LineItemCard(
                    line = line,
                    index = index,
                    onQuantityChange = { viewModel.updateLineQuantity(index, it) },
                    onUnitCostChange = { viewModel.updateLineUnitCost(index, it) },
                    onRemove = { viewModel.removeLineItem(index) }
                )
            }
        }
        if (state.validationErrors.containsKey("lines")) {
            Text(
                state.validationErrors["lines"] ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
        SecondaryButton(
            text = "Add Product",
            onClick = { /* Would show product picker */ },
            icon = Icons.Default.Add
        )
    }
}

@Composable
private fun LineItemCard(
    line: PurchaseOrderLineForm,
    index: Int,
    onQuantityChange: (String) -> Unit,
    onUnitCostChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    ElevatedPosCard(
        title = line.productName,
        actions = {
            PosIconButton(
                icon = Icons.Default.Delete,
                onClick = onRemove,
                contentDescription = "Remove item"
            )
        }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosTextField(
                    value = line.quantity,
                    onValueChange = onQuantityChange,
                    label = "Quantity",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                PosTextField(
                    value = line.unitCost,
                    onValueChange = onUnitCostChange,
                    label = "Unit Cost",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Total: ${line.total.format()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun TotalsSection(state: PurchaseOrderUiState, viewModel: PurchaseOrderViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PosTextField(
            value = state.form.shippingCost,
            onValueChange = viewModel::setShippingCost,
            label = "Shipping Cost",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        PosTextField(
            value = state.form.taxPercent,
            onValueChange = viewModel::setTaxPercent,
            label = "Tax Percent (%)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        TotalRow("Subtotal", state.subtotal.format())
        TotalRow("Shipping", state.shipping.format())
        TotalRow("Tax", state.tax.format())
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        TotalRow("Total", state.total.format(), isBold = true)
    }
}

@Composable
private fun TotalRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActionButtonsSection(
    state: PurchaseOrderUiState,
    viewModel: PurchaseOrderViewModel,
    orderId: Id<com.enterprise.pos.domain.model.PurchaseOrderTag>?,
    storeId: StoreId
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            text = if (orderId == null) "Save Order" else "Update Order",
            onClick = { viewModel.saveOrder(storeId) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            icon = Icons.Default.Save,
            enabled = !state.isSaving
        )
        if (orderId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Send",
                    onClick = viewModel::sendOrder,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.Send
                )
                SecondaryButton(
                    text = "Receive",
                    onClick = viewModel::receiveOrder,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle
                )
                DangerButton(
                    text = "Cancel",
                    onClick = viewModel::cancelOrder,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Cancel
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PurchaseOrderScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                FormSection(title = "Order Information") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(
                            value = "Global Roasters Inc.",
                            onValueChange = {},
                            label = "Supplier",
                            readOnly = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PosTextField(
                                value = "Jun 17, 2025",
                                onValueChange = {},
                                label = "Order Date",
                                readOnly = true,
                                modifier = Modifier.weight(1f)
                            )
                            PosTextField(
                                value = "Jun 24, 2025",
                                onValueChange = {},
                                label = "Expected Delivery",
                                readOnly = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                FormSection(title = "Line Items") {
                    LineItemCard(
                        line = PurchaseOrderLineForm(
                            productName = "Organic Coffee Beans",
                            quantity = "50",
                            unitCost = "8.50"
                        ),
                        index = 0,
                        onQuantityChange = {},
                        onUnitCostChange = {},
                        onRemove = {}
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                FormSection(title = "Totals") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TotalRow("Subtotal", "$425.00")
                        TotalRow("Shipping", "$15.00")
                        TotalRow("Tax", "$35.20")
                        TotalRow("Total", "$475.20", isBold = true)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PurchaseOrderScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                FormSection(title = "Order Information") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(
                            value = "Global Roasters Inc.",
                            onValueChange = {},
                            label = "Supplier",
                            readOnly = true
                        )
                    }
                }
            }
        }
    }
}
