package com.enterprise.pos.feature.catalog.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.feature.catalog.state.CatalogViewModel

@Composable
fun CatalogScreen(
    onProductClick: (ProductId) -> Unit,
    storeId: StoreId? = null,
    registerId: RegisterId? = null,
    employeeId: EmployeeId? = null,
    onCartReady: (OrderId) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadCategories() }

    LaunchedEffect(storeId, registerId) {
        if (storeId != null && registerId != null) {
            viewModel.bindRegisterContext(storeId, registerId)
        }
    }

    val pendingCartOrderId = state.pendingCartOrderId
    LaunchedEffect(pendingCartOrderId) {
        pendingCartOrderId?.let { orderId ->
            onCartReady(orderId)
            viewModel.consumeCartNavigation()
        }
    }

    state.message?.let { message ->
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2200)
            viewModel.dismissMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search menu…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true
        )
        if (state.isLoading || state.isAddingProduct) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.categories, key = { it.id.value }) { cat ->
                FilterChip(
                    selected = state.selectedCategory == cat.id,
                    onClick = { viewModel.selectCategory(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.products.isEmpty() && !state.isLoading -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No products found.")
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.products, key = { it.id.value }) { product ->
                        ProductTile(
                            product = product,
                            enabled = !state.isAddingProduct,
                            onClick = {
                                if (storeId != null && registerId != null && employeeId != null) {
                                    viewModel.addProductToActiveCart(
                                        productId = product.id,
                                        storeId = storeId,
                                        registerId = registerId,
                                        employeeId = employeeId
                                    )
                                } else {
                                    onProductClick(product.id)
                                }
                            }
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Snackbar(
                action = {
                    IconButton(onClick = viewModel::dismissError) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                }
            ) { Text(error) }
        }

        state.message?.let { message ->
            Spacer(Modifier.height(8.dp))
            Snackbar { Text(message) }
        }
    }
}

@Composable
private fun ProductTile(product: Product, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
        enabled = product.isAvailable && enabled
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.AddShoppingCart,
                    contentDescription = null,
                    tint = if (product.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val price = product.defaultVariant?.price ?: Money.ZERO
                Text(price.format(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (!product.isAvailable) {
                    Surface(color = MaterialTheme.colorScheme.error, shape = RoundedCornerShape(6.dp)) {
                        Text("86'd", color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
