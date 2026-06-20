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
import com.enterprise.pos.feature.settings.state.DeviceSettings
import com.enterprise.pos.feature.settings.state.DeviceType
import com.enterprise.pos.feature.settings.state.HardwareSettingsViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareSettingsScreen(
    viewModel: HardwareSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var manualName by remember { mutableStateOf("") }
    var manualType by remember { mutableStateOf(DeviceType.PRINTER) }
    var manualAddress by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9100") }
    var manualProtocol by remember { mutableStateOf("TCP") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Settings") },
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
            ElevatedPosCard(title = "Discovered Devices", icon = Icons.Default.Devices) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton(
                            text = if (state.isScanning) "Scanning..." else "Scan Devices",
                            onClick = viewModel::scanDevices,
                            modifier = Modifier.weight(1f),
                            enabled = !state.isScanning
                        )
                        SecondaryButton(text = "Add Manual", onClick = viewModel::openManualAddDialog, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (state.discoveredDevices.isEmpty()) {
                        Text("No devices discovered. Tap Scan to find nearby hardware.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        state.discoveredDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text("${device.type.name} · ${device.connectionType}", style = MaterialTheme.typography.bodySmall)
                                }
                                PrimaryButton(text = "Connect", onClick = { viewModel.connect(device) })
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            ElevatedPosCard(title = "Connected Devices", icon = Icons.Default.CheckCircle) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.connectedDevices.isEmpty()) {
                        Text("No connected devices.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        state.connectedDevices.forEach { device ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(device.name, fontWeight = FontWeight.SemiBold)
                                    Text(device.address, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { viewModel.testDevice(device) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Test")
                                    }
                                    IconButton(onClick = { viewModel.disconnect(device) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Disconnect", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            if (device.type == DeviceType.PRINTER) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Paper width:", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Slider(
                                        value = device.settings.paperWidth.toFloat(),
                                        onValueChange = {
                                            viewModel.updateDeviceSettings(device.id, device.settings.copy(paperWidth = it.toInt()))
                                        },
                                        valueRange = 58f..80f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("${device.settings.paperWidth}mm", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (state.showManualAddDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeManualAddDialog,
                title = { Text("Add Device Manually") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(value = manualName, onValueChange = { manualName = it }, label = "Name")
                        PosDropdownField(
                            value = manualType,
                            options = DeviceType.entries.toList(),
                            onValueChange = { manualType = it },
                            label = "Type"
                        )
                        PosTextField(value = manualAddress, onValueChange = { manualAddress = it }, label = "IP Address")
                        PosTextField(value = manualPort, onValueChange = { manualPort = it }, label = "Port", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        PosTextField(value = manualProtocol, onValueChange = { manualProtocol = it }, label = "Protocol")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.addManualDevice(manualName, manualType, manualAddress, manualPort.toIntOrNull() ?: 9100, manualProtocol)
                    }, enabled = manualName.isNotBlank() && manualAddress.isNotBlank()) { Text("Add") }
                },
                dismissButton = { TextButton(onClick = viewModel::closeManualAddDialog) { Text("Cancel") } }
            )
        }

        if (state.saved) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); viewModel.dismissSaved() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Hardware settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HardwareSettingsScreenPreview() {
    PosTheme { HardwareSettingsScreen() }
}
