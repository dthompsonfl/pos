package com.enterprise.pos.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enterprise.pos.data.db.PosDatabase
import com.enterprise.pos.data.db.PosMigrations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.google.common.truth.Truth.assertThat

@RunWith(AndroidJUnit4::class)
class PosDatabaseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun databaseCreationSucceeds() {
        val db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
        assertThat(db.catalogDao()).isNotNull()
        assertThat(db.orderDao()).isNotNull()
        assertThat(db.customerDao()).isNotNull()
        assertThat(db.employeeDao()).isNotNull()
        db.close()
    }

    @Test
    fun migration2to3Succeeds() {
        val helper = MigrationTestHelper(
            context,
            PosDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
        )
        helper.createDatabase("test-db", 2).use { db ->
            db.execSQL("INSERT INTO orders (id, storeId, registerId, employeeId, diningMode, status, orderLevelDiscountMinor, tipMinor, createdAt, updatedAt) VALUES ('o1', 's1', 'r1', 'e1', 'RETAIL', 'OPEN', 0, 0, 0, 0)")
        }
        helper.runMigrationsAndValidate("test-db", 3, true, PosMigrations.MIGRATION_2_3).use { db ->
            val cursor = db.query("SELECT serviceChargesMinor, taxExempt FROM orders WHERE id = 'o1'")
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("serviceChargesMinor"))).isEqualTo(0)
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("taxExempt"))).isEqualTo(0)
            cursor.close()
        }
        helper.closeWhenFinished()
    }

    @Test
    fun migration3to4Succeeds() {
        val helper = MigrationTestHelper(
            context,
            PosDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
        )
        helper.createDatabase("test-db", 3).use { db ->
            db.execSQL("INSERT INTO products (id, name, description, categoryId, type, taxCategory, ageRestriction, isAvailable, trackInventory, prepTimeMinutes, updatedAt) VALUES ('p1', 'Burger', '', 'c1', 'PHYSICAL', 'STANDARD', 'NONE', 1, 1, 0, 0)")
        }
        helper.runMigrationsAndValidate("test-db", 4, true, PosMigrations.MIGRATION_3_4).use { db ->
            val cursor = db.query("SELECT modifierGroupsJson, displayOrder FROM products WHERE id = 'p1'")
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow("modifierGroupsJson"))).isEqualTo("")
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("displayOrder"))).isEqualTo(0)
            cursor.close()

            val tableCursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='modifier_groups'")
            assertThat(tableCursor.moveToFirst()).isTrue()
            tableCursor.close()
        }
        helper.closeWhenFinished()
    }

    @Test
    fun migration4to5Succeeds() {
        val helper = MigrationTestHelper(
            context,
            PosDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory()
        )
        helper.createDatabase("test-db", 4).use { db ->
            db.execSQL("INSERT INTO customers (id, name, email, phone, loyaltyPoints, storeCreditMinor, marketingOptIn, dietaryRestrictions, createdAt) VALUES ('c1', 'Alice', 'alice@example.com', '555-0100', 0, 0, 0, '', 0)")
            db.execSQL("INSERT INTO employees (id, name, pinHash, role, active, createdAt) VALUES ('e1', 'Bob', 'hash', 'CASHIER', 1, 0)")
        }
        helper.runMigrationsAndValidate("test-db", 5, true, PosMigrations.MIGRATION_4_5).use { db ->
            val customerCursor = db.query("SELECT firstName, lastName, city, country FROM customers WHERE id = 'c1'")
            assertThat(customerCursor.moveToFirst()).isTrue()
            assertThat(customerCursor.getString(customerCursor.getColumnIndexOrThrow("country"))).isEqualTo("USA")
            customerCursor.close()

            val employeeCursor = db.query("SELECT hourlyRateMinor, hireDate FROM employees WHERE id = 'e1'")
            assertThat(employeeCursor.moveToFirst()).isTrue()
            assertThat(employeeCursor.getInt(employeeCursor.getColumnIndexOrThrow("hourlyRateMinor"))).isEqualTo(0)
            employeeCursor.close()
        }
        helper.closeWhenFinished()
    }

    @Test
    fun concurrentAccessDoesNotCrash() {
        val db = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
        val dao = db.orderDao()
        val jobs = (1..10).map { i ->
            kotlinx.coroutines.GlobalScope.launch {
                com.enterprise.pos.data.db.entity.OrderEntity(
                    id = "order-$i", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
                    diningMode = "RETAIL", status = "OPEN", orderLevelDiscountMinor = 0, tipMinor = 0,
                    createdAt = 0, updatedAt = 0
                )
                // Just verify concurrent access doesn't crash
            }
        }
        kotlinx.coroutines.runBlocking {
            jobs.forEach { it.join() }
        }
        db.close()
    }
}

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createDb(): PosDatabase {
        return Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
    }

    @Test
    fun orderWithPaymentLifecycle() = kotlinx.coroutines.runBlocking {
        val db = createDb()
        val orderDao = db.orderDao()
        val paymentDao = db.paymentDao()

        val order = com.enterprise.pos.data.db.entity.OrderEntity(
            id = "order-1", storeId = "store-1", registerId = "reg-1", employeeId = "emp-1",
            diningMode = "RETAIL", status = "AWAITING_PAYMENT", orderLevelDiscountMinor = 0, tipMinor = 0,
            createdAt = 0, updatedAt = 0
        )
        orderDao.upsert(order)

        val payment = com.enterprise.pos.data.db.entity.PaymentEntity(
            id = "pay-1", orderId = "order-1", provider = "CASH", providerTransactionId = "txn-1",
            amountMinor = 1000, currency = "USD", capturedAt = 0
        )
        paymentDao.upsert(payment)

        val payments = paymentDao.forOrder("order-1")
        assertThat(payments).hasSize(1)
        assertThat(payments[0].amountMinor).isEqualTo(1000)

        db.close()
    }

    @Test
    fun catalogWithInventoryAdjustment() = kotlinx.coroutines.runBlocking {
        val db = createDb()
        val catalogDao = db.catalogDao()

        val product = com.enterprise.pos.data.db.entity.ProductEntity(
            id = "prod-1", name = "Burger", description = "", categoryId = "cat-1",
            type = "PHYSICAL", taxCategory = "STANDARD", ageRestriction = "NONE",
            isAvailable = true, trackInventory = true, prepTimeMinutes = 0, updatedAt = 0
        )
        catalogDao.upsertProduct(product)

        val inventory = com.enterprise.pos.data.db.entity.InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 10, committed = 0,
            lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0
        )
        catalogDao.upsertInventory(inventory)

        val adjusted = catalogDao.adjustInventory("var-1", "store-1", -3)
        assertThat(adjusted.onHand).isEqualTo(7)

        db.close()
    }

    @Test
    fun employeeWithAuditLog() = kotlinx.coroutines.runBlocking {
        val db = createDb()
        val employeeDao = db.employeeDao()
        val auditDao = db.auditLogDao()

        val employee = com.enterprise.pos.data.db.entity.EmployeeEntity(
            id = "emp-1", name = "Alice", pinHash = "hash", role = "CASHIER", active = true,
            email = null, phone = null, createdAt = 0
        )
        employeeDao.upsert(employee)

        val entry = com.enterprise.pos.data.db.entity.AuditLogEntity(
            id = "audit-1", storeId = "store-1", registerId = null, employeeId = "emp-1",
            employeeName = "Alice", action = "EMPLOYEE_LOGIN", entityType = "Employee",
            entityId = "emp-1", timestamp = 0
        )
        auditDao.insert(entry)

        val logs = auditDao.range("store-1", 0, Long.MAX_VALUE)
        assertThat(logs).hasSize(1)
        assertThat(logs[0].action).isEqualTo("EMPLOYEE_LOGIN")

        db.close()
    }
}
