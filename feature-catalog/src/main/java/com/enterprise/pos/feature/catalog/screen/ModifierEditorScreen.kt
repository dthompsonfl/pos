package com.enterprise.pos.feature.catalog.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.ModifierGroup
import com.enterprise.pos.domain.model.ModifierOption
import com.enterprise.pos.feature.catalog.state.ModifierEditorEvent
import com.enterprise.pos.feature.catalog.state.ModifierEditorState
import com.enterprise.pos.feature.catalog.state.ModifierEditorViewModel

@Composable
fun ModifierEditorScreen(
    modifierGroupId: ModifierGroupId?,
    onNavigateBack: () -> Unit,
    viewModel: ModifierEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(modifierGroupId) {
        viewModel.load(modifierGroupId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ModifierEditorEvent.Saved -> onNavigateBack()
                else -> {}
            }
        }
    }

    ModifierEditorContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSave = { viewModel.save() },
        onNameChange = { viewModel.updateName(it) },
        onDescriptionChange = { viewModel.updateDescription(it) },
        onRequiredChange = { viewModel.updateRequired(it) },
        onMaxSelectionsChange = { viewModel.updateMaxSelections(it) },
        onMinSelectionsChange = { viewModel.updateMinSelections(it) },
        onAddOption = { viewModel.addOption() },
        onRemoveOption = { viewModel.removeOption(it) },
        onUpdateOption = { optionId, name, priceAdjustment ->
            viewModel.updateOption(optionId, name = name, priceAdjustment = priceAdjustment)
        },
        onReorderOption = { from, to -> viewModel.reorderOptions(from, to) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModifierEditorContent(
    state: ModifierEditorState,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onRequiredChange: (Boolean) -> Unit,
    onMaxSelectionsChange: (Int) -> Unit,
    onMinSelectionsChange: (Int) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (String) -> Unit,
    onUpdateOption: (String, String, String) -> Unit,
    onReorderOption: (Int, Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.modifierGroup?.name?.isBlank() != false) "New Modifier Group" else state.modifierGroup.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val group = state.modifierGroup
            if (group != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Name
                        OutlinedTextField(
                            value = group.name,
                            onValueChange = onNameChange,
                            label = { Text("Group Name *") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = state.validationErrors.containsKey("name"),
                            supportingText = {
                                state.validationErrors["name"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Description
                        OutlinedTextField(
                            value = group.description,
                            onValueChange = onDescriptionChange,
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Required toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Required", style = MaterialTheme.typography.bodyLarge)
                                Text("Customer must select at least one option", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = group.isRequired,
                                onCheckedChange = onRequiredChange
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Min/Max selections
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = group.minSelections.toString(),
                                onValueChange = { onMinSelectionsChange(it.toIntOrNull() ?: 0) },
                                label = { Text("Min Selections") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = group.maxSelections.toString(),
                                onValueChange = { onMaxSelectionsChange(it.toIntOrNull() ?: 1) },
                                label = { Text("Max Selections") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Options section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = onAddOption) {
                                Icon(Icons.Filled.Add, contentDescription = "Add option")
                            }
                        }

                        state.validationErrors["options"]?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (group.options.isEmpty()) {
                            Text("No options yet. Click + to add one.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            group.options.forEachIndexed { index, option ->
                                OptionRow(
                                    option = option,
                                    index = index,
                                    totalCount = group.options.size,
                                    onNameChange = { onUpdateOption(option.id, it, option.priceAdjustment.asBigDecimal.toPlainString()) },
                                    onPriceChange = { onUpdateOption(option.id, option.name, it) },
                                    onRemove = { onRemoveOption(option.id) },
                                    onMoveUp = { if (index > 0) onReorderOption(index, index - 1) },
                                    onMoveDown = { if (index < group.options.size - 1) onReorderOption(index, index + 1) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.error != null) {
                            Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (state.saveResult == true) {
                            Text("Saved successfully", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: ModifierOption,
    index: Int,
    totalCount: Int,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = option.name,
                    onValueChange = onNameChange,
                    label = { Text("Option Name") },
                    modifier = Modifier.weight(2f)
                )
                OutlinedTextField(
                    value = option.priceAdjustment.asBigDecimal.toPlainString(),
                    onValueChange = onPriceChange,
                    label = { Text("Price Adj.") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ModifierEditorPreview() {
    MaterialTheme {
        ModifierEditorContent(
            state = ModifierEditorState(
                modifierGroup = ModifierGroup(
                    id = ModifierGroupId("mod-1"),
                    name = "Size",
                    description = "Choose your portion size",
                    options = listOf(
                        ModifierOption(id = "1", name = "Small", priceAdjustment = Money.of(-2.0)),
                        ModifierOption(id = "2", name = "Medium", priceAdjustment = Money.ZERO),
                        ModifierOption(id = "3", name = "Large", priceAdjustment = Money.of(2.0))
                    ),
                    isRequired = true,
                    maxSelections = 1,
                    minSelections = 1
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onSave = {},
            onNameChange = {},
            onDescriptionChange = {},
            onRequiredChange = {},
            onMaxSelectionsChange = {},
            onMinSelectionsChange = {},
            onAddOption = {},
            onRemoveOption = {},
            onUpdateOption = { _, _, _ -> },
            onReorderOption = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModifierEditorDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ModifierEditorContent(
            state = ModifierEditorState(
                modifierGroup = ModifierGroup(
                    id = ModifierGroupId("mod-1"),
                    name = "Size",
                    options = listOf(
                        ModifierOption(id = "1", name = "Small", priceAdjustment = Money.of(-1.0))
                    ),
                    isRequired = false
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onSave = {},
            onNameChange = {},
            onDescriptionChange = {},
            onRequiredChange = {},
            onMaxSelectionsChange = {},
            onMinSelectionsChange = {},
            onAddOption = {},
            onRemoveOption = {},
            onUpdateOption = { _, _, _ -> },
            onReorderOption = { _, _ -> }
        )
    }
}
