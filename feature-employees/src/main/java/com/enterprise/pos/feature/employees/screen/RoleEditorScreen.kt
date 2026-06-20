package com.enterprise.pos.feature.employees.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.feature.employees.state.RoleEditorEvent
import com.enterprise.pos.feature.employees.state.RoleEditorViewModel
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleEditorScreen(
    onBack: () -> Unit,
    viewModel: RoleEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.selectRole(EmployeeRole.CASHIER)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RoleEditorEvent.Saved -> {}
                is RoleEditorEvent.Error -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Role & Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveRole() },
                        enabled = !state.isSaving
                    ) { Icon(Icons.Filled.Save, "Save") }
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

            Text("Select Role", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            var roleExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = roleExpanded,
                onExpandedChange = { roleExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = state.selectedRole.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
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
                                viewModel.selectRole(role)
                                roleExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            viewModel.permissionGroups.forEach { group ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        group.permissions.forEach { permKey ->
                            val permLabel = permKey.replace('_', ' ').replaceFirstChar { it.titlecase() }
                            val checked = state.rolePermissions[permKey] ?: false
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.togglePermission(permKey) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(permLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { viewModel.saveRole() },
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

@Preview(showBackground = true)
@Composable
private fun RoleEditorScreenPreview() {
    PosTheme {
        Surface {
            RoleEditorScreen(onBack = {})
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoleEditorScreenDarkPreview() {
    PosTheme {
        Surface {
            RoleEditorScreen(onBack = {})
        }
    }
}
