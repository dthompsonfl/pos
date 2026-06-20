package com.enterprise.pos.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.enterprise.pos.ui.theme.PosTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ============================================================================
// TextField — Standard text field with label, helper text, error state
// ============================================================================
@Composable
private fun PosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "",
    helperText: String = "",
    errorText: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isError: Boolean = false,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (isError && errorText.isNotEmpty()) errorText else label
            },
        label = { if (label.isNotEmpty()) Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        leadingIcon = leadingIcon?.let {
            { Icon(imageVector = it, contentDescription = null) }
        },
        trailingIcon = trailingIcon?.let {
            {
                IconButton(
                    onClick = { onTrailingIconClick?.invoke() }
                ) {
                    Icon(imageVector = it, contentDescription = null)
                }
            }
        },
        isError = isError,
        readOnly = readOnly,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        supportingText = {
            when {
                isError && errorText.isNotEmpty() -> Text(errorText, color = MaterialTheme.colorScheme.error)
                helperText.isNotEmpty() -> Text(helperText)
            }
        }
    )
}

// ============================================================================
// NumberField — Numeric input with increment/decrement buttons
// ============================================================================
@Composable
fun PosNumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    min: Double = Double.NEGATIVE_INFINITY,
    max: Double = Double.POSITIVE_INFINITY,
    step: Double = 1.0,
    decimals: Int = 0,
    enabled: Boolean = true
) {
    val formatter = remember(decimals) {
        NumberFormat.getInstance().apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
        }
    }
    var text by remember(value) { mutableStateOf(formatter.format(value)) }
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            val parsed = newText.replace(",", "").toDoubleOrNull()
            isError = parsed == null || parsed < min || parsed > max
            if (parsed != null && !isError) {
                onValueChange(parsed)
            }
        },
        modifier = modifier.fillMaxWidth(),
        label = { if (label.isNotEmpty()) Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        isError = isError,
        enabled = enabled,
        singleLine = true,
        leadingIcon = {
            IconButton(
                onClick = {
                    val newValue = (value - step).coerceAtLeast(min)
                    onValueChange(newValue)
                    text = formatter.format(newValue)
                },
                enabled = enabled && value > min
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    val newValue = (value + step).coerceAtMost(max)
                    onValueChange(newValue)
                    text = formatter.format(newValue)
                },
                enabled = enabled && value < max
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    )
}

// ============================================================================
// DropdownField — Dropdown with search/filter capability
// ============================================================================
@Composable
fun <T> PosDropdownField(
    selectedItem: T?,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "Select...",
    itemText: (T) -> String = { it.toString() },
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val filteredItems = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter { itemText(it).contains(searchQuery, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedItem?.let(itemText) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { if (label.isNotEmpty()) Text(label) },
            placeholder = { Text(placeholder) },
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .semantics { contentDescription = label }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            filteredItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemText(item)) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                        searchQuery = ""
                        keyboardController?.hide()
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }

            if (filteredItems.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No results found", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {},
                    enabled = false
                )
            }
        }
    }
}

// ============================================================================
// DateTimeField — Date/time picker field
// ============================================================================
@Composable
fun PosDateTimeField(
    value: Date?,
    onValueChange: (Date) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    placeholder: String = "Select date",
    enabled: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val displayText = value?.let { formatter.format(it) } ?: ""

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { if (label.isNotEmpty()) Text(label) },
            placeholder = { Text(placeholder) },
            enabled = enabled,
            trailingIcon = {
                Icon(Icons.Default.CalendarToday, contentDescription = "Select date")
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { showPicker = true }
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.time
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onValueChange(Date(millis))
                        }
                        showPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ============================================================================
// SwitchField — Labeled switch
// ============================================================================
@Composable
fun PosSwitchField(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    description: String = "",
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = "$label switch"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

// ============================================================================
// CheckboxField — Labeled checkbox
// ============================================================================
@Composable
fun PosCheckboxField(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    description: String = "",
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = "$label checkbox"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (label.isNotEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// RadioGroupField — Radio button group
// ============================================================================
@Composable
fun <T> PosRadioGroupField(
    selectedOption: T?,
    options: List<T>,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    optionText: (T) -> String = { it.toString() },
    enabled: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onOptionSelected(option) }
                    .padding(vertical = 4.dp)
                    .semantics {
                        contentDescription = optionText(option)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option == selectedOption,
                    onClick = null,
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = optionText(option),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// ============================================================================
// FormSection — Grouped form section with title and divider
// ============================================================================
@Composable
fun FormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Divider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        if (description.isNotEmpty()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun FormComponentsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PosTextField(
                    value = "Sample",
                    onValueChange = {},
                    label = "Product Name",
                    helperText = "Enter the product name"
                )

                PosNumberField(
                    value = 10.0,
                    onValueChange = {},
                    label = "Quantity",
                    min = 0.0,
                    max = 100.0
                )

                PosSwitchField(
                    checked = true,
                    onCheckedChange = {},
                    label = "Taxable",
                    description = "Include sales tax in calculations"
                )

                PosCheckboxField(
                    checked = false,
                    onCheckedChange = {},
                    label = "Apply Discount",
                    description = "Use promotional pricing"
                )

                FormSection(
                    title = "Pricing",
                    description = "Configure product pricing options"
                ) {
                    PosTextField(
                        value = "",
                        onValueChange = {},
                        label = "Unit Price"
                    )
                }
            }
        }
    }
}
