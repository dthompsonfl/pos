package com.enterprise.pos.feature.employees.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.domain.model.Employee
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.feature.employees.state.EmployeesManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesManagementScreen(viewModel: EmployeesManagementViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Employees", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) {
                Icon(Icons.Filled.PersonAdd, "Add employee")
            }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(state.employees, key = { it.id.value }) { emp ->
                ListItem(
                    leadingContent = {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emp.name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    },
                    headlineContent = { Text(emp.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column {
                            Text(emp.role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.bodySmall)
                            emp.email?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, null) },
                    modifier = Modifier.padding(vertical = 2.dp).clickable {
                        viewModel.select(emp); showEditor = true
                    }
                )
                HorizontalDivider()
            }
        }

        if (showEditor) {
            EmployeeEditorSheet(
                employee = state.selected,
                onDismiss = { showEditor = false; viewModel.select(null) },
                onSubmit = { name, pin, role, email, phone ->
                    viewModel.upsert(name, pin, role, email, phone)
                    showEditor = false
                },
                onDeactivate = state.selected?.let { { viewModel.deactivate(it.id) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmployeeEditorSheet(
    employee: Employee?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, EmployeeRole, String?, String?) -> Unit,
    onDeactivate: (() -> Unit)?
) {
    var name by remember { mutableStateOf(employee?.name ?: "") }
    var pin by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(employee?.role ?: EmployeeRole.CASHIER) }
    var email by remember { mutableStateOf(employee?.email ?: "") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (employee == null) "Add Employee" else "Edit Employee", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text(if (employee == null) "PIN (4-6 digits)" else "New PIN (leave blank to keep current)") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            Text("Role", style = MaterialTheme.typography.titleSmall)
            EmployeeRole.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { r ->
                        FilterChip(
                            selected = role == r,
                            onClick = { role = r },
                            label = { Text(r.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size < 3) repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            Button(
                onClick = { onSubmit(name, pin, role, email.ifBlank { null }, phone.ifBlank { null }) },
                enabled = name.isNotBlank() && (employee != null || pin.length >= 4),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Save") }

            onDeactivate?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Filled.PersonRemove, null); Spacer(Modifier.width(8.dp)); Text("Deactivate")
                }
            }
        }
    }
}
