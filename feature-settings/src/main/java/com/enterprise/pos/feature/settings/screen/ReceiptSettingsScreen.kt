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
import com.enterprise.pos.feature.settings.state.PrintMode
import com.enterprise.pos.feature.settings.state.ReceiptSettingsViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSettingsScreen(
    viewModel: ReceiptSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receipt Settings") },
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
            ElevatedPosCard(title = "Receipt Template", icon = Icons.Default.Receipt) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosSwitchField(title = "Include logo", checked = state.hasLogo, onCheckedChange = viewModel::updateLogo)
                    PosTextField(value = state.headerText, onValueChange = viewModel::updateHeader, label = "Header Text", maxLines = 3, minLines = 2)
                    PosTextField(value = state.footerText, onValueChange = viewModel::updateFooter, label = "Footer Text", maxLines = 3, minLines = 2)
                }
            }

            ElevatedPosCard(title = "Show/Hide Fields", icon = Icons.Default.Visibility) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosSwitchField(title = "Tax breakdown", checked = state.showTaxBreakdown, onCheckedChange = viewModel::toggleShowTaxBreakdown)
                    PosSwitchField(title = "Employee name", checked = state.showEmployeeName, onCheckedChange = viewModel::toggleShowEmployeeName)
                    PosSwitchField(title = "Store information", checked = state.showStoreInfo, onCheckedChange = viewModel::toggleShowStoreInfo)
                    PosSwitchField(title = "Barcode", checked = state.showBarcode, onCheckedChange = viewModel::toggleShowBarcode)
                }
            }

            ElevatedPosCard(title = "Print Options", icon = Icons.Default.Print) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Print Mode", style = MaterialTheme.typography.labelLarge)
                    PrintMode.entries.forEach { mode ->
                        SelectableCard(
                            selected = state.printMode == mode,
                            onSelect = { viewModel.setPrintMode(mode) },
                            title = mode.name.replace('_', ' ').replaceFirstChar { it.titlecase() }
                        )
                    }
                    PosSwitchField(title = "Auto-print after payment", checked = state.autoPrint, onCheckedChange = viewModel::setAutoPrint)
                }
            }

            ElevatedPosCard(title = "Email Receipt", icon = Icons.Default.Email) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosSwitchField(title = "Enable email receipts", checked = state.emailReceiptEnabled, onCheckedChange = viewModel::setEmailReceiptEnabled)
                    AnimatedVisibility(visible = state.emailReceiptEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PosTextField(value = state.smtpHost, onValueChange = { viewModel.updateSmtp(it, state.smtpPort, state.smtpUsername, state.smtpPassword) }, label = "SMTP Host")
                            PosTextField(value = state.smtpPort, onValueChange = { viewModel.updateSmtp(state.smtpHost, it, state.smtpUsername, state.smtpPassword) }, label = "SMTP Port", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            PosTextField(value = state.smtpUsername, onValueChange = { viewModel.updateSmtp(state.smtpHost, state.smtpPort, it, state.smtpPassword) }, label = "SMTP Username")
                            PosTextField(value = state.smtpPassword, onValueChange = { viewModel.updateSmtp(state.smtpHost, state.smtpPort, state.smtpUsername, it) }, label = "SMTP Password", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
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

        if (state.saved) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); viewModel.dismissSaved() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Receipt settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReceiptSettingsScreenPreview() {
    PosTheme { ReceiptSettingsScreen() }
}
