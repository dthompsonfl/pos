package com.enterprise.pos.feature.restaurant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.feature.restaurant.state.FloorViewModel

/** The home screen — choose how the order starts: dine-in (host seated), self-seated, to-go, etc. */
@Composable
fun FloorScreen(
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId,
    onOrderCreated: (orderId: String, diningMode: DiningMode) -> Unit,
    viewModel: FloorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val startState by viewModel.startState.collectAsStateWithLifecycle()

    LaunchedEffect(storeId) { viewModel.loadTables(storeId) }
    LaunchedEffect(startState.order?.id) {
        startState.order?.let { o -> onOrderCreated(o.id.value, startState.diningMode) }
    }

    var showStartOrderSheet by remember { mutableStateOf<StartOrderMode?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("What kind of order?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        DiningModeGrid(
            onPick = { mode ->
                when (mode) {
                    StartOrderMode.DINE_IN_HOST -> {
                        // show floor map for table selection
                        showStartOrderSheet = mode
                    }
                    StartOrderMode.DINE_IN_SELF -> {
                        viewModel.startDineInSelfSeated(storeId, registerId, employeeId)
                    }
                    StartOrderMode.TO_GO -> {
                        viewModel.startTakeout(storeId, registerId, employeeId)
                    }
                    StartOrderMode.DELIVERY -> {
                        // Delivery launched from a separate screen
                    }
                    StartOrderMode.RETAIL -> {
                        viewModel.startTakeout(storeId, registerId, employeeId) // reuses flow without table
                    }
                }
            }
        )

        Spacer(Modifier.height(24.dp))
        Text("Floor Map", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (state.tables.isEmpty() && !state.isLoading) {
            EmptyFloorState()
        } else {
            val sections = state.tables.map { it.section }.distinct()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sectionFilter == null,
                    onClick = { viewModel.selectSection(null) },
                    label = { Text("All") }
                )
                sections.forEach { s ->
                    FilterChip(
                        selected = state.sectionFilter == s,
                        onClick = { viewModel.selectSection(s) },
                        label = { Text(s) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.tables, key = { it.id.value }) { table ->
                    TableCard(
                        table = table,
                        onClick = {
                            viewModel.selectTable(table)
                            showStartOrderSheet = StartOrderMode.DINE_IN_HOST
                        }
                    )
                }
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(err, modifier = Modifier.padding(12.dp))
            }
        }
    }

    showStartOrderSheet?.let { mode ->
        StartDineInSheet(
            mode = mode,
            selectedTable = state.selectedTable,
            guestCount = startState.guestCount,
            onDismiss = {
                showStartOrderSheet = null
                viewModel.clearSelection()
            },
            onConfirm = { table, guests ->
                viewModel.startDineInHostSeated(table, employeeId, storeId, registerId, guests)
                showStartOrderSheet = null
            },
            onTakeout = {
                viewModel.startTakeout(storeId, registerId, employeeId)
                showStartOrderSheet = null
            },
            onSelfSeated = {
                viewModel.startDineInSelfSeated(storeId, registerId, employeeId)
                showStartOrderSheet = null
            }
        )
    }
}

private enum class StartOrderMode { DINE_IN_HOST, DINE_IN_SELF, TO_GO, DELIVERY, RETAIL }

private val TableStatus.floorColor: Color
    get() = when (this) {
        TableStatus.AVAILABLE -> Color(0xFF4CAF50)
        TableStatus.SEATED -> Color(0xFF2196F3)
        TableStatus.ORDERED -> Color(0xFFFF9800)
        TableStatus.DINING -> Color(0xFF9C27B0)
        TableStatus.BILL_REQUESTED -> Color(0xFFF44336)
        TableStatus.CLEANING -> Color(0xFF607D8B)
        TableStatus.RESERVED -> Color(0xFF00BCD4)
    }

@Composable
private fun DiningModeGrid(onPick: (StartOrderMode) -> Unit) {
    val items = listOf(
        StartOrderMode.DINE_IN_HOST to ("Dine-In" to Icons.Filled.Restaurant),
        StartOrderMode.DINE_IN_SELF to ("Self-Seated" to Icons.Filled.Storefront),
        StartOrderMode.TO_GO to ("To-Go" to Icons.Filled.ShoppingBag),
        StartOrderMode.DELIVERY to ("Delivery" to Icons.Filled.DeliveryDining),
        StartOrderMode.RETAIL to ("Retail" to Icons.Filled.PointOfSale)
    )
    Column {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (mode, pair) ->
                    val (label, icon) = pair
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clickable { onPick(mode) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.height(4.dp))
                            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TableCard(table: RestaurantTable, onClick: () -> Unit) {
    val cardColor = table.status.floorColor
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = cardColor.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(table.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Seats ${table.capacity}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            val statusText = when (table.status) {
                TableStatus.AVAILABLE -> "Available"
                TableStatus.SEATED -> "Seated · ${table.currentGuestCount} guests"
                TableStatus.ORDERED -> "Order placed"
                TableStatus.DINING -> "Dining"
                TableStatus.BILL_REQUESTED -> "Bill requested"
                TableStatus.CLEANING -> "Cleaning"
                TableStatus.RESERVED -> "Reserved"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium, color = cardColor)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = cardColor,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    table.section,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EmptyFloorState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.TableRestaurant, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            Text("No tables configured", style = MaterialTheme.typography.bodyLarge)
            Text("Seed data will create demo tables on first launch.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDineInSheet(
    mode: StartOrderMode,
    selectedTable: RestaurantTable?,
    guestCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (RestaurantTable, Int) -> Unit,
    onTakeout: () -> Unit,
    onSelfSeated: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            if (mode == StartOrderMode.DINE_IN_HOST && selectedTable != null) {
                Text("Seat ${selectedTable.name}", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                Text("How many guests?", style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1, 2, 3, 4, 5, 6, 7, 8).forEach { n ->
                        FilterChip(
                            selected = guestCount == n,
                            onClick = { /* update guest count via VM */ },
                            label = { Text(n.toString()) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onConfirm(selectedTable, guestCount) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Order")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
