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
import com.enterprise.pos.feature.settings.state.StoreSettingsViewModel
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreSettingsScreen(
    viewModel: StoreSettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val store = state.store

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Store Settings") },
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
            ElevatedPosCard(title = "Store Profile", icon = Icons.Default.Store) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosTextField(
                        value = store?.name ?: "",
                        onValueChange = { viewModel.updateStore(name = it) },
                        label = "Store Name",
                        isError = store?.name?.isBlank() == true
                    )
                    PosTextField(
                        value = store?.address ?: "",
                        onValueChange = { viewModel.updateStore(address = it) },
                        label = "Address",
                        isError = store?.address?.isBlank() == true
                    )
                    PosTextField(
                        value = store?.phone ?: "",
                        onValueChange = { viewModel.updateStore(phone = it) },
                        label = "Phone",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                        isError = store?.phone?.isBlank() == true
                    )
                    PosTextField(
                        value = store?.taxIdentifier ?: "",
                        onValueChange = { viewModel.updateStore(taxId = it.ifBlank { null }) },
                        label = "Tax ID"
                    )
                    PosDropdownField(
                        value = store?.currency,
                        options = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY"),
                        onValueChange = { viewModel.updateStore(currency = it) },
                        label = "Currency"
                    )
                    PosDropdownField(
                        value = store?.timezone,
                        options = listOf(
                            "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
                            "Europe/London", "Europe/Paris", "Europe/Berlin", "Asia/Tokyo"
                        ),
                        onValueChange = { viewModel.updateStore(timezone = it) },
                        label = "Timezone"
                    )
                }
            }

            ElevatedPosCard(title = "Receipt Customization", icon = Icons.Default.Receipt) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PosTextField(
                        value = state.receiptHeader,
                        onValueChange = viewModel::updateReceiptHeader,
                        label = "Receipt Header",
                        maxLines = 3,
                        minLines = 2
                    )
                    PosTextField(
                        value = state.receiptFooter,
                        onValueChange = viewModel::updateReceiptFooter,
                        label = "Receipt Footer",
                        maxLines = 3,
                        minLines = 2
                    )
                    PosSwitchField(
                        title = "Include logo on receipt",
                        checked = state.hasLogo,
                        onCheckedChange = viewModel::toggleLogo
                    )
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
                Snackbar(modifier = Modifier.padding(16.dp)) { Text("Store settings saved") }
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
private fun StoreSettingsScreenPreview() {
    PosTheme { StoreSettingsScreen() }
}
