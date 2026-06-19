package com.enterprise.pos.feature.inventory.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.feature.inventory.state.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    storeId: StoreId,
    employeeId: EmployeeId,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId) { viewModel.load(storeId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventory", fontWeight = FontWeight.Bold) },
                actions = {
                    FilterChip(
                        selected = state.lowStockOnly,
                        onClick = { viewModel.toggleLowStockFilter() },
                        label = { Text("Low stock") },
                        leadingIcon = { Icon(Icons.Filled.Warning, null, modifier = Modifier.size(16.dp)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.reorderAll(storeId) }) {
                        Icon(Icons.Filled.Autorenew, "Reorder all")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Summary cards
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("Total Value", state.totalValue.format(), Icons.Filled.Savings, Modifier.weight(1f))
                SummaryCard("Low Stock", state.lowStockCount.toString(), Icons.Filled.ErrorOutline, Modifier.weight(1f))
                SummaryCard("Adjustments", "—", Icons.Filled.Edit, Modifier.weight(1f))
            }

            // List placeholder (production would show inventory rows from a combined query)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Inventory2, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("Inventory Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Tap any tracked variant to adjust stock. Real-time valuation, low-stock alerts, transfers, and reordering are all supported.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { /* navigate to product picker */ }) {
                                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("New Adjustment")
                            }
                        }
                    }
                }
            }
        }

        if (state.showAdjustmentSheet) {
            AdjustmentSheet(
                delta = state.adjustmentDelta,
                reason = state.adjustmentReason,
                notes = state.adjustmentNotes,
                onDeltaChange = viewModel::setDelta,
                onReasonChange = viewModel::setReason,
                onNotesChange = viewModel::setNotes,
                onDismiss = viewModel::closeAdjustmentSheet,
                onSubmit = { viewModel.submitAdjustment(storeId, employeeId) }
            )
        }

        state.info?.let { info ->
            Snackbar { Text(info) }
            LaunchedEffect(info) {
                kotlinx.coroutines.delay(2500)
                viewModel.dismissInfo()
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentSheet(
    delta: String,
    reason: AdjustmentReason,
    notes: String,
    onDeltaChange: (String) -> Unit,
    onReasonChange: (AdjustmentReason) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Adjust Inventory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = delta,
                onValueChange = onDeltaChange,
                label = { Text("Quantity change (use - for negative)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))
            Text("Reason", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            // Reason picker — chip row
            AdjustmentReason.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        FilterChip(
                            selected = reason == r,
                            onClick = { onReasonChange(r) },
                            label = { Text(r.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Save Adjustment")
            }
        }
    }
}
