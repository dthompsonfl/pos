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
import com.enterprise.pos.feature.settings.state.AdvancedSettingsViewModel
import com.enterprise.pos.feature.settings.state.ConflictResolution
import com.enterprise.pos.feature.settings.state.LogLevel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    viewModel: AdvancedSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Settings") },
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
            ElevatedPosCard(title = "Debug & Diagnostics", icon = Icons.Default.BugReport) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosSwitchField(title = "Debug mode", checked = state.debugMode, onCheckedChange = viewModel::toggleDebugMode)
                    PosDropdownField(
                        value = state.logLevel,
                        options = LogLevel.entries.toList(),
                        onValueChange = viewModel::setLogLevel,
                        label = "Log Level"
                    )
                    PosSwitchField(title = "Network logging", checked = state.networkLogging, onCheckedChange = viewModel::toggleNetworkLogging)
                    PosSwitchField(title = "UI inspection", checked = state.uiInspection, onCheckedChange = viewModel::toggleUiInspection)
                }
            }

            ElevatedPosCard(title = "Maintenance", icon = Icons.Default.Build) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton(text = "Clear Cache", onClick = viewModel::openClearCacheConfirmation, modifier = Modifier.fillMaxWidth())
                    DangerButton(text = "Reset App Data", onClick = viewModel::openResetConfirmation, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(text = "Database Maintenance", onClick = viewModel::openDbMaintenanceDialog, modifier = Modifier.fillMaxWidth())
                }
            }

            ElevatedPosCard(title = "Sync", icon = Icons.Default.Sync) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosTextField(
                        value = state.autoSyncInterval.toString(),
                        onValueChange = { viewModel.setAutoSyncInterval(it.toIntOrNull() ?: 15) },
                        label = "Auto-sync interval (minutes)",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                    PosDropdownField(
                        value = state.conflictResolution,
                        options = ConflictResolution.entries.toList(),
                        onValueChange = viewModel::setConflictResolution,
                        label = "Conflict Resolution"
                    )
                    SecondaryButton(text = "Manual Sync Now", onClick = viewModel::manualSync, modifier = Modifier.fillMaxWidth())
                }
            }

            ElevatedPosCard(title = "API & Features", icon = Icons.Default.Api) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosTextField(
                        value = state.apiEndpoint,
                        onValueChange = viewModel::updateApiEndpoint,
                        label = "API Base URL"
                    )
                    PosSwitchField(
                        title = "Experimental features",
                        checked = state.featureFlags["experimental"] ?: false,
                        onCheckedChange = { viewModel.toggleFeatureFlag("experimental", it) }
                    )
                    PosSwitchField(
                        title = "Beta analytics",
                        checked = state.featureFlags["beta_analytics"] ?: false,
                        onCheckedChange = { viewModel.toggleFeatureFlag("beta_analytics", it) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (state.showResetConfirmation) {
            PosAlertDialog(
                title = "Reset App Data",
                message = "This will erase all local data and reset the app to factory defaults. This action cannot be undone.",
                onConfirm = viewModel::resetAppData,
                onDismiss = viewModel::closeResetConfirmation,
                confirmText = "Reset",
                confirmButtonColor = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            )
        }

        if (state.showClearCacheConfirmation) {
            PosAlertDialog(
                title = "Clear Cache",
                message = "This will clear temporary files and cached data. Your transactions and settings will not be affected.",
                onConfirm = viewModel::clearCache,
                onDismiss = viewModel::closeClearCacheConfirmation,
                confirmText = "Clear"
            )
        }

        if (state.showDbMaintenanceDialog) {
            PosAlertDialog(
                title = "Database Maintenance",
                message = "Run vacuum, analyze, and integrity check on the local database?",
                onConfirm = viewModel::performDatabaseMaintenance,
                onDismiss = viewModel::closeDbMaintenanceDialog,
                confirmText = "Run"
            )
        }

        state.info?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); viewModel.dismissInfo() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
            }
        }

        if (state.saved) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); viewModel.dismissSaved() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Advanced settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdvancedSettingsScreenPreview() {
    PosTheme { AdvancedSettingsScreen() }
}
