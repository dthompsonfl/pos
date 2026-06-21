package com.enterprise.pos.feature.employees.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.feature.employees.state.EmployeeDetailEvent
import com.enterprise.pos.feature.employees.state.EmployeeDetailViewModel
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailScreen(
    employeeId: EmployeeId,
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeactivated: () -> Unit,
    viewModel: EmployeeDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPinResetDialog by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    LaunchedEffect(employeeId) {
        viewModel.load(employeeId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EmployeeDetailEvent.Deactivated -> onDeactivated()
                is EmployeeDetailEvent.PinReset -> {
                    showPinResetDialog = false
                    newPin = ""
                }
                is EmployeeDetailEvent.Error -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.employee?.name ?: "Employee", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                }
            )
        }
    ) { padding ->
        val employee = state.employee
        if (employee == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text(state.error ?: "Not found")
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    employee.name.take(1),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                employee.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                employee.role.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (!employee.active) {
                                Text(
                                    "Inactive",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        InfoRow("Email", employee.email ?: "—")
                        InfoRow("Phone", employee.phone ?: "—")
                        InfoRow("Hourly Rate", if (employee.hourlyRate.isZero()) "—" else employee.hourlyRate.format())
                        employee.hireDate?.let {
                            InfoRow("Hire Date", java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(it)))
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val perms = listOf(
                            "Process Refunds" to employee.customPermissions.contains("process_refunds"),
                            "Apply Discounts" to employee.customPermissions.contains("apply_discounts"),
                            "Void Orders" to employee.customPermissions.contains("void_orders"),
                            "Open Drawer" to employee.customPermissions.contains("open_drawer"),
                            "Manage Employees" to employee.customPermissions.contains("manage_employees"),
                            "View Reports" to employee.customPermissions.contains("view_reports"),
                            "Manage Inventory" to employee.customPermissions.contains("manage_inventory"),
                            "Comp Items" to employee.customPermissions.contains("comp_items")
                        )
                        perms.forEach { (name, enabled) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (enabled) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                    null,
                                    tint = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Performance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        InfoRow("Total Sales", state.stats.totalSales.format())
                        InfoRow("Orders", state.stats.orderCount.toString())
                        InfoRow("Avg Order", state.stats.averageOrderValue.format())
                        InfoRow("Hours Worked", state.stats.hoursWorked.toString())
                    }
                }
            }

            val notes = employee.notes
            if (notes != null && notes.isNotBlank()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (isAdmin) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showPinResetDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.LockReset, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset PIN")
                        }
                        OutlinedButton(
                            onClick = { showDeactivateDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Filled.PersonRemove, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Deactivate")
                        }
                    }
                }
            }
        }
    }

    if (showPinResetDialog) {
        AlertDialog(
            onDismissRequest = { showPinResetDialog = false },
            title = { Text("Reset PIN") },
            text = {
                Column {
                    Text("Enter new 4-6 digit PIN for ${state.employee?.name ?: ""}")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("New PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resetPin(employeeId, newPin) },
                    enabled = newPin.length in 4..6
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showPinResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = { Text("Deactivate Employee") },
            text = { Text("This will deactivate ${state.employee?.name ?: ""}. They will no longer be able to log in. This action can be reversed by reactivating the employee.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deactivate(employeeId); showDeactivateDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Deactivate") }
            },
            dismissButton = {
                TextButton(onClick = { showDeactivateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true)
@Composable
private fun EmployeeDetailScreenPreview() {
    PosTheme {
        Surface {
            EmployeeDetailScreen(
                employeeId = EmployeeId("preview-id"),
                isAdmin = true,
                onBack = {},
                onEdit = {},
                onDeactivated = {}
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmployeeDetailScreenDarkPreview() {
    PosTheme {
        Surface {
            EmployeeDetailScreen(
                employeeId = EmployeeId("preview-id"),
                isAdmin = true,
                onBack = {},
                onEdit = {},
                onDeactivated = {}
            )
        }
    }
}
