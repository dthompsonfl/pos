package com.enterprise.pos.feature.customers.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.feature.customers.state.CustomersViewModel

@Composable
fun CustomersScreen(
    onCustomerSelected: (CustomerId) -> Unit = {},
    viewModel: CustomersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; viewModel.search(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search by name, phone, or email…") },
            leadingIcon = { Icon(Icons.Filled.PersonSearch, null) },
            trailingIcon = {
                IconButton(onClick = { /* new customer */ }) { Icon(Icons.Filled.PersonAdd, "Add customer") }
            }
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(state.customers, key = { it.id.value }) { c ->
                ListItem(
                    headlineContent = { Text(c.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column {
                            c.email?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            c.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (c.dietaryRestrictions.isNotEmpty()) {
                                Text("⚠ ${c.dietaryRestrictions.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${c.loyaltyPoints} pts", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            if (!c.storeCredit.isZero()) Text(c.storeCredit.format(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    },
                    modifier = Modifier.padding(vertical = 2.dp).clickable { onCustomerSelected(c.id) }
                )
                HorizontalDivider()
            }
        }
    }
}
