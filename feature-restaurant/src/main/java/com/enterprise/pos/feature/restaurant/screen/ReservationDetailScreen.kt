package com.enterprise.pos.feature.restaurant.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.feature.restaurant.state.ReservationDetailEvent
import com.enterprise.pos.feature.restaurant.state.ReservationDetailUiState
import com.enterprise.pos.feature.restaurant.state.ReservationDetailViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailScreen(
    reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>,
    onNavigateToEdit: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ReservationDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(reservationId) {
        viewModel.load(reservationId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReservationDetailEvent.NavigateToEdit -> onNavigateToEdit()
                is ReservationDetailEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reservation Detail", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = viewModel::navigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::edit) {
                        Icon(Icons.Default.Edit, "Edit reservation")
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
            } else if (state.reservation == null) {
                item {
                    EmptyState(
                        title = "Reservation Not Found",
                        message = "The requested reservation could not be loaded.",
                        icon = Icons.Default.EventBusy
                    )
                }
            } else {
                val reservation = state.reservation!!
                item { ReservationHeaderCard(reservation) }
                item { GuestInfoCard(reservation) }
                item { ReservationDetailsCard(reservation) }
                item { ActionButtonsCard(reservation, viewModel) }
                if (reservation.notes?.isNotBlank() == true) {
                    item { NotesCard(reservation.notes) }
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
}

@Composable
private fun ReservationHeaderCard(reservation: Reservation) {
    val statusColor = when (reservation.status) {
        ReservationStatus.REQUESTED -> MaterialTheme.colorScheme.outline
        ReservationStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        ReservationStatus.SEATED -> MaterialTheme.colorScheme.tertiary
        ReservationStatus.CANCELLED -> MaterialTheme.colorScheme.error
        ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
        ReservationStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    }
    ElevatedPosCard(
        title = reservation.customerName,
        icon = Icons.Default.Person
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        reservation.status.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                reservation.tableId?.let {
                    FilterChip(label = "Table assigned", selected = false, onClick = {})
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${reservation.partySize} guests · ${SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.US).format(Date(reservation.requestedAt))}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                SimpleDateFormat("h:mm a", Locale.US).format(Date(reservation.requestedAt)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GuestInfoCard(reservation: Reservation) {
    ElevatedPosCard(
        title = "Guest Information",
        icon = Icons.Default.ContactPhone
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Phone", reservation.phone)
            reservation.email?.let { InfoRow("Email", it) }
            if (reservation.dietaryRestrictions.isNotEmpty()) {
                InfoRow("Dietary", reservation.dietaryRestrictions.joinToString(", "))
            }
        }
    }
}

@Composable
private fun ReservationDetailsCard(reservation: Reservation) {
    ElevatedPosCard(
        title = "Reservation Details",
        icon = Icons.Default.EventNote
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoRow("Created", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(reservation.createdAt)))
            reservation.confirmedAt?.let {
                InfoRow("Confirmed", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(it)))
            }
            reservation.seatedAt?.let {
                InfoRow("Seated", SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US).format(Date(it)))
            }
            reservation.tableId?.let {
                InfoRow("Table", it.value)
            }
        }
    }
}

@Composable
private fun ActionButtonsCard(
    reservation: Reservation,
    viewModel: ReservationDetailViewModel
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
            when (reservation.status) {
                ReservationStatus.REQUESTED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = "Confirm",
                            onClick = { viewModel.confirm(reservation.id) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CheckCircle
                        )
                        DangerButton(
                            text = "Cancel",
                            onClick = { viewModel.cancel(reservation.id) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Cancel
                        )
                    }
                }
                ReservationStatus.CONFIRMED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            text = "Seat Guest",
                            onClick = {
                                reservation.tableId?.let { viewModel.seat(reservation.id, it) }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Chair
                        )
                        DangerButton(
                            text = "Cancel",
                            onClick = { viewModel.cancel(reservation.id) },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Cancel
                        )
                    }
                }
                ReservationStatus.SEATED -> {
                    PrimaryButton(
                        text = "Mark Completed",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Default.Done
                    )
                }
                else -> {
                    Text(
                        "No actions available for this status.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (reservation.status != ReservationStatus.CANCELLED && reservation.status != ReservationStatus.NO_SHOW) {
                OutlinedButton(
                    onClick = { viewModel.markNoShow(reservation.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.PersonOff, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark No-Show")
                }
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String) {
    ElevatedPosCard(
        title = "Notes",
        icon = Icons.Default.Notes
    ) {
        Text(
            notes,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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
private fun ReservationDetailScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReservationHeaderCard(
                    Reservation(
                        id = Id("res1"),
                        storeId = StoreId("s1"),
                        customerName = "Alice Johnson",
                        phone = "+1-555-987-6543",
                        email = "alice@example.com",
                        partySize = 4,
                        requestedAt = System.currentTimeMillis() + 3600000,
                        tableId = TableId("t12"),
                        status = ReservationStatus.CONFIRMED,
                        notes = "Birthday celebration, bring dessert menu",
                        createdAt = System.currentTimeMillis() - 86400000
                    )
                )
                GuestInfoCard(
                    Reservation(
                        id = Id("res1"),
                        storeId = StoreId("s1"),
                        customerName = "Alice Johnson",
                        phone = "+1-555-987-6543",
                        email = "alice@example.com",
                        partySize = 4,
                        requestedAt = System.currentTimeMillis() + 3600000,
                        status = ReservationStatus.CONFIRMED,
                        createdAt = System.currentTimeMillis() - 86400000
                    )
                )
                ReservationDetailsCard(
                    Reservation(
                        id = Id("res1"),
                        storeId = StoreId("s1"),
                        customerName = "Alice Johnson",
                        phone = "+1-555-987-6543",
                        partySize = 4,
                        requestedAt = System.currentTimeMillis() + 3600000,
                        tableId = TableId("t12"),
                        status = ReservationStatus.CONFIRMED,
                        confirmedAt = System.currentTimeMillis() - 43200000,
                        createdAt = System.currentTimeMillis() - 86400000
                    )
                )
                NotesCard("Birthday celebration, bring dessert menu")
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReservationDetailScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReservationHeaderCard(
                    Reservation(
                        id = Id("res1"),
                        storeId = StoreId("s1"),
                        customerName = "Alice Johnson",
                        phone = "+1-555-987-6543",
                        partySize = 4,
                        requestedAt = System.currentTimeMillis() + 3600000,
                        status = ReservationStatus.CONFIRMED,
                        createdAt = System.currentTimeMillis() - 86400000
                    )
                )
            }
        }
    }
}
