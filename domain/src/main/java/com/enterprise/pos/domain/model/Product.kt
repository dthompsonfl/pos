package com.enterprise.pos.domain.model

import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import kotlinx.serialization.Serializable

@Serializable
enum class ProductType { PHYSICAL, DIGITAL, SERVICE, BUNDLE }

@Serializable
enum class TaxCategory {
    STANDARD,
    FOOD_GROCERY,
    PREPARED_FOOD,
    MEDICINE,
    CLOTHING,
    EXEMPT,
    ZERO_RATED
}

@Serializable
enum class AgeRestriction { NONE, EIGHTEEN_PLUS, TWENTY_ONE_PLUS }

@Serializable
data class Category(
    val id: CategoryId,
    val name: String,
    val parentId: CategoryId? = null,
    val displayOrder: Int = 0,
    val iconKey: String? = null,
    val color: Long = 0xFF607D8B
)

@Serializable
data class ProductVariant(
    val id: VariantId,
    val name: String,
    val sku: String,
    val barcode: String? = null,
    val price: Money,
    val costPrice: Money? = null,
    val attributes: Map<String, String> = emptyMap()
)

@Serializable
data class Product(
    val id: ProductId,
    val name: String,
    val description: String = "",
    val categoryId: CategoryId,
    val type: ProductType = ProductType.PHYSICAL,
    val taxCategory: TaxCategory = TaxCategory.STANDARD,
    val ageRestriction: AgeRestriction = AgeRestriction.NONE,
    val imageUrl: String? = null,
    val defaultVariantId: VariantId? = null,
    val variants: List<ProductVariant> = emptyList(),
    val modifierGroupIds: List<ModifierGroupId> = emptyList(),
    val tags: List<String> = emptyList(),
    val trackInventory: Boolean = true,
    val isAvailable: Boolean = true,
    val displayOrder: Int = 0,
    val kitchenRoutingKey: String? = null,
    val prepTimeMinutes: Int = 0
) {
    val defaultVariant: ProductVariant?
        get() = variants.firstOrNull { it.id == defaultVariantId } ?: variants.firstOrNull()
}

@Serializable
data class InventorySnapshot(
    val variantId: VariantId,
    val storeId: StoreId,
    val onHand: Int,
    val committed: Int = 0,
    val available: Int = onHand - committed,
    val lowStockThreshold: Int = 5,
    val reorderPoint: Int = 10
) {
    val isLow: Boolean get() = available <= lowStockThreshold
    val needsReorder: Boolean get() = available <= reorderPoint
}

@Serializable
data class ModifierOption(
    val id: String, // UUID within the group
    val name: String,
    val priceAdjustment: Money = Money.ZERO,
    val isAvailable: Boolean = true
)

@Serializable
data class ModifierGroup(
    val id: ModifierGroupId,
    val name: String,
    val description: String = "",
    val options: List<ModifierOption> = emptyList(),
    val displayOrder: Int = 0,
    val isRequired: Boolean = false,
    val maxSelections: Int = 1,
    val minSelections: Int = 0
)

@Serializable
data class ProductModifierAssignment(
    val productId: ProductId,
    val modifierGroupId: ModifierGroupId
)
