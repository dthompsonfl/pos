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
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.*
import com.enterprise.pos.feature.catalog.state.ProductEditEvent
import com.enterprise.pos.feature.catalog.state.ProductEditState
import com.enterprise.pos.feature.catalog.state.ProductEditViewModel
import com.enterprise.pos.feature.catalog.state.ProductFormData
import com.enterprise.pos.feature.catalog.state.ProductVariantForm

@Composable
fun ProductEditScreen(
    productId: ProductId?,
    onNavigateBack: () -> Unit,
    viewModel: ProductEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(productId) {
        viewModel.load(productId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductEditEvent.Saved -> onNavigateBack()
                else -> {}
            }
        }
    }

    ProductEditContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onSave = { viewModel.save() },
        onFormChange = { update -> viewModel.updateForm(update) },
        onAddVariant = { viewModel.addVariant() },
        onRemoveVariant = { viewModel.removeVariant(it) },
        onUpdateVariant = { id, update -> viewModel.updateVariant(id, update) },
        onToggleModifierGroup = { viewModel.toggleModifierGroup(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductEditContent(
    state: ProductEditState,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    onFormChange: ((ProductFormData) -> ProductFormData) -> Unit,
    onAddVariant: () -> Unit,
    onRemoveVariant: (String) -> Unit,
    onUpdateVariant: (String, (ProductVariantForm) -> ProductVariantForm) -> Unit,
    onToggleModifierGroup: (ModifierGroupId) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.form.id.isBlank()) "New Product" else "Edit Product") },
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
            val form = state.form
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Basic Info
                    SectionTitle("Basic Information")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { newValue -> onFormChange { it.copy(name = newValue) } },
                        label = { Text("Product Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.validationErrors.containsKey("name"),
                        supportingText = {
                            state.validationErrors["name"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = form.sku,
                            onValueChange = { newValue -> onFormChange { it.copy(sku = newValue) } },
                            label = { Text("SKU *") },
                            modifier = Modifier.weight(1f),
                            isError = state.validationErrors.containsKey("sku")
                        )
                        OutlinedTextField(
                            value = form.barcode,
                            onValueChange = { newValue -> onFormChange { it.copy(barcode = newValue) } },
                            label = { Text("Barcode") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = form.description,
                        onValueChange = { newValue -> onFormChange { it.copy(description = newValue) } },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Category
                    SectionTitle("Category")
                    Spacer(modifier = Modifier.height(8.dp))
                    var categoryExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.categoryId?.let { catId ->
                                state.categories.find { it.id == catId }?.name ?: ""
                            } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category *") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            isError = state.validationErrors.containsKey("category"),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            state.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = {
                                        onFormChange { it.copy(categoryId = category.id) }
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Pricing
                    SectionTitle("Pricing")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = form.price,
                            onValueChange = { newValue -> onFormChange { it.copy(price = newValue) } },
                            label = { Text("Price *") },
                            modifier = Modifier.weight(1f),
                            isError = state.validationErrors.containsKey("price"),
                            supportingText = {
                                state.validationErrors["price"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                        )
                        OutlinedTextField(
                            value = form.cost,
                            onValueChange = { newValue -> onFormChange { it.copy(cost = newValue) } },
                            label = { Text("Cost") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    var taxExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = taxExpanded,
                        onExpandedChange = { taxExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.taxCategory.name.replace("_", " "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tax Category") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = taxExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = taxExpanded,
                            onDismissRequest = { taxExpanded = false }
                        ) {
                            TaxCategory.entries.forEach { tax ->
                                DropdownMenuItem(
                                    text = { Text(tax.name.replace("_", " ")) },
                                    onClick = {
                                        onFormChange { it.copy(taxCategory = tax) }
                                        taxExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Inventory
                    SectionTitle("Inventory")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Available for sale", style = MaterialTheme.typography.bodyLarge)
                        }
                        Switch(
                            checked = form.isAvailable,
                            onCheckedChange = { newValue -> onFormChange { it.copy(isAvailable = newValue) } }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Track inventory", style = MaterialTheme.typography.bodyLarge)
                            Text("Monitor stock levels for this product", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = form.trackInventory,
                            onCheckedChange = { newValue -> onFormChange { it.copy(trackInventory = newValue) } }
                        )
                    }
                    if (form.trackInventory) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = form.stockLevel,
                                onValueChange = { newValue -> onFormChange { it.copy(stockLevel = newValue) } },
                                label = { Text("Stock Level") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.lowStockThreshold,
                                onValueChange = { newValue -> onFormChange { it.copy(lowStockThreshold = newValue) } },
                                label = { Text("Low Stock Alert") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Image
                    SectionTitle("Image")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = form.imageUrl,
                        onValueChange = { newValue -> onFormChange { it.copy(imageUrl = newValue) } },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Modifier Groups
                    SectionTitle("Modifier Groups")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.allModifierGroups.isEmpty()) {
                        Text("No modifier groups available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.allModifierGroups.forEach { group ->
                            val selected = form.modifierGroupIds.contains(group.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { onToggleModifierGroup(group.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Variants
                    SectionTitle("Variants")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onAddVariant) {
                            Icon(Icons.Filled.Add, contentDescription = "Add variant")
                        }
                    }
                    state.validationErrors["variants"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (form.variants.isEmpty()) {
                        Text("No variants. A default variant will be created from the SKU and price above.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        form.variants.forEachIndexed { index, variant ->
                            VariantEditCard(
                                variant = variant,
                                onNameChange = { newValue -> onUpdateVariant(variant.id) { it.copy(name = newValue) } },
                                onSkuChange = { newValue -> onUpdateVariant(variant.id) { it.copy(sku = newValue) } },
                                onPriceChange = { newValue -> onUpdateVariant(variant.id) { it.copy(price = newValue) } },
                                onCostChange = { newValue -> onUpdateVariant(variant.id) { it.copy(cost = newValue) } },
                                onRemove = { onRemoveVariant(variant.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Error/Success messages
                    if (state.error != null) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (state.saveResult == true) {
                        Text("Product saved successfully", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VariantEditCard(
    variant: ProductVariantForm,
    onNameChange: (String) -> Unit,
    onSkuChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Variant", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = variant.name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = variant.sku,
                onValueChange = onSkuChange,
                label = { Text("SKU") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = variant.price,
                    onValueChange = onPriceChange,
                    label = { Text("Price") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = variant.cost,
                    onValueChange = onCostChange,
                    label = { Text("Cost") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductEditPreview() {
    MaterialTheme {
        ProductEditContent(
            state = ProductEditState(
                form = ProductFormData(
                    name = "Grilled Chicken Sandwich",
                    sku = "CHS-001",
                    barcode = "123456789",
                    description = "Marinated chicken breast with lettuce, tomato, and mayo on a brioche bun.",
                    price = "12.99",
                    cost = "5.50",
                    categoryId = CategoryId("cat-1"),
                    taxCategory = TaxCategory.PREPARED_FOOD,
                    isAvailable = true,
                    trackInventory = true,
                    stockLevel = "15",
                    lowStockThreshold = "5",
                    imageUrl = "https://example.com/chicken.jpg",
                    modifierGroupIds = listOf(ModifierGroupId("mod-1")),
                    variants = listOf(
                        ProductVariantForm(
                            id = "var-1",
                            name = "Regular",
                            sku = "CHS-001",
                            price = "12.99",
                            cost = "5.50"
                        )
                    )
                ),
                categories = listOf(
                    Category(id = CategoryId("cat-1"), name = "Entrees"),
                    Category(id = CategoryId("cat-2"), name = "Sides")
                ),
                allModifierGroups = listOf(
                    ModifierGroup(id = ModifierGroupId("mod-1"), name = "Size"),
                    ModifierGroup(id = ModifierGroupId("mod-2"), name = "Add-ons")
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onSave = {},
            onFormChange = {},
            onAddVariant = {},
            onRemoveVariant = {},
            onUpdateVariant = { _, _ -> },
            onToggleModifierGroup = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProductEditDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ProductEditContent(
            state = ProductEditState(
                form = ProductFormData(
                    name = "New Product",
                    sku = "",
                    price = "",
                    categoryId = null
                ),
                categories = emptyList(),
                allModifierGroups = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onSave = {},
            onFormChange = {},
            onAddVariant = {},
            onRemoveVariant = {},
            onUpdateVariant = { _, _ -> },
            onToggleModifierGroup = {}
        )
    }
}
