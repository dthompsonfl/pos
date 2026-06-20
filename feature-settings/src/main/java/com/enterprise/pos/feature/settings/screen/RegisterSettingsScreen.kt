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
import com.enterprise.pos.feature.settings.state.HardwareType
import com.enterprise.pos.feature.settings.state.RegisterSettingsViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterSettingsScreen(
    viewModel: RegisterSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val register = state.register

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Settings") },
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
            ElevatedPosCard(title = "Register Profile", icon = Icons.Default.PointOfSale) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosTextField(
                        value = register?.name ?: "",
                        onValueChange = { viewModel.updateRegister(name = it) },
                        label = "Register Name",
                        isError = register?.name?.isBlank() == true
                    )
                    PosTextField(
                        value = register?.deviceIdentifier ?: "",
                        onValueChange = { viewModel.updateRegister(deviceId = it) },
                        label = "Device Identifier"
                    )
                    PosSwitchField(
                        title = "Active",
                        checked = register?.active ?: true,
                        onCheckedChange = { viewModel.updateRegister(active = it) }
                    )
                }
            }

            ElevatedPosCard(title = "Hardware Assignments", icon = Icons.Default.Devices) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosDropdownField(
                        value = state.printerName.ifBlank { null },
                        options = listOf("Star TSP100", "Epson TM-T20", "None"),
                        onValueChange = { viewModel.updateHardwareAssignments(printer = it) },
                        label = "Printer"
                    )
                    PosDropdownField(
                        value = state.drawerName.ifBlank { null },
                        options = listOf("APG Cash Drawer", "MMF Cash Drawer", "None"),
                        onValueChange = { viewModel.updateHardwareAssignments(drawer = it) },
                        label = "Cash Drawer"
                    )
                    PosDropdownField(
                        value = state.displayName.ifBlank { null },
                        options = listOf("Customer Display 1", "None"),
                        onValueChange = { viewModel.updateHardwareAssignments(display = it) },
                        label = "Customer Display"
                    )
                    PosDropdownField(
                        value = state.scannerName.ifBlank { null },
                        options = listOf("Honeywell Voyager", "Zebra DS2208", "None"),
                        onValueChange = { viewModel.updateHardwareAssignments(scanner = it) },
                        label = "Barcode Scanner"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(text = "Test Printer", onClick = { viewModel.testHardware(HardwareType.PRINTER) }, modifier = Modifier.weight(1f))
                        SecondaryButton(text = "Test Drawer", onClick = { viewModel.testHardware(HardwareType.DRAWER) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (state.saved) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1500)
                viewModel.dismissSaved()
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Register settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(
                title = "Error",
                message = state.error ?: "",
                onConfirm = viewModel::dismissError,
                onDismiss = viewModel::dismissError
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterSettingsScreenPreview() {
    PosTheme { RegisterSettingsScreen() }
}
