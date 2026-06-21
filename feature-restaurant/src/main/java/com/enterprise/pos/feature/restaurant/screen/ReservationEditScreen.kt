@file:OptIn(ExperimentalLayoutApi::class)

package com.enterprise.pos.feature.restaurant.screen

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
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.feature.restaurant.state.ReservationEditEvent
import com.enterprise.pos.feature.restaurant.state.ReservationEditUiState
import com.enterprise.pos.feature.restaurant.state.ReservationEditViewModel
import com.enterprise.pos.feature.restaurant.state.ReservationForm
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationEditScreen(
    reservationId: Id<com.enterprise.pos.domain.model.ReservationTag>? = null,
    storeId: StoreId,
    onSaved: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ReservationEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(reservationId, storeId) {
        if (reservationId != null) {
            viewModel.loadExisting(reservationId, storeId)
        } else {
            viewModel.loadNew(storeId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ReservationEditEvent.Saved -> onSaved()
                is ReservationEditEvent.Back -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (reservationId == null) "New Reservation" else "Edit Reservation", fontWeight = FontWeight.Bold) },
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
            if (state.isLoading) {
                LoadingState(modifier = Modifier.fillMaxWidth().height(200.dp))
            } else {
                FormSection(title = "Guest Information") {
                    GuestInfoForm(state, viewModel)
                }
                FormSection(title = "Reservation Details") {
                    ReservationDetailsForm(state, viewModel)
                }
                FormSection(title = "Table Assignment") {
                    TableAssignmentForm(state, viewModel)
                }
                FormSection(title = "Additional Information") {
                    AdditionalInfoForm(state, viewModel)
                }
                FormSection(title = "Status") {
                    StatusSelector(state, viewModel)
                }
                ActionButtons(state, viewModel, storeId)
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
private fun GuestInfoForm(state: ReservationEditUiState, viewModel: ReservationEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosTextField(
            value = state.form.guestName,
            onValueChange = viewModel::setGuestName,
            label = "Guest Name",
            isError = state.validationErrors.containsKey("name"),
            errorText = state.validationErrors["name"] ?: "",
            leadingIcon = Icons.Default.Person
        )
        PosTextField(
            value = state.form.phone,
            onValueChange = viewModel::setPhone,
            label = "Phone Number",
            isError = state.validationErrors.containsKey("phone"),
            errorText = state.validationErrors["phone"] ?: "",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            leadingIcon = Icons.Default.Phone
        )
        PosTextField(
            value = state.form.email,
            onValueChange = viewModel::setEmail,
            label = "Email (optional)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = Icons.Default.Email
        )
    }
}

@Composable
private fun ReservationDetailsForm(state: ReservationEditUiState, viewModel: ReservationEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosTextField(
                value = state.form.partySize,
                onValueChange = viewModel::setPartySize,
                label = "Party Size",
                isError = state.validationErrors.containsKey("partySize"),
                errorText = state.validationErrors["partySize"] ?: "",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Default.Groups
            )
            PosTextField(
                value = state.form.time,
                onValueChange = viewModel::setTime,
                label = "Time (HH:mm)",
                isError = state.validationErrors.containsKey("time"),
                errorText = state.validationErrors["time"] ?: "",
                modifier = Modifier.weight(1f),
                leadingIcon = Icons.Default.Schedule
            )
        }
        PosDateTimeField(
            value = Date(state.form.date),
            onValueChange = { viewModel.setDate(it.time) },
            label = "Date"
        )
        if (state.validationErrors.containsKey("date")) {
            Text(
                state.validationErrors["date"] ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TableAssignmentForm(state: ReservationEditUiState, viewModel: ReservationEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PosDropdownField(
            selectedItem = state.tables.find { it.id == state.form.tableId },
            items = state.availableTables,
            onItemSelected = { viewModel.setTable(it) },
            label = "Table (optional)",
            itemText = { "${it.name} · ${it.section} · Seats ${it.capacity}" }
        )
        if (state.availableTables.isNotEmpty()) {
            Text(
                "Available tables for ${state.form.partySize} guests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableTables.forEach { table ->
                    val selected = state.form.tableId == table.id
                    FilterChip(
                        label = "${table.name} (${table.capacity})",
                        selected = selected,
                        onClick = { viewModel.setTable(if (selected) null else table) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdditionalInfoForm(state: ReservationEditUiState, viewModel: ReservationEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosTextField(
            value = state.form.specialRequests,
            onValueChange = viewModel::setSpecialRequests,
            label = "Special Requests / Dietary Restrictions",
            singleLine = false,
            leadingIcon = Icons.Default.Restaurant
        )
        PosTextField(
            value = state.form.notes,
            onValueChange = viewModel::setNotes,
            label = "Internal Notes",
            singleLine = false,
            leadingIcon = Icons.Default.Notes
        )
    }
}

@Composable
private fun StatusSelector(state: ReservationEditUiState, viewModel: ReservationEditViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ReservationStatus.entries.forEach { status ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.form.status == status,
                    onClick = { viewModel.setStatus(status) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    status.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(state: ReservationEditUiState, viewModel: ReservationEditViewModel, storeId: StoreId) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton(
            text = if (state.reservationId == null) "Create Reservation" else "Update Reservation",
            onClick = { viewModel.save(storeId) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            icon = Icons.Default.Save,
            enabled = !state.isSaving
        )
        if (state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReservationEditScreenPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormSection(title = "Guest Information") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(
                            value = "Alice Johnson",
                            onValueChange = {},
                            label = "Guest Name"
                        )
                        PosTextField(
                            value = "+1-555-987-6543",
                            onValueChange = {},
                            label = "Phone Number"
                        )
                        PosTextField(
                            value = "alice@example.com",
                            onValueChange = {},
                            label = "Email (optional)"
                        )
                    }
                }
                FormSection(title = "Reservation Details") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PosTextField(
                                value = "4",
                                onValueChange = {},
                                label = "Party Size",
                                modifier = Modifier.weight(1f)
                            )
                            PosTextField(
                                value = "19:00",
                                onValueChange = {},
                                label = "Time (HH:mm)",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                FormSection(title = "Table Assignment") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(label = "Table 12 (4)", selected = true, onClick = {})
                        FilterChip(label = "Table 14 (4)", selected = false, onClick = {})
                        FilterChip(label = "Table 15 (6)", selected = false, onClick = {})
                    }
                }
                FormSection(title = "Status") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReservationStatus.entries.forEach { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = status == ReservationStatus.CONFIRMED,
                                    onClick = {}
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    status.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReservationEditScreenDarkPreview() {
    PosTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FormSection(title = "Guest Information") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(
                            value = "Alice Johnson",
                            onValueChange = {},
                            label = "Guest Name"
                        )
                    }
                }
            }
        }
    }
}
