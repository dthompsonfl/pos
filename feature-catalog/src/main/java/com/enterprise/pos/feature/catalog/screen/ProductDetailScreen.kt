package com.enterprise.pos.feature.catalog.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.enterprise.pos.feature.catalog.state.ProductDetailEvent
import com.enterprise.pos.feature.catalog.state.ProductDetailState
import com.enterprise.pos.feature.catalog.state.ProductDetailViewModel

@Composable
fun ProductDetailScreen(
    productId: ProductId,
    onNavigateBack: () -> Unit,
    onEditProduct: (ProductId) -> Unit,
    onViewHistory: (ProductId) -> Unit = {},
    viewModel: ProductDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(productId) {
        viewModel.load(productId)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ProductDetailEvent.ProductDeleted -> onNavigateBack()
                else -> {}
            }
        }
    }

    ProductDetailContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onEditProduct = { onEditProduct(productId) },
        onToggleAvailability = { viewModel.toggleAvailability(productId) },
        onConfirmDelete = { viewModel.confirmDelete() },
        onViewHistory = { onViewHistory(productId) }
    )

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete ${state.product?.name ?: "this product"}? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProduct(productId) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetailContent(
    state: ProductDetailState,
    onNavigateBack: () -> Unit,
    onEditProduct: () -> Unit,
    onToggleAvailability: () -> Unit,
    onConfirmDelete: () -> Unit,
    onViewHistory: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.product?.name ?: "Product Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditProduct) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onConfirmDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        } else {
            val product = state.product
            if (product != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = if (product.isAvailable) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = if (product.isAvailable) "Available" else "Unavailable",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (product.isAvailable) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val defaultVariant = product.defaultVariant
                        if (defaultVariant != null) {
                            Text(
                                text = "SKU: ${defaultVariant.sku}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = defaultVariant.price.format(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = state.category?.name ?: "Unknown Category",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Availability toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Available for sale", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = product.isAvailable,
                                onCheckedChange = { onToggleAvailability() }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description
                        if (product.description.isNotBlank()) {
                            Text("Description", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(product.description, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Inventory
                        if (product.trackInventory) {
                            Text("Inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            val inventory = state.inventory
                            if (inventory != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    InventoryStat("On Hand", inventory.onHand.toString())
                                    InventoryStat("Available", inventory.available.toString())
                                    InventoryStat("Committed", inventory.committed.toString())
                                }
                                if (inventory.isLow) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "Low stock warning",
                                            modifier = Modifier.padding(8.dp),
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            } else {
                                Text("No inventory data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Variants
                        if (product.variants.size > 1) {
                            Text("Variants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            product.variants.forEach { variant ->
                                VariantRow(variant)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Modifier groups
                        if (state.modifierGroups.isNotEmpty()) {
                            Text("Modifier Groups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            state.modifierGroups.forEach { group ->
                                ModifierGroupRow(group)
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onViewHistory,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("View History")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VariantRow(variant: ProductVariant) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(variant.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("SKU: ${variant.sku}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(variant.price.format(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ModifierGroupRow(group: ModifierGroup) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(group.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            group.options.take(3).forEach { option ->
                val priceText = if (option.priceAdjustment != Money.ZERO) " (+${option.priceAdjustment.format()})" else ""
                Text(
                    "${option.name}$priceText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (group.options.size > 3) {
                Text(
                    "+${group.options.size - 3} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductDetailPreview() {
    MaterialTheme {
        ProductDetailContent(
            state = ProductDetailState(
                product = Product(
                    id = ProductId("prod-1"),
                    name = "Grilled Chicken Sandwich",
                    description = "Marinated chicken breast with lettuce, tomato, and mayo on a brioche bun.",
                    categoryId = CategoryId("cat-1"),
                    variants = listOf(
                        ProductVariant(
                            id = VariantId("var-1"),
                            name = "Regular",
                            sku = "CHS-001",
                            price = Money.of(12.99)
                        )
                    ),
                    defaultVariantId = VariantId("var-1"),
                    isAvailable = true,
                    trackInventory = true
                ),
                inventory = InventorySnapshot(
                    variantId = VariantId("var-1"),
                    storeId = com.enterprise.pos.core.StoreId("store-1"),
                    onHand = 15,
                    committed = 2,
                    lowStockThreshold = 5
                ),
                category = Category(id = CategoryId("cat-1"), name = "Entrees"),
                modifierGroups = listOf(
                    ModifierGroup(
                        id = ModifierGroupId("mod-1"),
                        name = "Size",
                        options = listOf(
                            com.enterprise.pos.domain.model.ModifierOption(id = "1", name = "Small", priceAdjustment = Money.of(-2.0)),
                            com.enterprise.pos.domain.model.ModifierOption(id = "2", name = "Large", priceAdjustment = Money.of(2.0))
                        )
                    )
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onEditProduct = {},
            onToggleAvailability = {},
            onConfirmDelete = {},
            onViewHistory = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProductDetailDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        ProductDetailContent(
            state = ProductDetailState(
                product = Product(
                    id = ProductId("prod-1"),
                    name = "Grilled Chicken Sandwich",
                    description = "Marinated chicken breast with lettuce, tomato, and mayo on a brioche bun.",
                    categoryId = CategoryId("cat-1"),
                    variants = listOf(
                        ProductVariant(
                            id = VariantId("var-1"),
                            name = "Regular",
                            sku = "CHS-001",
                            price = Money.of(12.99)
                        )
                    ),
                    defaultVariantId = VariantId("var-1"),
                    isAvailable = true
                ),
                inventory = InventorySnapshot(
                    variantId = VariantId("var-1"),
                    storeId = com.enterprise.pos.core.StoreId("store-1"),
                    onHand = 15,
                    committed = 2
                ),
                category = Category(id = CategoryId("cat-1"), name = "Entrees"),
                modifierGroups = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onEditProduct = {},
            onToggleAvailability = {},
            onConfirmDelete = {},
            onViewHistory = {}
        )
    }
}
