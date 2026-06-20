package com.enterprise.pos.feature.employees.screen

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.feature.employees.state.EmployeeEditEvent
import com.enterprise.pos.feature.employees.state.EmployeeEditViewModel
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeEditScreen(
    employeeId: String? = null,
    isAdmin: Boolean = false,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EmployeeEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(employeeId) {
        employeeId?.let { viewModel.loadEmployee(com.enterprise.pos.core.EmployeeId(it)) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is EmployeeEditEvent.Saved -> onSaved()
                is EmployeeEditEvent.Error -> {}
                is EmployeeEditEvent.ValidationFailed -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (employeeId == null) "New Employee" else "Edit Employee",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(isAdmin) },
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Filled.Save, "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle("Contact Information")

            OutlinedTextField(
                value = state.form.firstName,
                onValueChange = { value -> viewModel.updateForm { it.copy(firstName = value) } },
                label = { Text("First Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.errors.containsKey("firstName"),
                supportingText = { state.errors["firstName"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.lastName,
                onValueChange = { value -> viewModel.updateForm { it.copy(lastName = value) } },
                label = { Text("Last Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.errors.containsKey("lastName"),
                supportingText = { state.errors["lastName"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.email,
                onValueChange = { value -> viewModel.updateForm { it.copy(email = value) } },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                isError = state.errors.containsKey("email"),
                supportingText = { state.errors["email"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            OutlinedTextField(
                value = state.form.phone,
                onValueChange = { value -> viewModel.updateForm { it.copy(phone = value) } },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
            )

            SectionTitle("Role & Security")

            var roleExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.form.role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = roleExpanded,
                    onDismissRequest = { roleExpanded = false }
                ) {
                    EmployeeRole.entries.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) },
                            onClick = {
                                viewModel.updateForm { it.copy(role = role) }
                                roleExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.form.pin,
                onValueChange = { value ->
                    viewModel.updateForm { it.copy(pin = value.filter { c -> c.isDigit() }.take(6)) }
                },
                label = { Text(if (employeeId == null) "PIN * (4-6 digits)" else "New PIN (leave blank to keep)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                isError = state.errors.containsKey("pin"),
                supportingText = { state.errors["pin"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            if (isAdmin) {
                SectionTitle("Admin Fields")

                OutlinedTextField(
                    value = state.form.hourlyRate,
                    onValueChange = { value -> viewModel.updateForm { it.copy(hourlyRate = value) } },
                    label = { Text("Hourly Rate") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )

                OutlinedTextField(
                    value = state.form.hireDate,
                    onValueChange = { value -> viewModel.updateForm { it.copy(hireDate = value) } },
                    label = { Text("Hire Date (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.form.active,
                        onCheckedChange = { checked ->
                            viewModel.updateForm { it.copy(active = checked) }
                        }
                    )
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                }

                SectionTitle("Custom Permissions")
                val allPerms = remember {
                    listOf(
                        "process_refunds" to "Process Refunds",
                        "apply_discounts" to "Apply Discounts",
                        "void_orders" to "Void Orders",
                        "open_drawer" to "Open Drawer",
                        "manage_employees" to "Manage Employees",
                        "view_reports" to "View Reports",
                        "manage_inventory" to "Manage Inventory",
                        "comp_items" to "Comp Items",
                        "manage_settings" to "Manage Settings"
                    )
                }
                allPerms.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.form.permissions.contains(key),
                            onCheckedChange = { viewModel.togglePermission(key) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SectionTitle("Notes")

            OutlinedTextField(
                value = state.form.notes,
                onValueChange = { value -> viewModel.updateForm { it.copy(notes = value) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { viewModel.save(isAdmin) },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun EmployeeEditScreenPreview() {
    PosTheme {
        Surface {
            EmployeeEditScreen(
                employeeId = null,
                isAdmin = true,
                onBack = {},
                onSaved = {}
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmployeeEditScreenDarkPreview() {
    PosTheme {
        Surface {
            EmployeeEditScreen(
                employeeId = null,
                isAdmin = true,
                onBack = {},
                onSaved = {}
            )
        }
    }
}
