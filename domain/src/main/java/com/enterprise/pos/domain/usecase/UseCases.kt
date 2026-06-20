package com.enterprise.pos.domain.usecase

import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.Percent
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Customer
import com.enterprise.pos.domain.model.Discount
import com.enterprise.pos.domain.model.DiscountType
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.EmployeeRole
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.Product
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.EmployeeRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.service.CartEngine
import com.enterprise.pos.domain.service.TaxEngine

/**
 * Use cases orchestrate repository calls + domain services.
 * Each use case = one business operation. They are the only entry point
 * the UI has into the domain layer.
 */

class CreateOrderUseCase(
    private val orders: OrderRepository,
    private val clock: com.enterprise.pos.core.Clock
) {
    suspend operator fun invoke(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId,
        diningMode: DiningMode = DiningMode.RETAIL,
        tableId: TableId? = null,
        guestCount: Int = 0
    ): Result<Order> = orders.createOrder(storeId, registerId, employeeId, diningMode, tableId, guestCount)
}

class AddItemToOrderUseCase(
    private val orders: OrderRepository,
    private val catalog: CatalogRepository,
    private val cart: CartEngine
) {
    suspend operator fun invoke(
        order: Order,
        productId: com.enterprise.pos.core.ProductId,
        variantId: com.enterprise.pos.core.VariantId? = null,
        quantity: com.enterprise.pos.core.Quantity = com.enterprise.pos.core.Quantity.ONE,
        notes: String? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Order> {
        val product = catalog.getProduct(productId).getOrNull()
            ?: return Result.failure(AppError.NotFound("Product"))
        val variant = variantId?.let { id -> product.variants.firstOrNull { it.id == id } }
            ?: product.defaultVariant
            ?: return Result.failure(AppError.NotFound("Variant"))
        return cart.addItem(order, product, variant, quantity, notes, now = now)
            .flatMap { updated -> orders.updateOrder(updated) }
    }
}

class SendToKitchenUseCase(
    private val orders: OrderRepository,
    private val cart: CartEngine
) {
    suspend operator fun invoke(order: Order, now: Long = System.currentTimeMillis()): Result<Order> =
        cart.sendToKitchen(order, now).flatMap { orders.updateOrder(it) }
}

class FinalizeOrderForPaymentUseCase(
    private val orders: OrderRepository,
    private val taxEngine: TaxEngine,
    private val cart: CartEngine
) {
    suspend operator fun invoke(order: Order, now: Long = System.currentTimeMillis()): Result<Order> {
        val taxes = taxEngine.calculate(order).getOrNull() ?: emptyList()
        val withTaxes = order.copy(taxLines = taxes, status = OrderStatus.AWAITING_PAYMENT, updatedAt = now)
        return orders.updateOrder(withTaxes)
    }
}

class CloseOrderUseCase(private val orders: OrderRepository) {
    suspend operator fun invoke(orderId: OrderId): Result<Order> = orders.closeOrder(orderId)
}

class VoidOrderUseCase(
    private val orders: OrderRepository,
    private val cart: CartEngine
) {
    suspend operator fun invoke(order: Order, reason: String, now: Long = System.currentTimeMillis()): Result<Order> =
        cart.voidOrder(order, reason, now).flatMap { orders.updateOrder(it) }
}

class ApplyDiscountUseCase(
    private val orders: OrderRepository,
    private val cart: CartEngine,
    private val employees: EmployeeRepository
) {
    suspend operator fun invoke(
        order: Order,
        discount: Discount,
        requestingEmployee: EmployeeId,
        now: Long = System.currentTimeMillis()
    ): Result<Order> {
        // Manager approval check
        if (discount.requiresManagerApproval) {
            val employee = employees.get(requestingEmployee).getOrNull()
                ?: return Result.failure(AppError.Auth())
            val perms = employees.permissions(employee.role).getOrNull()
                ?: return Result.failure(AppError.Auth())
            val discountPercent = when (discount.type) {
                DiscountType.PERCENTAGE -> discount.percent.asDouble.toInt()
                DiscountType.FIXED -> 100
                DiscountType.BOGO -> 50
            }
            if (discountPercent > perms.maxDiscountPercent && !perms.canApplyDiscounts) {
                return Result.failure(AppError.Permission("discount > ${perms.maxDiscountPercent}%"))
            }
        }
        val updated = cart.addOrderLevelDiscount(order, discount, now).getOrNull()
            ?: return Result.failure(AppError.Generic("Discount failed"))
        return orders.updateOrder(updated)
    }
}

class LookupCustomerUseCase(private val customers: CustomerRepository) {
    suspend operator fun invoke(query: String): Result<List<Customer>> = customers.search(query)
    fun observe(query: String) = customers.observeCustomers(query)
}

class AddLoyaltyPointsUseCase(private val customers: CustomerRepository) {
    suspend operator fun invoke(customerId: CustomerId, points: Int): Result<Customer> =
        customers.addLoyaltyPoints(customerId, points)
}

class LoginUseCase(private val employees: EmployeeRepository) {
    suspend operator fun invoke(pin: String): Result<com.enterprise.pos.domain.model.Employee> = employees.login(pin)
}

class SearchProductsUseCase(private val catalog: CatalogRepository) {
    suspend operator fun invoke(query: String): Result<List<Product>> = catalog.search(query)
}

class ScanProductUseCase(private val catalog: CatalogRepository) {
    suspend operator fun invoke(barcode: String): Result<Product?> = catalog.getProductByBarcode(barcode)
}

class SeatTableUseCase(
    private val orders: OrderRepository,
    private val clock: com.enterprise.pos.core.Clock
) {
    suspend operator fun invoke(
        tableId: TableId,
        serverId: EmployeeId,
        storeId: StoreId,
        registerId: RegisterId,
        guestCount: Int
    ): Result<Order> {
        val order = orders.createOrder(
            storeId = storeId,
            registerId = registerId,
            employeeId = serverId,
            diningMode = DiningMode.DINE_IN_HOST_SEATED,
            tableId = tableId,
            guestCount = guestCount
        )
        order.onSuccess {
            orders.assignTable(it.id, tableId)
            orders.assignServer(tableId, serverId)
            orders.setTableStatus(tableId, com.enterprise.pos.domain.model.TableStatus.SEATED)
        }
        return order
    }
}

class StartTakeoutOrderUseCase(
    private val orders: OrderRepository
) {
    suspend operator fun invoke(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId,
        customerId: CustomerId? = null
    ): Result<Order> = orders.createOrder(
        storeId = storeId,
        registerId = registerId,
        employeeId = employeeId,
        diningMode = DiningMode.TO_GO,
        tableId = null,
        guestCount = 0
    ).also { result ->
        result.onSuccess { order ->
            customerId?.let { _ -> /* attach customer via separate use case */ }
        }
    }
}

/** Use case that returns the order total breakdown for display to the customer. */
class GetOrderTotalsUseCase(private val taxEngine: TaxEngine) {
    suspend operator fun invoke(order: Order): Result<com.enterprise.pos.domain.service.OrderTotalBreakdown> {
        val withTaxes = taxEngine.calculate(order).getOrNull() ?: emptyList()
        val enriched = order.copy(taxLines = withTaxes)
        return Result.success(com.enterprise.pos.domain.service.OrderTotalBreakdown.of(enriched))
    }
}
