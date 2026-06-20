package com.enterprise.pos.domain.repository

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.ModifierGroup
import com.enterprise.pos.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    // Categories
    fun observeCategories(): Flow<List<Category>>
    fun observeCategory(categoryId: CategoryId): Flow<Category?>
    suspend fun getCategory(categoryId: CategoryId): Result<Category?>
    suspend fun upsertCategory(category: Category): Result<Category>
    suspend fun deleteCategory(categoryId: CategoryId): Result<Unit>

    // Products
    fun observeProducts(categoryId: CategoryId?): Flow<List<Product>>
    fun observeProduct(productId: ProductId): Flow<Product?>
    suspend fun getProduct(productId: ProductId): Result<Product?>
    suspend fun getProductByBarcode(barcode: String): Result<Product?>
    suspend fun search(query: String): Result<List<Product>>
    suspend fun upsertProduct(storeId: StoreId, product: Product): Result<Product>
    suspend fun deleteProduct(storeId: StoreId, productId: ProductId): Result<Unit>
    suspend fun setAvailable(storeId: StoreId, productId: ProductId, available: Boolean): Result<Unit>

    // Inventory
    fun observeInventory(storeId: StoreId, variantId: VariantId): Flow<InventorySnapshot?>
    suspend fun adjustInventory(storeId: StoreId, variantId: VariantId, delta: Int, reason: String): Result<InventorySnapshot>
    suspend fun upsertInventory(storeId: StoreId, inventory: InventorySnapshot): Result<InventorySnapshot>

    // Modifier groups
    fun observeModifierGroups(): Flow<List<ModifierGroup>>
    fun observeModifierGroup(id: ModifierGroupId): Flow<ModifierGroup?>
    suspend fun getModifierGroup(id: ModifierGroupId): Result<ModifierGroup?>
    suspend fun upsertModifierGroup(storeId: StoreId, modifierGroup: ModifierGroup): Result<ModifierGroup>
    suspend fun deleteModifierGroup(storeId: StoreId, id: ModifierGroupId): Result<Unit>
}
