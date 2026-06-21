package com.enterprise.pos.feature.settings.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.enterprise.pos.feature.settings.state.SettingsUiState
import com.enterprise.pos.feature.settings.state.SettingsViewModel
import com.enterprise.pos.payment.model.PaymentProviderId
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToStore: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onNavigateToTax: () -> Unit = {},
    onNavigateToPayment: () -> Unit = {},
    onNavigateToReceipt: () -> Unit = {},
    onNavigateToHardware: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { /* search toggle */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search settings")
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
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search settings...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))

            SettingsSection(
                title = "Store",
                icon = Icons.Default.Store,
                summary = state.store?.name ?: "Not configured",
                visible = searchQuery.isBlank() || "store".contains(searchQuery, ignoreCase = true)
            ) {
                SummaryRow("Name", state.store?.name ?: "-")
                SummaryRow("Currency", state.store?.currency ?: "-")
                SummaryRow("Timezone", state.store?.timezone ?: "-")
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Edit Store", onClick = onNavigateToStore, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Register",
                icon = Icons.Default.PointOfSale,
                summary = "Device and hardware assignments",
                visible = searchQuery.isBlank() || "register".contains(searchQuery, ignoreCase = true)
            ) {
                SummaryRow("Device", state.register?.deviceIdentifier ?: "-")
                SummaryRow("Printer", state.printerName.ifBlank { "Not assigned" })
                SummaryRow("Drawer", state.drawerName.ifBlank { "Not assigned" })
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Edit Register", onClick = onNavigateToRegister, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Tax",
                icon = Icons.Default.AccountBalance,
                summary = "${state.taxConfig.rules.size} tax rules configured",
                visible = searchQuery.isBlank() || "tax".contains(searchQuery, ignoreCase = true)
            ) {
                state.taxConfig.rules.forEach { rule ->
                    SummaryRow(rule.name, "${"%.2f".format(rule.rate.asDouble)}%")
                }
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Edit Tax Rules", onClick = onNavigateToTax, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Payment",
                icon = Icons.Default.CreditCard,
                summary = "${state.enabledProviders.size} providers active",
                visible = searchQuery.isBlank() || "payment".contains(searchQuery, ignoreCase = true)
            ) {
                SummaryRow("Default", state.defaultProvider.displayName)
                SummaryRow("Offline mode", if (state.enableOfflineMode) "Enabled" else "Disabled")
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Edit Payment", onClick = onNavigateToPayment, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Receipt",
                icon = Icons.Default.Receipt,
                summary = "Template and print options",
                visible = searchQuery.isBlank() || "receipt".contains(searchQuery, ignoreCase = true)
            ) {
                PosSwitchField(
                    title = "Auto-print receipts",
                    checked = state.autoPrintReceipts,
                    onCheckedChange = viewModel::setAutoPrint
                )
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Edit Receipt", onClick = onNavigateToReceipt, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Hardware",
                icon = Icons.Default.Devices,
                summary = "Connected peripherals",
                visible = searchQuery.isBlank() || "hardware".contains(searchQuery, ignoreCase = true)
            ) {
                PrimaryButton(text = "Manage Hardware", onClick = onNavigateToHardware, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Employees",
                icon = Icons.Default.People,
                summary = "${state.employeeCount} employees",
                visible = searchQuery.isBlank() || "employee".contains(searchQuery, ignoreCase = true)
            ) {
                state.rolePermissions.forEach { (role, perms) ->
                    val label = role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
                    SummaryRow(label, "Refunds: ${if (perms.canProcessRefunds) "Yes" else "No"}, Drawer: ${if (perms.canOpenDrawer) "Yes" else "No"}")
                }
            }

            SettingsSection(
                title = "Backup",
                icon = Icons.Default.Backup,
                summary = state.lastBackupAt?.let { "Last backup: ${java.text.SimpleDateFormat("MMM d, yyyy HH:mm").format(java.util.Date(it))}" } ?: "No backups yet",
                visible = searchQuery.isBlank() || "backup".contains(searchQuery, ignoreCase = true)
            ) {
                PrimaryButton(text = "Manage Backups", onClick = onNavigateToBackup, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "Advanced",
                icon = Icons.Default.Tune,
                summary = "Debug, sync, and maintenance",
                visible = searchQuery.isBlank() || "advanced".contains(searchQuery, ignoreCase = true)
            ) {
                PosSwitchField(title = "Dark theme", checked = state.darkMode, onCheckedChange = viewModel::setDarkMode)
                PosSwitchField(title = "Quick checkout", checked = state.enableQuickCheckout, onCheckedChange = viewModel::setQuickCheckout)
                Spacer(modifier = Modifier.height(8.dp))
                PrimaryButton(text = "Advanced Options", onClick = onNavigateToAdvanced, modifier = Modifier.fillMaxWidth())
            }

            SettingsSection(
                title = "About",
                icon = Icons.Default.Info,
                summary = "Version ${state.appVersion} (${state.buildNumber})",
                visible = searchQuery.isBlank() || "about".contains(searchQuery, ignoreCase = true)
            ) {
                SummaryRow("Version", "${state.appVersion} (${state.buildNumber})")
                Text("Enterprise POS", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Enterprise-grade point of sale for restaurants and retail.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { /* Open privacy policy */ }, modifier = Modifier.fillMaxWidth()) { Text("Privacy Policy") }
                TextButton(onClick = { /* Open terms */ }, modifier = Modifier.fillMaxWidth()) { Text("Terms of Service") }
                TextButton(onClick = { /* Open licenses */ }, modifier = Modifier.fillMaxWidth()) { Text("Open Source Licenses") }
                TextButton(onClick = { /* Contact support */ }, modifier = Modifier.fillMaxWidth()) { Text("Contact Support") }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        state.info?.let { message ->
            SnackbarHost(
                hostState = remember { SnackbarHostState() }.apply {
                    LaunchedEffect(message) { showSnackbar(message) }
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    summary: String,
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(visible = visible) {
        ElevatedPosCard(
            title = title,
            subtitle = summary,
            icon = icon
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PosTheme {
        SettingsScreen()
    }
}
