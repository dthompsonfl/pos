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
import com.enterprise.pos.feature.settings.state.PaymentSettingsViewModel
import com.enterprise.pos.feature.settings.state.RefundPolicy
import com.enterprise.pos.feature.settings.state.TipEntryMode
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.payment.model.ProviderConfig
import com.enterprise.pos.payment.model.ProviderEnvironment
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSettingsScreen(
    viewModel: PaymentSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var providerApiKey by remember { mutableStateOf("") }
    var providerMerchantId by remember { mutableStateOf("") }
    var providerEnv by remember { mutableStateOf(ProviderEnvironment.SANDBOX) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment Settings") },
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
            ElevatedPosCard(title = "Payment Providers", icon = Icons.Default.CreditCard) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PaymentProviderId.entries.forEach { provider ->
                        val active = provider in state.activeProviders
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                val config = state.providers[provider]
                                Text(config?.environment?.name ?: "Not configured", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Switch(checked = active, onCheckedChange = { viewModel.toggleProvider(provider) })
                                IconButton(onClick = { viewModel.openProviderDialog(provider) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Configure")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Default Provider", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.activeProviders.forEach { p ->
                            FilterChip(
                                label = p.displayName,
                                selected = state.defaultProvider == p,
                                onClick = { viewModel.setDefaultProvider(p) }
                            )
                        }
                    }
                }
            }

            ElevatedPosCard(title = "Cash Drawer", icon = Icons.Default.Draw) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PosSwitchField(title = "Auto-open on cash payment", checked = state.cashDrawerAutoOpen, onCheckedChange = { viewModel.setCashDrawerSettings(it, state.cashDrawerCloseConfirm) })
                    PosSwitchField(title = "Close confirmation", checked = state.cashDrawerCloseConfirm, onCheckedChange = { viewModel.setCashDrawerSettings(state.cashDrawerAutoOpen, it) })
                }
            }

            ElevatedPosCard(title = "Tip Settings", icon = Icons.Default.AttachMoney) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tip Percentages", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tipPercentages.forEach { pct ->
                            AssistChip(
                                onClick = {},
                                label = { Text("$pct%") }
                            )
                        }
                    }
                    PosSwitchField(title = "Allow custom tip amount", checked = state.tipCustomEnabled, onCheckedChange = { enabled ->
                        viewModel.setTipSettings(state.tipPercentages, state.tipFixedAmounts, enabled, state.tipEntryMode)
                    })
                    Text("Tip Entry Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TipEntryMode.entries.forEach { mode ->
                            FilterChip(
                                label = mode.name.replace('_', ' '),
                                selected = state.tipEntryMode == mode,
                                onClick = { viewModel.setTipSettings(state.tipPercentages, state.tipFixedAmounts, state.tipCustomEnabled, mode) }
                            )
                        }
                    }
                }
            }

            ElevatedPosCard(title = "Refund Policy", icon = Icons.Default.MoneyOff) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RefundPolicy.entries.forEach { policy ->
                        SelectableCard(
                            selected = state.refundPolicy == policy,
                            onSelect = { viewModel.setRefundPolicy(policy, state.partialRefundMinimum) },
                            title = policy.name.replace('_', ' ').replaceFirstChar { it.titlecase() },
                            subtitle = when (policy) {
                                RefundPolicy.ORIGINAL_METHOD -> "Refunds must use original payment method"
                                RefundPolicy.STORE_CREDIT_ALLOWED -> "Store credit is permitted for refunds"
                                RefundPolicy.NO_RESTRICTIONS -> "No restrictions on refund methods"
                            }
                        )
                    }
                    PosTextField(
                        value = state.partialRefundMinimum,
                        onValueChange = { viewModel.setRefundPolicy(state.refundPolicy, it) },
                        label = "Partial refund minimum",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TertiaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Save", onClick = viewModel::save, modifier = Modifier.weight(1f), enabled = !state.isSaving)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (state.showProviderDialog) {
            val provider = state.editingProvider
            val currentConfig = provider?.let { state.providers[it] } ?: ProviderConfig()
            LaunchedEffect(provider) {
                providerApiKey = currentConfig.apiKey ?: ""
                providerMerchantId = currentConfig.merchantId ?: ""
                providerEnv = currentConfig.environment
            }
            AlertDialog(
                onDismissRequest = viewModel::closeProviderDialog,
                title = { Text("${provider?.displayName ?: "Provider"} Configuration") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosTextField(value = providerApiKey, onValueChange = { providerApiKey = it }, label = "API Key")
                        PosTextField(value = providerMerchantId, onValueChange = { providerMerchantId = it }, label = "Merchant ID")
                        PosDropdownField(
                            value = providerEnv,
                            options = ProviderEnvironment.entries.toList(),
                            onValueChange = { providerEnv = it },
                            label = "Environment"
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        provider?.let {
                            viewModel.updateProviderConfig(it, ProviderConfig(apiKey = providerApiKey, merchantId = providerMerchantId, environment = providerEnv))
                        }
                    }) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = viewModel::closeProviderDialog) { Text("Cancel") } }
            )
        }

        if (state.saved) {
            LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); viewModel.dismissSaved() }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Payment settings saved") }
            }
        }

        if (state.error != null) {
            PosAlertDialog(title = "Error", message = state.error ?: "", onConfirm = viewModel::dismissError, onDismiss = viewModel::dismissError)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaymentSettingsScreenPreview() {
    PosTheme { PaymentSettingsScreen() }
}
