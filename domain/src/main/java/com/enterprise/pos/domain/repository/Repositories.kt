package com.enterprise.pos.domain.repository

import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.RegisterId
import com.enterprise.pos.core.Result
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.TableId
import com.enterprise.pos.domain.model.Customer
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import kotlinx.coroutines.flow.Flow

interface OrderRepository {
    fun observeOpenOrders(storeId: StoreId): Flow<List<Order>>
    fun observeOrder(orderId: OrderId): Flow<Order?>
    fun observeOrdersByTable(tableId: TableId): Flow<List<Order>>
    fun observeTables(storeId: StoreId): Flow<List<RestaurantTable>>
    suspend fun createOrder(
        storeId: StoreId,
        registerId: RegisterId,
        employeeId: EmployeeId,
        diningMode: DiningMode,
        tableId: TableId? = null,
        guestCount: Int = 0
    ): Result<Order>

    suspend fun updateOrder(order: Order): Result<Order>
    suspend fun setStatus(orderId: OrderId, status: OrderStatus): Result<Order>
    suspend fun assignTable(orderId: OrderId, tableId: TableId?): Result<Order>
    suspend fun assignServer(tableId: TableId, serverId: EmployeeId?): Result<Unit>
    suspend fun setTableStatus(tableId: TableId, status: TableStatus): Result<Unit>
    suspend fun getById(orderId: OrderId): Result<Order?>
    suspend fun closeOrder(orderId: OrderId): Result<Order>
    suspend fun recentOrders(storeId: StoreId, limit: Int = 50): Result<List<Order>>

    /**
     * Transactionally mark an order as PAID:
     *   1. Persist the [payment] (provider, providerTransactionId, amount, tip, capturedAt).
     *   2. Decrement inventory for each line.
     *   3. Write audit log entry (PAYMENT_CAPTURED + ORDER_PAID + INVENTORY_DECREMENTED).
     *   4. Enqueue sync outbox event.
     *   5. Set order status to PAID with closedAt timestamp — only if amountDue == 0.
     *
     * Returns the updated order. Fails if the payment amount does not satisfy the amount due
     * (unless this is a partial payment within a split-tender flow).
     */
    suspend fun markPaid(
        orderId: OrderId,
        payment: com.enterprise.pos.domain.model.Payment,
        employeeId: EmployeeId
    ): Result<Order>

    /** Refund a previously captured payment. Updates the order's amountRefunded. */
    suspend fun refund(
        orderId: OrderId,
        refund: com.enterprise.pos.domain.model.Payment,
        reason: String,
        employeeId: EmployeeId
    ): Result<Order>

    /** Void an open (unpaid) order with a reason. Audited. */
    suspend fun voidOrder(orderId: OrderId, reason: String, employeeId: EmployeeId): Result<Order>
}

interface CustomerRepository {
    fun observeCustomers(query: String): Flow<List<Customer>>
    fun observeCustomer(id: CustomerId): Flow<Customer?>
    suspend fun get(id: CustomerId): Result<Customer?>
    suspend fun search(query: String): Result<List<Customer>>
    suspend fun upsert(customer: Customer): Result<Customer>
    suspend fun delete(id: CustomerId): Result<Unit>
    suspend fun addLoyaltyPoints(id: CustomerId, points: Int): Result<Customer>
    suspend fun adjustStoreCredit(id: CustomerId, delta: Money, reason: String): Result<Customer>
    suspend fun purchaseHistory(id: CustomerId): Result<List<Order>>
}

interface EmployeeRepository {
    fun observeEmployees(): Flow<List<com.enterprise.pos.domain.model.Employee>>
    fun observeEmployee(id: EmployeeId): Flow<com.enterprise.pos.domain.model.Employee?>
    suspend fun login(pin: String): Result<com.enterprise.pos.domain.model.Employee>
    suspend fun get(id: EmployeeId): Result<com.enterprise.pos.domain.model.Employee?>
    suspend fun upsert(employee: com.enterprise.pos.domain.model.Employee): Result<com.enterprise.pos.domain.model.Employee>
    suspend fun deactivate(id: EmployeeId): Result<Unit>
    suspend fun resetPin(id: EmployeeId, newPin: String): Result<com.enterprise.pos.domain.model.Employee>
    suspend fun permissions(role: com.enterprise.pos.domain.model.EmployeeRole): Result<com.enterprise.pos.domain.model.RolePermissions>
}

interface StoreRepository {
    fun observeStores(): Flow<List<com.enterprise.pos.domain.model.Store>>
    suspend fun current(): Result<com.enterprise.pos.domain.model.Store>
    suspend fun registers(storeId: StoreId): Result<List<com.enterprise.pos.domain.model.Register>>
}
