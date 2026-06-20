package com.enterprise.pos.feature.customers.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.feature.customers.state.CustomerEditEvent
import com.enterprise.pos.feature.customers.state.CustomerEditForm
import com.enterprise.pos.feature.customers.state.CustomerEditViewModel
import com.enterprise.pos.ui.theme.PosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerEditScreen(
    customerId: CustomerId? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CustomerEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(customerId) {
        customerId?.let { viewModel.loadCustomer(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomerEditEvent.Saved -> onSaved()
                is CustomerEditEvent.Error -> {}
                is CustomerEditEvent.ValidationFailed -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (customerId == null) "New Customer" else "Edit Customer",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving
                    ) {
                        Icon(Icons.Filled.Save, "Save")
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
            Spacer(Modifier.height(8.dp))

            SectionTitle("Contact Information")

            OutlinedTextField(
                value = state.form.firstName,
                onValueChange = { viewModel.updateForm { it.copy(firstName = it.firstName) } },
                label = { Text("First Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.errors.containsKey("firstName"),
                supportingText = { state.errors["firstName"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.lastName,
                onValueChange = { viewModel.updateForm { it.copy(lastName = it.lastName) } },
                label = { Text("Last Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.errors.containsKey("lastName"),
                supportingText = { state.errors["lastName"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.phone,
                onValueChange = { viewModel.updateForm { it.copy(phone = it.phone) } },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                isError = state.errors.containsKey("phone"),
                supportingText = { state.errors["phone"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            OutlinedTextField(
                value = state.form.email,
                onValueChange = { viewModel.updateForm { it.copy(email = it.email) } },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                isError = state.errors.containsKey("email"),
                supportingText = { state.errors["email"]?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
            )

            SectionTitle("Address")

            OutlinedTextField(
                value = state.form.address,
                onValueChange = { viewModel.updateForm { it.copy(address = it.address) } },
                label = { Text("Street Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.form.city,
                    onValueChange = { viewModel.updateForm { it.copy(city = it.city) } },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = state.form.state,
                    onValueChange = { viewModel.updateForm { it.copy(state = it.state) } },
                    label = { Text("State") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.form.zip,
                    onValueChange = { viewModel.updateForm { it.copy(zip = it.zip) } },
                    label = { Text("ZIP") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.PostalAddress, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = state.form.country,
                    onValueChange = { viewModel.updateForm { it.copy(country = it.country) } },
                    label = { Text("Country") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            SectionTitle("Details")

            OutlinedTextField(
                value = state.form.loyaltyNumber,
                onValueChange = { viewModel.updateForm { it.copy(loyaltyNumber = it.loyaltyNumber) } },
                label = { Text("Loyalty Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.group,
                onValueChange = { viewModel.updateForm { it.copy(group = it.group) } },
                label = { Text("Customer Group") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.tags,
                onValueChange = { viewModel.updateForm { it.copy(tags = it.tags) } },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.birthday,
                onValueChange = { viewModel.updateForm { it.copy(birthday = it.birthday) } },
                label = { Text("Birthday (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = state.form.notes,
                onValueChange = { viewModel.updateForm { it.copy(notes = it.notes) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.form.marketingConsent,
                    onCheckedChange = { checked ->
                        viewModel.updateForm { it.copy(marketingConsent = checked) }
                    }
                )
                Text("Marketing consent", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun CustomerAddScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    CustomerEditScreen(
        customerId = null,
        onBack = onBack,
        onSaved = onSaved
    )
}

@Preview(showBackground = true)
@Composable
private fun CustomerEditScreenPreview() {
    PosTheme {
        Surface {
            CustomerEditScreen(
                customerId = null,
                onBack = {},
                onSaved = {}
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CustomerEditScreenDarkPreview() {
    PosTheme {
        Surface {
            CustomerEditScreen(
                customerId = null,
                onBack = {},
                onSaved = {}
            )
        }
    }
}
