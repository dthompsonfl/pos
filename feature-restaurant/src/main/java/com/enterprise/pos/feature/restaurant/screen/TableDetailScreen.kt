package com.enterprise.pos.feature.restaurant.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import com.enterprise.pos.feature.restaurant.state.TableDetailEvent
import com.enterprise.pos.feature.restaurant.state.TableDetailUiState
import com.enterprise.pos.feature.restaurant.state.TableDetailViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDetailScreen(
    tableId: TableId,
    storeId: StoreId,
    onNavigateToOrder: (OrderId) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: TableDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(tableId, storeId) {
        viewModel.load(tableId, storeId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TableDetailEvent.NavigateToOrder -> onNavigateToOrder(event.orderId)
                is TableDetailEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Table Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            } else if (state.table == null) {
                item {
                    EmptyState(
                        title = "Table Not Found",
                        message = "The requested table could not be loaded.",
                        icon = Icons.Default.TableRestaurant
                    )
                }
            } else {
                val table = state.table!!
                item { TableHeaderCard(table) }
                item { StatusActionsCard(table, viewModel, state) }
                if (state.currentOrder != null) {
                    item { CurrentOrderCard(state.currentOrder!!, viewModel) }
                }
                if (state.reservations.isNotEmpty()) {
                    item {
                        Text(
                            "Reservations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(state.reservations, key = { it.id.value }) { reservation ->
                        ReservationMiniCard(reservation)
                    }
                }
            }
        }
    }

    if (state.showStatusDialog) {
        StatusSelectionDialog(
            currentStatus = state.table?.status ?: TableStatus.AVAILABLE,
            onSelect = { status ->
                state.table?.let { viewModel.changeStatus(it.id, status) }
            },
            onDismiss = viewModel::closeStatusDialog
        )
    }
}

@Composable
private fun TableHeaderCard(table: RestaurantTable) {
    val statusColor = table.status.color
    ElevatedPosCard(
        title = table.name,
        subtitle = "Section: ${table.section} · Capacity: ${table.capacity}",
        icon = Icons.Default.TableRestaurant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusBadge(
                    status = when (table.status) {
                        TableStatus.AVAILABLE -> StatusType.ACTIVE
                        TableStatus.SEATED -> StatusType.PENDING
                        TableStatus.ORDERED -> StatusType.PENDING
                        TableStatus.DINING -> StatusType.PENDING
                        TableStatus.BILL_REQUESTED -> StatusType.ERROR
                        TableStatus.CLEANING -> StatusType.INACTIVE
                        TableStatus.RESERVED -> StatusType.PENDING
                    },
                    label = table.status.name.replace('_', ' ').lowercase()
                        .replaceFirstChar { it.titlecase() }
                )
                table.serverId?.let {
                    FilterChip(
                        label = "Server assigned",
                        selected = false,
                        onClick = {}
                    )
                }
            }
            if (table.currentGuestCount > 0) {
                Text(
                    "${table.currentGuestCount} guests currently seated",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusActionsCard(
    table: RestaurantTable,
    viewModel: TableDetailViewModel,
    state: TableDetailUiState
) {
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
                    text = "Change Status",
                    onClick = viewModel::openStatusDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit
                )
                SecondaryButton(
                    text = "Assign Server",
                    onClick = viewModel::openServerDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Person
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Combine Table",
                    onClick = viewModel::openCombineDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Merge
                )
                SecondaryButton(
                    text = "Split Check",
                    onClick = viewModel::openSplitDialog,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.CallSplit
                )
            }
            if (state.currentOrder != null) {
                PrimaryButton(
                    text = "View Order",
                    onClick = { viewModel.viewOrder(state.currentOrder!!.id) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Receipt
                )
            }
        }
    }
}

@Composable
private fun CurrentOrderCard(order: Order, viewModel: TableDetailViewModel) {
    ElevatedPosCard(
        title = "Current Order",
        subtitle = "#${order.id.value.take(8)}",
        icon = Icons.Default.Receipt
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Status: ${order.status.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    order.grandTotal.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${order.lines.size} items · ${order.guestCount} guests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                text = "Open Order",
                onClick = { viewModel.viewOrder(order.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReservationMiniCard(reservation: Reservation) {
    val statusColor = when (reservation.status) {
        ReservationStatus.REQUESTED -> MaterialTheme.colorScheme.outline
        ReservationStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        ReservationStatus.SEATED -> MaterialTheme.colorScheme.tertiary
        ReservationStatus.CANCELLED -> MaterialTheme.colorScheme.error
        ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
        ReservationStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
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
                    reservation.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${reservation.partySize} guests · ${SimpleDateFormat("h:mm a", Locale.US).format(Date(reservation.requestedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    reservation.status.name,
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
private fun StatusSelectionDialog(
    currentStatus: TableStatus,
    onSelect: (TableStatus) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TableStatus.entries.forEach { status ->
                    val color = status.color
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(status) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                                Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(color, MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            status.name.replace('_', ' ').lowercase()
                                .replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (status == currentStatus) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val TableStatus.color: Color
    get() = when (this) {
        TableStatus.AVAILABLE -> Color(0xFF4CAF50)
        TableStatus.SEATED -> Color(0xFFFF9800)
        TableStatus.ORDERED -> Color(0xFFFF9800)
        TableStatus.DINING -> Color(0xFFFF9800)
        TableStatus.BILL_REQUESTED -> Color(0xFFF44336)
        TableStatus.CLEANING -> Color(0xFF607D8B)
        TableStatus.RESERVED -> Color(0xFF2196F3)
    }

@Preview(showBackground = true)
@Composable
private fun TableDetailScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TableHeaderCard(
                    RestaurantTable(
                        id = TableId("t1"),
                        storeId = StoreId("s1"),
                        name = "Table 12",
                        section = "Patio",
                        capacity = 4,
                        status = TableStatus.SEATED,
                        currentGuestCount = 3
                    )
                )
                StatusActionsCard(
                    RestaurantTable(
                        id = TableId("t1"),
                        storeId = StoreId("s1"),
                        name = "Table 12",
                        section = "Patio",
                        capacity = 4,
                        status = TableStatus.SEATED,
                        currentGuestCount = 3
                    ),
                    viewModel = TableDetailViewModel(
                        orderRepo = object : com.enterprise.pos.domain.repository.OrderRepository {
                            override fun observeOpenOrders(storeId: StoreId) = kotlinx.coroutines.flow.emptyFlow<List<Order>>()
                            override fun observeOrder(orderId: OrderId) = kotlinx.coroutines.flow.emptyFlow<Order?>()
                            override fun observeOrdersByTable(tableId: TableId) = kotlinx.coroutines.flow.emptyFlow<List<Order>>()
                            override fun observeTables(storeId: StoreId) = kotlinx.coroutines.flow.emptyFlow<List<RestaurantTable>>()
                            override suspend fun createOrder(storeId: StoreId, registerId: com.enterprise.pos.core.RegisterId, employeeId: EmployeeId, diningMode: com.enterprise.pos.domain.model.DiningMode, tableId: TableId?, guestCount: Int) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun updateOrder(order: Order) = com.enterprise.pos.core.Result.success(order)
                            override suspend fun setStatus(orderId: OrderId, status: OrderStatus) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = orderId, storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun assignTable(orderId: OrderId, tableId: TableId?) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun assignServer(tableId: TableId, serverId: EmployeeId?) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun setTableStatus(tableId: TableId, status: TableStatus) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun getById(orderId: OrderId) = com.enterprise.pos.core.Result.success<Order?>(null)
                            override suspend fun closeOrder(orderId: OrderId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun recentOrders(storeId: StoreId, limit: Int) = com.enterprise.pos.core.Result.success(emptyList<Order>())
                            override suspend fun markPaid(orderId: OrderId, payment: com.enterprise.pos.domain.model.Payment, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun refund(orderId: OrderId, refund: com.enterprise.pos.domain.model.Payment, reason: String, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun voidOrder(orderId: OrderId, reason: String, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                        },
                        reservationRepo = object : com.enterprise.pos.domain.repository.ReservationRepository {
                            override fun observeReservations(storeId: StoreId, date: Long) = kotlinx.coroutines.flow.emptyFlow<List<Reservation>>()
                            override fun observeUpcoming(storeId: StoreId, hours: Int) = kotlinx.coroutines.flow.emptyFlow<List<Reservation>>()
                            override suspend fun get(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>) = com.enterprise.pos.core.Result.success<Reservation?>(null)
                            override suspend fun upsert(r: Reservation) = com.enterprise.pos.core.Result.success(r)
                            override suspend fun setStatus(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, status: ReservationStatus) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun seat(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: TableId) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun cancel(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, reason: String) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun checkTableAvailability(storeId: StoreId, date: Long, partySize: Int) = com.enterprise.pos.core.Result.success(emptyList<RestaurantTable>())
                        }
                    )
                , state = TableDetailUiState())
                CurrentOrderCard(
                    Order(
                        id = OrderId("o1"),
                        storeId = StoreId("s1"),
                        registerId = com.enterprise.pos.core.RegisterId("r1"),
                        employeeId = EmployeeId("e1"),
                        status = OrderStatus.OPEN,
                        guestCount = 3,
                        lines = emptyList()
                    ),
                    viewModel = TableDetailViewModel(
                        orderRepo = object : com.enterprise.pos.domain.repository.OrderRepository {
                            override fun observeOpenOrders(storeId: StoreId) = kotlinx.coroutines.flow.emptyFlow<List<Order>>()
                            override fun observeOrder(orderId: OrderId) = kotlinx.coroutines.flow.emptyFlow<Order?>()
                            override fun observeOrdersByTable(tableId: TableId) = kotlinx.coroutines.flow.emptyFlow<List<Order>>()
                            override fun observeTables(storeId: StoreId) = kotlinx.coroutines.flow.emptyFlow<List<RestaurantTable>>()
                            override suspend fun createOrder(storeId: StoreId, registerId: com.enterprise.pos.core.RegisterId, employeeId: EmployeeId, diningMode: com.enterprise.pos.domain.model.DiningMode, tableId: TableId?, guestCount: Int) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun updateOrder(order: Order) = com.enterprise.pos.core.Result.success(order)
                            override suspend fun setStatus(orderId: OrderId, status: OrderStatus) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = orderId, storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun assignTable(orderId: OrderId, tableId: TableId?) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun assignServer(tableId: TableId, serverId: EmployeeId?) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun setTableStatus(tableId: TableId, status: TableStatus) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun getById(orderId: OrderId) = com.enterprise.pos.core.Result.success<Order?>(null)
                            override suspend fun closeOrder(orderId: OrderId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun recentOrders(storeId: StoreId, limit: Int) = com.enterprise.pos.core.Result.success(emptyList<Order>())
                            override suspend fun markPaid(orderId: OrderId, payment: com.enterprise.pos.domain.model.Payment, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun refund(orderId: OrderId, refund: com.enterprise.pos.domain.model.Payment, reason: String, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                            override suspend fun voidOrder(orderId: OrderId, reason: String, employeeId: EmployeeId) = com.enterprise.pos.core.Result.success(com.enterprise.pos.domain.model.Order(id = OrderId(""), storeId = StoreId(""), registerId = com.enterprise.pos.core.RegisterId(""), employeeId = EmployeeId("")))
                        },
                        reservationRepo = object : com.enterprise.pos.domain.repository.ReservationRepository {
                            override fun observeReservations(storeId: StoreId, date: Long) = kotlinx.coroutines.flow.emptyFlow<List<Reservation>>()
                            override fun observeUpcoming(storeId: StoreId, hours: Int) = kotlinx.coroutines.flow.emptyFlow<List<Reservation>>()
                            override suspend fun get(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>) = com.enterprise.pos.core.Result.success<Reservation?>(null)
                            override suspend fun upsert(r: Reservation) = com.enterprise.pos.core.Result.success(r)
                            override suspend fun setStatus(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, status: ReservationStatus) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun seat(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, tableId: TableId) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun cancel(id: com.enterprise.pos.core.Id<com.enterprise.pos.domain.model.ReservationTag>, reason: String) = com.enterprise.pos.core.Result.success(Unit)
                            override suspend fun checkTableAvailability(storeId: StoreId, date: Long, partySize: Int) = com.enterprise.pos.core.Result.success(emptyList<RestaurantTable>())
                        }
                    )
                )
                ReservationMiniCard(
                    Reservation(
                        id = com.enterprise.pos.core.Id("res1"),
                        storeId = StoreId("s1"),
                        customerName = "Alice Johnson",
                        phone = "+1-555-987-6543",
                        partySize = 4,
                        requestedAt = System.currentTimeMillis() + 3600000,
                        status = ReservationStatus.CONFIRMED
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TableDetailScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TableHeaderCard(
                    RestaurantTable(
                        id = TableId("t1"),
                        storeId = StoreId("s1"),
                        name = "Table 12",
                        section = "Patio",
                        capacity = 4,
                        status = TableStatus.SEATED,
                        currentGuestCount = 3
                    )
                )
            }
        }
    }
}
