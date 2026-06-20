package com.enterprise.pos.feature.shifts.screen

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.feature.shifts.state.ShiftsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsScreen(
    storeId: StoreId,
    registerId: RegisterId,
    employeeId: EmployeeId,
    viewModel: ShiftsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(storeId, registerId, employeeId) { viewModel.load(storeId, registerId, employeeId) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Shift & Register", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Current shift card
            state.currentShift?.let { shift ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LockClock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("Shift Open", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Started: ${SimpleDateFormat("h:mm a", Locale.US).format(Date(shift.startedAt))}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Starting cash: ${shift.startingCash.format()}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.openCloseSheet() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.Logout, null); Spacer(Modifier.width(8.dp)); Text("Close Shift & Print Z-Report")
                        }
                    }
                }
            } ?: run {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("No shift open", style = MaterialTheme.typography.titleMedium)
                        Text("Open a shift to start accepting payments.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.openOpenSheet() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Open Shift")
                        }
                    }
                }
            }

            // Last Z-Report preview
            state.lastZReport?.let { z ->
                Spacer(Modifier.height(8.dp))
                Text("Last Z-Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ZReportCard(z)
            }

            // Tip pool
            state.tipPool?.let { pool ->
                Spacer(Modifier.height(8.dp))
                Text("Tip Pool Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                TipPoolCard(pool)
            }

            // Open shifts across registers
            if (state.openShifts.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text("All Open Shifts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                state.openShifts.forEach { s ->
                    ListItem(
                        headlineContent = { Text("Register ${s.registerId.value}") },
                        supportingContent = { Text("Employee ${s.employeeId.value.takeLast(6)} · Started ${SimpleDateFormat("h:mm a", Locale.US).format(Date(s.startedAt))}") },
                        leadingContent = { Icon(Icons.Filled.Person, null) },
                        trailingContent = { Text(s.startingCash.format(), fontWeight = FontWeight.SemiBold) }
                    )
                    HorizontalDivider()
                }
            }
        }

        if (state.showOpenSheet) {
            OpenShiftSheet(
                startingCash = state.startingCash,
                onStartingCashChange = viewModel::setStartingCash,
                onDismiss = viewModel::closeOpenSheet,
                onConfirm = { viewModel.startShift(storeId, registerId, employeeId) }
            )
        }
        if (state.showCloseSheet) {
            CloseShiftSheet(
                countedCash = state.countedCash,
                notes = state.closeNotes,
                onCountedChange = viewModel::setCountedCash,
                onNotesChange = viewModel::setCloseNotes,
                onDismiss = viewModel::closeCloseSheet,
                onConfirm = { viewModel.closeShift() }
            )
        }
    }
}

@Composable
private fun ZReportCard(z: com.enterprise.pos.domain.model.ZReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Receipt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Z-Report ${SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(z.generatedAt))}", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            ZReportRow("Gross Sales", z.grossSales)
            ZReportRow("Returns", z.returns)
            ZReportRow("Discounts", z.discounts)
            ZReportRow("Net Sales", z.netSales)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            z.taxCollected.forEach { (name, amt) -> ZReportRow(name, amt) }
            ZReportRow("Tips", z.tips)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ZReportRow("Cash", z.cashTotal)
            ZReportRow("Card", z.cardTotal)
            ZReportRow("Over/Short", z.overShort)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Transactions", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(z.transactionCount.toString(), fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth()) {
                Text("Voids", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Text(z.voidCount.toString(), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ZReportRow(label: String, value: Money) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value.format(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TipPoolCard(pool: com.enterprise.pos.domain.model.TipPoolSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Pool Type: ${pool.poolType.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(8.dp))
            ZReportRow("Total Tips", pool.totalTips)
            ZReportRow("Total Hours", Money.of(pool.totalHours)) // hacky, but readable
            Spacer(Modifier.height(8.dp))
            pool.entries.forEach { e ->
                ListItem(
                    headlineContent = { Text("Employee ${e.employeeId.value.takeLast(6)}") },
                    supportingContent = { Text("${"%.1f".format(e.hoursWorked)}h worked") },
                    trailingContent = { Text(e.totalTakeHome.format(), fontWeight = FontWeight.Bold) }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenShiftSheet(
    startingCash: String,
    onStartingCashChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Open Shift", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text("Count the cash drawer and enter the starting amount:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = startingCash,
                onValueChange = onStartingCashChange,
                label = { Text("Starting cash") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Open Shift")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloseShiftSheet(
    countedCash: String,
    notes: String,
    onCountedChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Close Shift", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text("Count the cash drawer and enter the actual amount:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = countedCash,
                onValueChange = onCountedChange,
                label = { Text("Counted cash") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Close Shift & Generate Z-Report")
            }
        }
    }
}
