package com.enterprise.pos.core

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Strongly-typed ID wrappers — prevents passing a CustomerId where an OrderId is expected.
 * Each ID is a UUID string at the value level, but the type system enforces correctness.
 */
@JvmInline
@Serializable
value class Id<T>(val value: String) {
    companion object {
        fun <T> random(): Id<T> = Id(UUID.randomUUID().toString())
    }
}

// Tag interfaces for the typed IDs.
interface IdTag
interface ProductTag : IdTag
interface VariantTag : IdTag
interface CategoryTag : IdTag
interface OrderTag : IdTag
interface OrderLineTag : IdTag
interface CustomerTag : IdTag
interface EmployeeTag : IdTag
interface StoreTag : IdTag
interface RegisterTag : IdTag
interface TableTag : IdTag
interface PaymentTag : IdTag
interface ReceiptTag : IdTag
interface ShiftTag : IdTag
interface ModifierGroupTag : IdTag

typealias ProductId = Id<ProductTag>
typealias VariantId = Id<VariantTag>
typealias CategoryId = Id<CategoryTag>
typealias OrderId = Id<OrderTag>
typealias OrderLineId = Id<OrderLineTag>
typealias CustomerId = Id<CustomerTag>
typealias EmployeeId = Id<EmployeeTag>
typealias StoreId = Id<StoreTag>
typealias RegisterId = Id<RegisterTag>
typealias TableId = Id<TableTag>
typealias PaymentId = Id<PaymentTag>
typealias ReceiptId = Id<ReceiptTag>
typealias ShiftId = Id<ShiftTag>
typealias ModifierGroupId = Id<ModifierGroupTag>

fun randomProductId(): ProductId = Id.random()
fun randomStoreId(): StoreId = Id.random()
fun randomRegisterId(): RegisterId = Id.random()
fun randomTableId(): TableId = Id.random()
fun randomVariantId(): VariantId = Id.random()
fun randomOrderId(): OrderId = Id.random()
fun randomOrderLineId(): OrderLineId = Id.random()
fun randomCustomerId(): CustomerId = Id.random()
fun randomEmployeeId(): EmployeeId = Id.random()
fun randomPaymentId(): PaymentId = Id.random()
fun randomReceiptId(): ReceiptId = Id.random()
fun randomShiftId(): ShiftId = Id.random()
fun randomModifierGroupId(): ModifierGroupId = Id.random()
