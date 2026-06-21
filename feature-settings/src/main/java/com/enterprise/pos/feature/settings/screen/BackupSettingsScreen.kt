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
import com.enterprise.pos.feature.settings.state.BackupSchedule
import com.enterprise.pos.feature.settings.state.BackupSettingsViewModel
import com.enterprise.pos.feature.settings.state.ExportFormat
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    viewModel: BackupSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup Settings") },
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
            ElevatedPosCard(title = "Backup Schedule", icon = Icons.Default.Schedule) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosDropdownField(
                        value = state.schedule,
                        options = BackupSchedule.entries.toList(),
                        onValueChange = viewModel::setSchedule,
                        label = "Schedule"
                    )
                    PosSwitchField(
                        title = "Cloud backup",
                        subtitle = "Upload backups to cloud storage",
                        checked = state.cloudBackupEnabled,
                        onCheckedChange = viewModel::toggleCloudBackup
                    )
                    state.lastBackupAt?.let {
                        Text("Last backup: ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US).format(Date(it))} (${state.lastBackupSize})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ElevatedPosCard(title = "Actions", icon = Icons.Default.Backup) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingButton(
                        text = "Backup Now",
                        onClick = viewModel::backupNow,
                        isLoading = state.isBackingUp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SecondaryButton(text = "Restore from Backup", onClick = viewModel::restoreFromBackup, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Export", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ExportFormat.entries.forEach { format ->
                            ActionChip(label = format.name, onClick = { viewModel.exportData(format) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Import", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(ExportFormat.CSV, ExportFormat.JSON).forEach { format ->
                            ActionChip(label = format.name, onClick = { viewModel.importData(format) })
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isBackingUp && !state.isRestoring)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        state.info?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2500)
                viewModel.dismissInfo()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text(msg) }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupSettingsScreenPreview() {
    PosTheme { BackupSettingsScreen() }
}
