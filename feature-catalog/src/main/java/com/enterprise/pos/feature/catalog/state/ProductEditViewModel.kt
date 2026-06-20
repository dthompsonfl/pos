package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CategoryId
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.core.randomProductId
import com.enterprise.pos.core.randomVariantId
import com.enterprise.pos.domain.model.AgeRestriction
import com.enterprise.pos.domain.model.InventorySnapshot
import com.enterprise.pos.domain.model.Category
import com.enterprise.pos.domain.model.ModifierGroup
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.model.ProductType
import com.enterprise.pos.domain.model.ProductVariant
import com.enterprise.pos.domain.model.TaxCategory
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductVariantForm(
    val id: String = "",
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val price: String = "",
    val cost: String = "",
    val attributes: Map<String, String> = emptyMap()
)

data class ProductFormData(
    val id: String = "",
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val description: String = "",
    val price: String = "",
    val cost: String = "",
    val categoryId: CategoryId? = null,
    val taxCategory: TaxCategory = TaxCategory.STANDARD,
    val isAvailable: Boolean = true,
    val trackInventory: Boolean = true,
    val stockLevel: String = "0",
    val lowStockThreshold: String = "5",
    val imageUrl: String = "",
    val modifierGroupIds: List<ModifierGroupId> = emptyList(),
    val variants: List<ProductVariantForm> = emptyList()
)

data class ProductEditState(
    val form: ProductFormData = ProductFormData(),
    val categories: List<Category> = emptyList(),
    val allModifierGroups: List<ModifierGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap(),
    val saveResult: Boolean? = null,
    val error: String? = null
)

sealed class ProductEditEvent {
    data class Saved(val productId: ProductId) : ProductEditEvent()
    data class Error(val message: String) : ProductEditEvent()
}

@HiltViewModel
class ProductEditViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val storeRepo: StoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProductEditState(isLoading = true))
    val state: StateFlow<ProductEditState> = _state.asStateFlow()

    private val _events = Channel<ProductEditEvent>(Channel.BUFFERED)
    val events: Flow<ProductEditEvent> = _events.receiveAsFlow()

    private var storeId: StoreId? = null

    init {
        viewModelScope.launch {
            storeRepo.current().onSuccess { store ->
                storeId = store.id
            }
        }
        repository.observeCategories()
            .onEach { cats ->
                _state.value = _state.value.copy(categories = cats)
            }
            .launchIn(viewModelScope)
        repository.observeModifierGroups()
            .onEach { groups ->
                _state.value = _state.value.copy(allModifierGroups = groups)
            }
            .launchIn(viewModelScope)
    }

    fun load(productId: ProductId?) {
        if (productId == null) {
            _state.value = _state.value.copy(isLoading = false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repository.getProduct(productId).onSuccess { product ->
                if (product == null) {
                    _state.value = _state.value.copy(isLoading = false, error = "Product not found")
                    return@onSuccess
                }
                val defaultVariant = product.defaultVariant
                val form = ProductFormData(
                    id = product.id.value,
                    name = product.name,
                    sku = defaultVariant?.sku ?: "",
                    barcode = defaultVariant?.barcode ?: "",
                    description = product.description,
                    price = defaultVariant?.price?.asBigDecimal?.toPlainString() ?: "",
                    cost = defaultVariant?.costPrice?.asBigDecimal?.toPlainString() ?: "",
                    categoryId = product.categoryId,
                    taxCategory = product.taxCategory,
                    isAvailable = product.isAvailable,
                    trackInventory = product.trackInventory,
                    imageUrl = product.imageUrl ?: "",
                    modifierGroupIds = product.modifierGroupIds,
                    variants = product.variants.map { v ->
                        ProductVariantForm(
                            id = v.id.value,
                            name = v.name,
                            sku = v.sku,
                            barcode = v.barcode ?: "",
                            price = v.price.asBigDecimal.toPlainString(),
                            cost = v.costPrice?.asBigDecimal?.toPlainString() ?: "",
                            attributes = v.attributes
                        )
                    }
                )
                _state.value = _state.value.copy(form = form, isLoading = false)

                // Load inventory for default variant
                val sid = storeId
                if (sid != null && defaultVariant != null) {
                    repository.observeInventory(sid, defaultVariant.id).collect { inv ->
                        _state.value = _state.value.copy(
                            form = _state.value.form.copy(
                                stockLevel = (inv?.onHand ?: 0).toString(),
                                lowStockThreshold = (inv?.lowStockThreshold ?: 5).toString()
                            )
                        )
                    }
                }
            }.onFailure { error ->
                _state.value = _state.value.copy(isLoading = false, error = error.message)
            }
        }
    }

    fun updateForm(update: (ProductFormData) -> ProductFormData) {
        _state.value = _state.value.copy(form = update(_state.value.form))
    }

    fun addVariant() {
        val newVariant = ProductVariantForm(
            id = randomVariantId().value,
            name = "New Variant",
            sku = "",
            price = "0.00"
        )
        _state.value = _state.value.copy(
            form = _state.value.form.copy(variants = _state.value.form.variants + newVariant)
        )
    }

    fun removeVariant(variantId: String) {
        _state.value = _state.value.copy(
            form = _state.value.form.copy(
                variants = _state.value.form.variants.filter { it.id != variantId }
            )
        )
    }

    fun updateVariant(variantId: String, update: (ProductVariantForm) -> ProductVariantForm) {
        _state.value = _state.value.copy(
            form = _state.value.form.copy(
                variants = _state.value.form.variants.map {
                    if (it.id == variantId) update(it) else it
                }
            )
        )
    }

    fun toggleModifierGroup(groupId: ModifierGroupId) {
        val current = _state.value.form.modifierGroupIds
        val updated = if (current.contains(groupId)) {
            current.filter { it != groupId }
        } else {
            current + groupId
        }
        _state.value = _state.value.copy(
            form = _state.value.form.copy(modifierGroupIds = updated)
        )
    }

    fun save() {
        val form = _state.value.form
        val errors = validate(form)
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(validationErrors = errors)
            return
        }
        val sid = storeId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, validationErrors = emptyMap())

            val price = Money.parse(form.price)
            val cost = form.cost.takeIf { it.isNotBlank() }?.let { Money.parse(it) }

            val defaultVariantId = if (form.id.isBlank()) randomVariantId()
            else {
                val existing = repository.getProduct(ProductId(form.id)).getOrNull()
                existing?.defaultVariantId ?: randomVariantId()
            }

            val variants = form.variants.map { vForm ->
                ProductVariant(
                    id = VariantId(vForm.id),
                    name = vForm.name,
                    sku = vForm.sku,
                    barcode = vForm.barcode.takeIf { it.isNotBlank() },
                    price = Money.parse(vForm.price),
                    costPrice = vForm.cost.takeIf { it.isNotBlank() }?.let { Money.parse(it) },
                    attributes = vForm.attributes
                )
            }.ifEmpty {
                listOf(
                    ProductVariant(
                        id = defaultVariantId,
                        name = form.name,
                        sku = form.sku,
                        barcode = form.barcode.takeIf { it.isNotBlank() },
                        price = price,
                        costPrice = cost,
                        attributes = emptyMap()
                    )
                )
            }

            val product = Product(
                id = if (form.id.isBlank()) randomProductId() else ProductId(form.id),
                name = form.name,
                description = form.description,
                categoryId = form.categoryId ?: CategoryId(""),
                type = ProductType.PHYSICAL,
                taxCategory = form.taxCategory,
                ageRestriction = AgeRestriction.NONE,
                imageUrl = form.imageUrl.takeIf { it.isNotBlank() },
                defaultVariantId = variants.firstOrNull()?.id,
                variants = variants,
                modifierGroupIds = form.modifierGroupIds,
                tags = emptyList(),
                trackInventory = form.trackInventory,
                isAvailable = form.isAvailable
            )

            repository.upsertProduct(sid, product)
                .onSuccess { savedProduct ->
                    // Update inventory for default variant if tracking
                    if (form.trackInventory && variants.isNotEmpty()) {
                        val defaultVar = savedProduct.defaultVariant
                        if (defaultVar != null) {
                            val targetStock = form.stockLevel.toIntOrNull() ?: 0
                            val targetThreshold = form.lowStockThreshold.toIntOrNull() ?: 5
                            val currentInv = runCatching {
                                repository.observeInventory(sid, defaultVar.id).first()
                            }.getOrNull()
                            val currentStock = currentInv?.onHand ?: 0
                            val delta = targetStock - currentStock
                            if (delta != 0) {
                                repository.adjustInventory(sid, defaultVar.id, delta, "Product edit stock adjustment")
                            }
                            repository.upsertInventory(
                                sid,
                                InventorySnapshot(
                                    variantId = defaultVar.id,
                                    storeId = sid,
                                    onHand = targetStock,
                                    committed = currentInv?.committed ?: 0,
                                    lowStockThreshold = targetThreshold,
                                    reorderPoint = currentInv?.reorderPoint ?: 10
                                )
                            )
                        }
                    }
                    _state.value = _state.value.copy(isSaving = false, saveResult = true)
                    _events.send(ProductEditEvent.Saved(savedProduct.id))
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isSaving = false, saveResult = false, error = error.message)
                    _events.send(ProductEditEvent.Error(error.message))
                }
        }
    }

    private fun validate(form: ProductFormData): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.name.isBlank()) errors["name"] = "Name is required"
        if (form.sku.isBlank()) errors["sku"] = "SKU is required"
        if (form.price.isBlank() || runCatching { Money.parse(form.price) }.isFailure) {
            errors["price"] = "Valid price is required"
        } else {
            val price = runCatching { Money.parse(form.price) }.getOrNull()
            if (price != null && !price.isPositive()) errors["price"] = "Price must be positive"
        }
        if (form.categoryId == null) errors["category"] = "Category is required"
        if (form.variants.any { it.sku.isBlank() }) errors["variants"] = "All variants must have a SKU"
        val skus = form.variants.map { it.sku }
        if (skus.toSet().size != skus.size) errors["sku"] = "SKUs must be unique"
        return errors
    }
}
