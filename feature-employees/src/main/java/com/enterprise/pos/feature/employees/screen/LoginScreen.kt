package com.enterprise.pos.feature.employees.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.feature.employees.state.LoginViewModel
import com.enterprise.pos.feature.employees.state.LoginUiState

@Composable
fun LoginScreen(
    onLoginSuccess: (com.enterprise.pos.domain.model.Employee) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Store,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enterprise POS",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Enter your PIN",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(24.dp))

        // Security warning banner
        AnimatedVisibility(
            visible = state.securityWarning != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            state.securityWarning?.let { warning ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            warning,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Last login info
        if (state.lastLoginAt != null) {
            Text(
                "Last login: ${state.lastLoginAt}${state.lastLoginDevice?.let { " on $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(8.dp))
        }

        // PIN dots with secure masking
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(8) { i ->
                Box(
                    modifier = Modifier.size(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (i < state.pin.length) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(50)
                        ) { Box(Modifier.size(16.dp)) }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(50)
                        ) { Box(Modifier.size(16.dp)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Lockout countdown
        if (state.isLockedOut && state.lockoutRemainingSeconds > 0) {
            Text(
                "Locked out: ${state.lockoutRemainingSeconds}s remaining",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }

        // Manager unlock required
        if (state.requiresManagerUnlock) {
            Text(
                "Manager unlock required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
        }

        state.loginError?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Keypad
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.width(280.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items((1..9).toList()) { n ->
                KeypadButton(
                    text = n.toString(),
                    onClick = { viewModel.typePin(n.toString()) },
                    enabled = !state.isLockedOut
                )
            }
            item { KeypadButton(text = "", onClick = {}, enabled = false) }
            item {
                KeypadButton(
                    text = "0",
                    onClick = { viewModel.typePin("0") },
                    enabled = !state.isLockedOut
                )
            }
            item {
                KeypadButton(
                    text = "\u232b",
                    onClick = { viewModel.clearPin() },
                    enabled = true
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Login button
        Button(
            onClick = { viewModel.login(onLoginSuccess) },
            enabled = state.pin.length >= 4 && !state.isLoggingIn && !state.isLockedOut,
            modifier = Modifier.fillMaxWidth(0.6f).height(52.dp)
        ) {
            if (state.isLoggingIn) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.Login, null)
                Spacer(Modifier.width(8.dp))
                Text("Log In")
            }
        }

        Spacer(Modifier.height(12.dp))

        // Biometric button
        if (state.showBiometricButton) {
            OutlinedButton(
                onClick = { /* Biometric auth triggered by the screen hosting this composable */ },
                enabled = !state.isLockedOut,
                modifier = Modifier.fillMaxWidth(0.6f).height(44.dp)
            ) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Biometric Login")
            }
            Spacer(Modifier.height(8.dp))
        }

        // Forgot PIN
        TextButton(
            onClick = { viewModel.showForgotPin() },
            enabled = !state.isLockedOut
        ) {
            Text("Forgot PIN?")
        }
    }

    // Forgot PIN dialog
    if (state.showForgotPinDialog) {
        ForgotPinDialog(
            state = state,
            onDismiss = { viewModel.dismissForgotPin() },
            onManagerOverride = { viewModel.showManagerOverride() }
        )
    }

    // Manager override dialog
    if (state.showManagerOverrideDialog) {
        ManagerOverrideDialog(
            state = state,
            onDismiss = { viewModel.dismissForgotPin() },
            onTypePin = { viewModel.typeManagerPin(it) },
            onClearPin = { viewModel.clearManagerPin() },
            onVerify = { viewModel.verifyManagerOverride { viewModel.dismissForgotPin() } }
        )
    }
}

@Composable
private fun KeypadButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    if (text.isEmpty()) {
        Box(Modifier.size(56.dp))
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp),
        enabled = enabled
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun ForgotPinDialog(
    state: LoginUiState,
    onDismiss: () -> Unit,
    onManagerOverride: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Forgot PIN?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please contact a manager to reset your PIN. Manager approval is required for security.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onManagerOverride, modifier = Modifier.fillMaxWidth()) {
                    Text("Manager Override")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ManagerOverrideDialog(
    state: LoginUiState,
    onDismiss: () -> Unit,
    onTypePin: (String) -> Unit,
    onClearPin: () -> Unit,
    onVerify: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.AdminPanelSettings, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text("Manager Override", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter a manager or admin PIN to unlock.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                // PIN display
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(8) { i ->
                        Box(
                            modifier = Modifier.size(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (i < state.managerOverridePin.length) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                ) { Box(Modifier.size(12.dp)) }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                ) { Box(Modifier.size(12.dp)) }
                            }
                        }
                    }
                }

                state.managerOverrideError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(12.dp))

                // Mini keypad
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.width(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items((1..9).toList()) { n ->
                        KeypadButton(text = n.toString(), onClick = { onTypePin(n.toString()) })
                    }
                    item { Box(Modifier.size(48.dp)) }
                    item { KeypadButton(text = "0", onClick = { onTypePin("0") }) }
                    item { KeypadButton(text = "\u232b", onClick = onClearPin) }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onVerify,
                    enabled = state.managerOverridePin.length >= 4,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
        }
    }
}
