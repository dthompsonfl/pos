package com.enterprise.pos.feature.catalog.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductVariant
import com.enterprise.pos.feature.catalog.state.CategoryDetailState
import com.enterprise.pos.feature.catalog.state.CategoryDetailViewModel

@Composable
fun CategoryDetailScreen(
    categoryId: CategoryId,
    onNavigateBack: () -> Unit,
    onEditCategory: (CategoryId) -> Unit,
    onProductClick: (ProductId) -> Unit,
    viewModel: CategoryDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(categoryId) {
        viewModel.load(categoryId)
    }

    CategoryDetailContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onEditCategory = { onEditCategory(categoryId) },
        onProductClick = onProductClick,
        onDeleteCategory = { viewModel.confirmDelete() },
        onReorderProducts = { from, to -> viewModel.reorderProducts(from, to) }
    )

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text("Delete Category") },
            text = { Text("Are you sure you want to delete ${state.category?.name ?: "this category"}? Products in this category will become uncategorized.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteCategory(categoryId) }) {
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
private fun CategoryDetailContent(
    state: CategoryDetailState,
    onNavigateBack: () -> Unit,
    onEditCategory: () -> Unit,
    onProductClick: (ProductId) -> Unit,
    onDeleteCategory: () -> Unit,
    onReorderProducts: (Int, Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.category?.name ?: "Category Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditCategory) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDeleteCategory) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
        } else if (state.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val category = state.category
                if (category != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(category.color)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Sort order: ${category.displayOrder}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Text(
                    text = "Products (${state.products.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (state.products.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No products in this category", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.products, key = { it.id.value }) { product ->
                            CategoryProductCard(
                                product = product,
                                index = state.products.indexOf(product),
                                totalCount = state.products.size,
                                onClick = { onProductClick(product.id) },
                                onMoveUp = {
                                    val idx = state.products.indexOf(product)
                                    if (idx > 0) onReorderProducts(idx, idx - 1)
                                },
                                onMoveDown = {
                                    val idx = state.products.indexOf(product)
                                    if (idx < state.products.size - 1) onReorderProducts(idx, idx + 1)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryProductCard(
    product: Product,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = product.isAvailable
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val price = product.defaultVariant?.price ?: Money.ZERO
            Text(
                text = price.format(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!product.isAvailable) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "Unavailable",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier)
                }
                Row {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryDetailPreview() {
    MaterialTheme {
        CategoryDetailContent(
            state = CategoryDetailState(
                category = Category(
                    id = CategoryId("cat-1"),
                    name = "Entrees",
                    color = 0xFF607D8B
                ),
                products = listOf(
                    Product(
                        id = ProductId("p1"),
                        name = "Burger",
                        categoryId = CategoryId("cat-1"),
                        variants = listOf(ProductVariant(VariantId("v1"), "Regular", "BRG-001", price = Money.of(9.99))),
                        defaultVariantId = VariantId("v1"),
                        displayOrder = 0
                    ),
                    Product(
                        id = ProductId("p2"),
                        name = "Salad",
                        categoryId = CategoryId("cat-1"),
                        variants = listOf(ProductVariant(VariantId("v2"), "Regular", "SLD-001", price = Money.of(7.99))),
                        defaultVariantId = VariantId("v2"),
                        displayOrder = 1
                    )
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onEditCategory = {},
            onProductClick = {},
            onDeleteCategory = {},
            onReorderProducts = { _, _ -> }
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryDetailDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        CategoryDetailContent(
            state = CategoryDetailState(
                category = Category(
                    id = CategoryId("cat-1"),
                    name = "Entrees",
                    color = 0xFF607D8B
                ),
                products = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onEditCategory = {},
            onProductClick = {},
            onDeleteCategory = {},
            onReorderProducts = { _, _ -> }
        )
    }
}
