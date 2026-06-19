package com.enterprise.pos.domain.repository

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeProducts(categoryId: CategoryId?): Flow<List<Product>>
    fun observeProduct(productId: com.enterprise.pos.core.ProductId): Flow<Product?>
    fun observeInventory(storeId: StoreId, variantId: VariantId): Flow<InventorySnapshot?>
    suspend fun getProduct(productId: com.enterprise.pos.core.ProductId): Result<Product?>
    suspend fun getProductByBarcode(barcode: String): Result<Product?>
    suspend fun search(query: String): Result<List<Product>>
    suspend fun upsertProduct(product: Product): Result<Product>
    suspend fun adjustInventory(storeId: StoreId, variantId: VariantId, delta: Int, reason: String): Result<InventorySnapshot>
    suspend fun setAvailable(productId: com.enterprise.pos.core.ProductId, available: Boolean): Result<Unit>
}
