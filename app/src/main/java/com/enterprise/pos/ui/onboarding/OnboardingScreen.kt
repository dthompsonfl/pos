package com.enterprise.pos.ui.onboarding

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.ui.components.*
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onComplete: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isComplete) {
        LaunchedEffect(Unit) { onComplete() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup") },
                navigationIcon = {
                    if (state.canGoBack) {
                        IconButton(onClick = viewModel::goBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                StepIndicator(current = state.currentStep.ordinal, total = OnboardingStep.entries.size)
                Spacer(modifier = Modifier.height(24.dp))
                when (state.currentStep) {
                    OnboardingStep.WELCOME -> WelcomeStep(state.progress, viewModel::updateProgress)
                    OnboardingStep.STORE -> StoreStep(state.progress, viewModel::updateProgress)
                    OnboardingStep.REGISTER -> RegisterStep(state.progress, viewModel::updateProgress)
                    OnboardingStep.EMPLOYEE -> EmployeeStep(state.progress, viewModel::updateProgress)
                    OnboardingStep.PRODUCT -> ProductStep(viewModel::updateProgress)
                    OnboardingStep.PAYMENT -> PaymentStep(viewModel::updateProgress)
                    OnboardingStep.COMPLETE -> CompleteStep(state.progress)
                }
            }

            Column {
                if (state.error != null) {
                    InfoCard(
                        type = InfoCardType.ERROR,
                        title = "Error",
                        message = state.error ?: "",
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.canSkip && state.currentStep != OnboardingStep.COMPLETE) {
                        TertiaryButton(text = "Skip", onClick = viewModel::skipStep, modifier = Modifier.weight(1f))
                    }
                    if (state.currentStep == OnboardingStep.COMPLETE) {
                        PrimaryButton(
                            text = if (state.isLoading) "Finishing..." else "Start Using POS",
                            onClick = viewModel::completeOnboarding,
                            modifier = Modifier.fillMaxWidth(),
                            isLoading = state.isLoading
                        )
                    } else {
                        PrimaryButton(
                            text = "Next",
                            onClick = viewModel::goToNext,
                            modifier = Modifier.weight(1f),
                            enabled = viewModel.validateCurrentStep()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    LinearProgressIndicator(
        progress = { (current + 1).toFloat() / total },
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text("Step ${current + 1} of $total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WelcomeStep(
    progress: OnboardingProgress,
    update: (OnboardingProgress. -> OnboardingProgress) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Welcome to Enterprise POS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Let's get your business set up in a few simple steps.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        SelectableCard(
            selected = progress.termsAccepted,
            onSelect = { update { copy(termsAccepted = !termsAccepted) } },
            title = "I accept the Terms of Service and Privacy Policy",
            subtitle = "You must accept to continue"
        )
    }
}

@Composable
private fun StoreStep(
    progress: OnboardingProgress,
    update: (OnboardingProgress. -> OnboardingProgress) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Store Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PosTextField(value = progress.storeName, onValueChange = { update { copy(storeName = it) } }, label = "Store Name")
        PosTextField(value = progress.storeAddress, onValueChange = { update { copy(storeAddress = it) } }, label = "Address")
        PosTextField(value = progress.storePhone, onValueChange = { update { copy(storePhone = it) } }, label = "Phone", keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        PosTextField(value = progress.storeTaxId, onValueChange = { update { copy(storeTaxId = it) } }, label = "Tax ID (optional)")
        PosDropdownField(value = progress.storeCurrency, options = listOf("USD", "EUR", "GBP", "CAD", "AUD", "JPY"), onValueChange = { update { copy(storeCurrency = it) } }, label = "Currency")
        PosDropdownField(value = progress.storeTimezone, options = listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "Europe/London", "Europe/Paris", "Asia/Tokyo"), onValueChange = { update { copy(storeTimezone = it) } }, label = "Timezone")
    }
}

@Composable
private fun RegisterStep(
    progress: OnboardingProgress,
    update: (OnboardingProgress. -> OnboardingProgress) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Register Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PosTextField(value = progress.registerName, onValueChange = { update { copy(registerName = it) } }, label = "Register Name")
        PosTextField(value = progress.deviceType, onValueChange = { update { copy(deviceType = it) } }, label = "Device Type / Model")
        Text("Hardware will be auto-detected after setup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun EmployeeStep(
    progress: OnboardingProgress,
    update: (OnboardingProgress. -> OnboardingProgress) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Create Admin Account", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        PosTextField(value = progress.adminName, onValueChange = { update { copy(adminName = it) } }, label = "Full Name")
        PosTextField(value = progress.adminEmail, onValueChange = { update { copy(adminEmail = it) } }, label = "Email (optional)")
        PosTextField(value = progress.adminPin, onValueChange = { update { copy(adminPin = it) } }, label = "PIN (min 4 digits)", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("This account will have full manager access.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ProductStep(update: (OnboardingProgress. -> OnboardingProgress) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Product Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("You can import products from a CSV file or create your first category and product later in the Catalog.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        SecondaryButton(text = "Import Products (CSV)", onClick = { /* file picker */ }, modifier = Modifier.fillMaxWidth())
        TertiaryButton(text = "Create Later in Catalog", onClick = { }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PaymentStep(update: (OnboardingProgress. -> OnboardingProgress) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Payment Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Configure payment providers in Settings after setup. For now, Cash and Manual Entry are always available.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        InfoCard(type = InfoCardType.INFO, title = "Tip", message = "You can connect Stripe, Square, or Shopify in Settings > Payment after completing setup.", icon = Icons.Default.Info)
    }
}

@Composable
private fun CompleteStep(progress: OnboardingProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text("Setup Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("You're ready to start taking orders.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        ElevatedPosCard(title = "Summary") {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Store: ${progress.storeName}", style = MaterialTheme.typography.bodyMedium)
                Text("Register: ${progress.registerName.ifBlank { "Main Register" }}", style = MaterialTheme.typography.bodyMedium)
                Text("Admin: ${progress.adminName}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("You can always adjust these settings later from the Settings menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    PosTheme { OnboardingScreen() }
}
