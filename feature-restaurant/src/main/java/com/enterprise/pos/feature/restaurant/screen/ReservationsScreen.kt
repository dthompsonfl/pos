package com.enterprise.pos.feature.restaurant.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Reservation
import com.enterprise.pos.domain.model.ReservationStatus
import com.enterprise.pos.feature.restaurant.state.ReservationsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    storeId: StoreId,
    viewModel: ReservationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId) { viewModel.load(storeId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reservations", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openNewSheet() }) {
                Icon(Icons.Filled.EventAvailable, "New reservation")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.today.isEmpty() && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.EventBusy, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("No reservations today")
                        }
                    }
                }
            }
            items(state.today, key = { it.id.value }) { r -> ReservationCard(r, onConfirm = { viewModel.confirm(r.id) }, onCancel = { viewModel.cancel(r.id, "Cancelled by staff") }) }
        }

        if (state.showNewSheet) {
            NewReservationSheet(
                onDismiss = viewModel::closeNewSheet,
                onCreate = { name, phone, party, time, notes ->
                    viewModel.createReservation(storeId, name, phone, party, time, notes, null)
                }
            )
        }
    }
}

@Composable
private fun ReservationCard(r: Reservation, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val statusColor = when (r.status) {
        ReservationStatus.REQUESTED -> MaterialTheme.colorScheme.outline
        ReservationStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        ReservationStatus.SEATED -> MaterialTheme.colorScheme.tertiary
        ReservationStatus.CANCELLED -> MaterialTheme.colorScheme.error
        ReservationStatus.NO_SHOW -> MaterialTheme.colorScheme.error
        ReservationStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.customerName, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("h:mm a", Locale.US).format(Date(r.requestedAt)) + " · ${r.partySize} guests", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                r.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
            Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                Text(r.status.name, color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (r.status == ReservationStatus.REQUESTED) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirm") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Cancel") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReservationSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, Long, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var party by remember { mutableStateOf("2") }
    var time by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New Reservation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = party, onValueChange = { party = it.filter { c -> c.isDigit() } },
                    label = { Text("Party size") }, modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                OutlinedTextField(
                    value = time, onValueChange = { time = it },
                    label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f), singleLine = true
                )
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (allergies, celebrations)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

            Button(
                onClick = {
                    val parts = time.split(":")
                    val cal = java.util.Calendar.getInstance()
                    if (parts.size == 2) {
                        cal.set(java.util.Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                        cal.set(java.util.Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                    }
                    onCreate(name, phone, party.toIntOrNull() ?: 2, cal.timeInMillis, notes.ifBlank { null })
                },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Create") }
        }
    }
}
