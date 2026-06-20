package com.enterprise.pos.feature.settings.screen

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
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.service.TaxRule
import com.enterprise.pos.feature.settings.state.TaxSettingsViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxSettingsScreen(
    viewModel: TaxSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialogName by remember { mutableStateOf("") }
    var dialogRate by remember { mutableStateOf("") }
    var dialogCompound by remember { mutableStateOf(false) }
    var dialogInclusive by remember { mutableStateOf(false) }
    var dialogCategories by remember { mutableStateOf(setOf<TaxCategory>()) }
    val editingName = state.editingRule?.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tax Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
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
            ElevatedPosCard(title = "Tax Rules", icon = Icons.Default.AccountBalance) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.rules.isEmpty()) {
                        Text("No tax rules configured.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        state.rules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.name, fontWeight = FontWeight.SemiBold)
                                    Text("${"%.2f".format(rule.rate.asDouble)}%", style = MaterialTheme.typography.bodySmall)
                                    Text("Applies to: ${rule.appliesTo.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { viewModel.openEditDialog(rule) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { viewModel.deleteRule(rule.name) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PrimaryButton(text = "Add Tax Rule", onClick = viewModel::openAddDialog, icon = Icons.Default.Add, modifier = Modifier.fillMaxWidth())
                }
            }

            ElevatedPosCard(title = "Options", icon = Icons.Default.Settings) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosDropdownField(
                        value = state.defaultCategory,
                        options = TaxCategory.entries.toList(),
                        onValueChange = viewModel::setDefaultCategory,
                        label = "Default Tax Category"
                    )
                    PosSwitchField(
                        title = "Tax-inclusive pricing",
                        subtitle = "Prices include tax; tax is shown as a breakdown",
                        checked = state.inclusivePricing,
                        onCheckedChange = viewModel::setInclusivePricing
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (state.showDialog) {
            LaunchedEffect(state.editingRule) {
                dialogName = state.editingRule?.name ?: ""
                dialogRate = state.editingRule?.rate?.asDouble?.toString() ?: ""
                dialogCompound = state.editingRule?.compoundOn?.isNotEmpty() ?: false
                dialogInclusive = state.editingRule?.isInclusive ?: false
                dialogCategories = state.editingRule?.appliesTo ?: setOf(TaxCategory.STANDARD)
            }
            AlertDialog(
                onDismissRequest = viewModel::closeDialog,
                title = { Text(if (editingName != null) "Edit Tax Rule" else "Add Tax Rule") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(value = dialogName, onValueChange = { dialogName = it }, label = "Name")
                        PosTextField(value = dialogRate, onValueChange = { dialogRate = it }, label = "Rate (%)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        PosSwitchField(title = "Compound on other taxes", checked = dialogCompound, onCheckedChange = { dialogCompound = it })
                        PosSwitchField(title = "Inclusive tax", checked = dialogInclusive, onCheckedChange = { dialogInclusive = it })
                        Text("Applies to:", style = MaterialTheme.typography.labelLarge)
                        TaxCategory.entries.forEach { cat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = cat in dialogCategories, onCheckedChange = { checked ->
                                    dialogCategories = if (checked) dialogCategories + cat else dialogCategories - cat
                                })
                                Text(cat.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val rate = dialogRate.toDoubleOrNull() ?: 0.0
                        val rule = TaxRule(
                            name = dialogName,
                            rate = com.enterprise.pos.core.Percent.of(rate),
                            appliesTo = dialogCategories,
                            compoundOn = if (dialogCompound) listOf("Sales Tax") else emptyList(),
                            isInclusive = dialogInclusive
                        )
                        if (editingName != null) viewModel.updateRule(editingName, rule) else viewModel.addRule(rule)
                    }, enabled = dialogName.isNotBlank() && dialogRate.toDoubleOrNull() != null) {
                        Text("Save")
                    }
                },
                dismissButton = { TextButton(onClick = viewModel::closeDialog) { Text("Cancel") } }
            )
        }

        if (state.saved) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); viewModel.dismissSaved() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Tax settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaxSettingsScreenPreview() {
    PosTheme { TaxSettingsScreen() }
}
