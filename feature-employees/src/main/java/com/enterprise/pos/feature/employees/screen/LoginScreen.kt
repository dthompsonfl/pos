package com.enterprise.pos.feature.employees.screen

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.feature.employees.state.EmployeesViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: (com.enterprise.pos.domain.model.Employee) -> Unit,
    viewModel: EmployeesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Store, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Enterprise POS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Enter your PIN", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(32.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
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

        state.loginError?.let { err ->
            Spacer(Modifier.height(16.dp))
            Text(err, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))

        // Keypad
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.width(280.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items((1..9).toList()) { n ->
                KeypadButton(text = n.toString(), onClick = { viewModel.typePin(n.toString()) })
            }
            item { KeypadButton(text = "", onClick = {}) }
            item { KeypadButton(text = "0", onClick = { viewModel.typePin("0") }) }
            item {
                KeypadButton(text = "⌫", onClick = { viewModel.clearPin() })
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.login(onLoginSuccess) },
            enabled = state.pin.length >= 4 && !state.isLoggingIn,
            modifier = Modifier.fillMaxWidth(0.6f).height(56.dp)
        ) {
            if (state.isLoggingIn) CircularProgressIndicator(strokeWidth = 2.dp)
            else { Icon(Icons.Filled.Login, null); Spacer(Modifier.width(8.dp)); Text("Log In") }
        }
    }
}

@Composable
private fun KeypadButton(text: String, onClick: () -> Unit) {
    if (text.isEmpty()) {
        Box(Modifier.size(56.dp))
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}
