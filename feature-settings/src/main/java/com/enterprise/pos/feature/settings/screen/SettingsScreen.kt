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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.feature.settings.state.SettingsViewModel
import com.enterprise.pos.payment.model.PaymentProviderId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- PAYMENT PROVIDERS ---
            Section(title = "Payment Providers", icon = Icons.Filled.CreditCard) {
                Text("Enable or disable providers for this register:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                PaymentProviderId.values().forEach { p ->
                    val enabled = p in state.enabledProviders
                    ListItem(
                        headlineContent = { Text(p.displayName) },
                        supportingContent = { Text(p.name) },
                        trailingContent = {
                            Switch(checked = enabled, onCheckedChange = { viewModel.toggleProvider(p) })
                        }
                    )
                    HorizontalDivider()
                }
                Spacer(Modifier.height(12.dp))
                Text("Default Provider", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.enabledProviders.forEach { p ->
                        FilterChip(
                            selected = state.defaultProvider == p,
                            onClick = { viewModel.setDefaultProvider(p) },
                            label = { Text(p.displayName) }
                        )
                    }
                }
            }

            // --- TAX CONFIGURATION ---
            Section(title = "Tax Configuration", icon = Icons.Filled.ReceiptLong) {
                state.taxConfig.rules.forEach { rule ->
                    ListItem(
                        headlineContent = { Text(rule.name) },
                        supportingContent = { Text("${"%.2f".format(rule.rate.asDouble)}% · ${rule.appliesTo.size} categories") },
                        trailingContent = { Icon(Icons.Filled.Edit, null) }
                    )
                    HorizontalDivider()
                }
                Text(
                    "Tax rates are configurable per store and per category. Compound taxes (e.g. QST + GST) are supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // --- ROLE PERMISSIONS ---
            Section(title = "Role Permissions", icon = Icons.Filled.Security) {
                EmployeeRole.values().forEach { role ->
                    val perms = state.rolePermissions[role] ?: return@forEach
                    ListItem(
                        headlineContent = { Text(role.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }) },
                        supportingContent = {
                            Text(
                                buildString {
                                    if (perms.canProcessRefunds) append("Refunds ")
                                    if (perms.canVoidOrders) append("Void ")
                                    if (perms.canManageEmployees) append("Emps ")
                                    if (perms.canViewReports) append("Reports ")
                                    if (perms.canOpenDrawer) append("Drawer ")
                                    append("Disc≤${perms.maxDiscountPercent}%")
                                }
                            )
                        },
                        trailingContent = { Icon(Icons.Filled.Edit, null) }
                    )
                    HorizontalDivider()
                }
            }

            // --- OFFLINE & OPERATIONS ---
            Section(title = "Offline & Operations", icon = Icons.Filled.CloudOff) {
                ToggleRow("Offline payment mode", "Allow card payments to queue when network is down", state.enableOfflineMode, viewModel::setOfflineMode)
                ToggleRow("Quick checkout", "Skip confirmation steps for fast casual flow", state.enableQuickCheckout, viewModel::setQuickCheckout)
                ToggleRow("Auto-print receipts", "Print receipt after every payment", state.autoPrintReceipts, viewModel::setAutoPrint)
                ToggleRow("Dark theme", "Use dark color scheme", state.darkMode, viewModel::setDarkMode)
                Spacer(Modifier.height(8.dp))
                Text("Max offline payment amount: ${state.maxOfflineAmount.format()}", style = MaterialTheme.typography.bodyMedium)
            }

            // --- DANGER ZONE ---
            Section(title = "Danger Zone", icon = Icons.Filled.Warning, color = MaterialTheme.colorScheme.error) {
                Button(
                    onClick = { /* future: reset DB */ },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Reset Local Database")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { /* future: export audit log */ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Export Audit Log")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
