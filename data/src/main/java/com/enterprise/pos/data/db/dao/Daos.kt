package com.enterprise.pos.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.enterprise.pos.data.db.entity.CategoryEntity
import com.enterprise.pos.data.db.entity.CustomerEntity
import com.enterprise.pos.data.db.entity.DiscountEntity
import com.enterprise.pos.data.db.entity.EmployeeEntity
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.db.entity.OrderEntity
import com.enterprise.pos.data.db.entity.OrderLineEntity
import com.enterprise.pos.data.db.entity.PaymentEntity
import com.enterprise.pos.data.db.entity.ProductEntity
import com.enterprise.pos.data.db.entity.RegisterEntity
import com.enterprise.pos.data.db.entity.StoreEntity
import com.enterprise.pos.data.db.entity.SyncQueueEntity
import com.enterprise.pos.data.db.entity.TableEntity
import com.enterprise.pos.data.db.entity.TaxLineEntity
import com.enterprise.pos.data.db.entity.VariantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories ORDER BY displayOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM products WHERE categoryId = :categoryId OR :categoryId IS NULL ORDER BY name ASC")
    fun observeProducts(categoryId: String?): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeProduct(id: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProduct(id: String): ProductEntity?

    @Query("SELECT * FROM variants WHERE productId = :productId")
    suspend fun variantsFor(productId: String): List<VariantEntity>

    @Query("SELECT * FROM variants WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): VariantEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVariants(variants: List<VariantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("UPDATE products SET isAvailable = :available WHERE id = :id")
    suspend fun setAvailable(id: String, available: Boolean)

    @Query("SELECT * FROM inventory WHERE variantId = :variantId AND storeId = :storeId")
    fun observeInventory(variantId: String, storeId: String): Flow<InventoryEntity?>

    @Query("SELECT * FROM inventory WHERE variantId = :variantId AND storeId = :storeId")
    suspend fun getInventory(variantId: String, storeId: String): InventoryEntity?

    @Query("SELECT * FROM inventory WHERE storeId = :storeId AND (onHand - committed) <= lowStockThreshold")
    suspend fun lowStockFor(storeId: String): List<InventoryEntity>

    @Query("SELECT * FROM inventory WHERE storeId = :storeId")
    suspend fun allForStore(storeId: String): List<InventoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventory(inventory: InventoryEntity)

    @Transaction
    suspend fun adjustInventory(variantId: String, storeId: String, delta: Int): InventoryEntity {
        val current = getInventory(variantId, storeId)
            ?: InventoryEntity(variantId, storeId, 0, 0, 5, 10, System.currentTimeMillis())
        val updated = current.copy(onHand = current.onHand + delta, updatedAt = System.currentTimeMillis())
        upsertInventory(updated)
        return updated
    }
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE storeId = :storeId AND status NOT IN ('PAID','REFUNDED','VOIDED','CANCELLED') ORDER BY updatedAt DESC")
    fun observeOpenOrders(storeId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeOrder(id: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE tableId = :tableId ORDER BY updatedAt DESC")
    fun observeOrdersByTable(tableId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE storeId = :storeId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recentOrders(storeId: String, limit: Int): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun get(id: String): OrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, updatedAt = :now, closedAt = :closedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long, closedAt: Long?)

    @Query("UPDATE orders SET tableId = :tableId, updatedAt = :now WHERE id = :id")
    suspend fun setTable(id: String, tableId: String?, now: Long)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLines(lines: List<OrderLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTaxLines(lines: List<TaxLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscounts(discounts: List<DiscountEntity>)

    @Query("DELETE FROM order_lines WHERE orderId = :orderId")
    suspend fun deleteLinesFor(orderId: String)

    @Query("DELETE FROM tax_lines WHERE orderId = :orderId")
    suspend fun deleteTaxLinesFor(orderId: String)

    @Query("DELETE FROM discounts WHERE orderId = :orderId")
    suspend fun deleteDiscountsFor(orderId: String)

    @Query("SELECT * FROM order_lines WHERE orderId = :orderId ORDER BY displayOrder ASC")
    suspend fun linesFor(orderId: String): List<OrderLineEntity>

    @Query("SELECT * FROM tax_lines WHERE orderId = :orderId")
    suspend fun taxLinesFor(orderId: String): List<TaxLineEntity>

    @Query("SELECT * FROM discounts WHERE orderId = :orderId")
    suspend fun discountsFor(orderId: String): List<DiscountEntity>

    @Transaction
    suspend fun replaceOrderChildren(orderId: String, lines: List<OrderLineEntity>, taxLines: List<TaxLineEntity>, discounts: List<DiscountEntity>) {
        deleteLinesFor(orderId)
        deleteTaxLinesFor(orderId)
        deleteDiscountsFor(orderId)
        upsertLines(lines)
        upsertTaxLines(taxLines)
        upsertDiscounts(discounts)
    }
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE :query = '' OR name LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' ORDER BY name ASC")
    fun observe(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR email LIKE '%' || :q || '%'")
    suspend fun search(q: String): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity)

    @Query("UPDATE customers SET loyaltyPoints = loyaltyPoints + :delta, updatedAt = :now WHERE id = :id")
    suspend fun addLoyalty(id: String, delta: Int, now: Long)

    @Query("UPDATE customers SET storeCreditMinor = storeCreditMinor + :deltaMinor, updatedAt = :now WHERE id = :id")
    suspend fun adjustStoreCredit(id: String, deltaMinor: Long, now: Long)
}

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<EmployeeEntity>>

    /** All active employees — the repository iterates these to verify the PIN hash.
     *  PIN hashes cannot be queried directly because each has a unique salt. */
    @Query("SELECT * FROM employees WHERE active = 1")
    suspend fun allActive(): List<EmployeeEntity>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun get(id: String): EmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: EmployeeEntity)

    @Query("UPDATE employees SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores")
    fun observeAll(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores LIMIT 1")
    suspend fun first(): StoreEntity?

    @Query("SELECT * FROM registers WHERE storeId = :storeId")
    suspend fun registersFor(storeId: String): List<RegisterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStore(store: StoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRegisters(registers: List<RegisterEntity>)
}

@Dao
interface TableDao {
    @Query("SELECT * FROM tables WHERE storeId = :storeId ORDER BY section ASC, name ASC")
    fun observeForStore(storeId: String): Flow<List<TableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(table: TableEntity)

    @Query("UPDATE tables SET status = :status, currentOrderId = :orderId, currentGuestCount = :guests, serverId = :server WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, orderId: String?, guests: Int, server: String?)

    @Query("UPDATE tables SET serverId = :server WHERE id = :id")
    suspend fun updateServer(id: String, server: String?)

    @Query("UPDATE tables SET status = :status WHERE id = :id")
    suspend fun updateStatusOnly(id: String, status: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE orderId = :orderId")
    suspend fun forOrder(orderId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE capturedAt >= :from AND capturedAt <= :to ORDER BY capturedAt DESC")
    suspend fun between(from: Long, to: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE capturedAt >= :from AND capturedAt <= :to AND provider = :provider")
    suspend fun betweenByProvider(from: Long, to: Long, provider: String): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(payment: PaymentEntity)

    @Query("UPDATE payments SET refundedAmountMinor = refundedAmountMinor + :delta WHERE id = :id")
    suspend fun addRefund(id: String, delta: Long)

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE capturedAt >= :from AND capturedAt <= :to")
    suspend fun sumBetween(from: Long, to: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM payments WHERE capturedAt >= :from AND capturedAt <= :to AND provider = :provider")
    suspend fun sumBetweenByProvider(from: Long, to: Long, provider: String): Long

    @Query("SELECT COUNT(*) FROM payments WHERE capturedAt >= :from AND capturedAt <= :to")
    suspend fun countBetween(from: Long, to: Long): Int
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY enqueuedAt ASC LIMIT :batchSize")
    suspend fun pending(batchSize: Int = 50): List<SyncQueueEntity>

    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sync_queue SET attempts = attempts + 1, lastError = :err WHERE id = :id")
    suspend fun markFailed(id: Long, err: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observeCount(): Flow<Int>
}
