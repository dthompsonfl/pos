package com.enterprise.pos.data.repository

import com.enterprise.pos.core.Clock
import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.ProductId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.VariantId
import com.enterprise.pos.data.db.dao.CatalogDao
import com.enterprise.pos.data.db.dao.InventoryAdjustmentDao
import com.enterprise.pos.data.db.dao.InventoryTransferDao
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.db.entity.ProductEntity
import com.enterprise.pos.data.db.entity.VariantEntity
import com.enterprise.pos.data.sync.SyncOutboxDao
import com.enterprise.pos.domain.model.AdjustmentReason
import com.enterprise.pos.domain.model.InventoryAdjustment
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.match
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class InventoryManagementRepositoryImplTest {

    private val catalogDao = mockk<CatalogDao>(relaxed = true)
    private val adjustmentDao = mockk<InventoryAdjustmentDao>(relaxed = true)
    private val transferDao = mockk<InventoryTransferDao>(relaxed = true)
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val clock = object : Clock { override fun now() = 1700000000000L }

    private val repository = InventoryManagementRepositoryImpl(
        catalogDao = catalogDao,
        adjustmentDao = adjustmentDao,
        transferDao = transferDao,
        syncOutboxDao = syncOutboxDao,
        clock = clock
    )

    private val storeId = StoreId("store-1")
    private val variantId = VariantId("var-1")

    @Test
    fun `observeLowStock maps inventory snapshots`() = runBlocking {
        coEvery { catalogDao.lowStockFor("store-1") } returns listOf(
            InventoryEntity(
                variantId = "var-1",
                storeId = "store-1",
                onHand = 3,
                committed = 1,
                lowStockThreshold = 5,
                reorderPoint = 10,
                updatedAt = clock.now()
            )
        )

        val result = repository.observeLowStock(storeId).first()

        assertThat(result).hasSize(1)
        assertThat(result[0].variantId).isEqualTo(variantId)
        assertThat(result[0].available).isEqualTo(2)
        assertThat(result[0].isLow).isTrue()
    }

    @Test
    fun `adjust persists adjustment updates inventory and queues sync`() = runBlocking {
        val adjustment = InventoryAdjustment(
            id = Id("adj-1"),
            variantId = variantId,
            storeId = storeId,
            delta = 5,
            reason = AdjustmentReason.RECEIVED,
            notes = "Restock",
            employeeId = EmployeeId("emp-1"),
            timestamp = clock.now(),
            unitCost = Money.of(4.00)
        )
        coEvery { adjustmentDao.insert(any()) } returns Unit
        coEvery { catalogDao.adjustInventory("var-1", "store-1", 5) } returns InventoryEntity(
            variantId = "var-1",
            storeId = "store-1",
            onHand = 15,
            committed = 0,
            lowStockThreshold = 5,
            reorderPoint = 10,
            updatedAt = clock.now()
        )
        coEvery { syncOutboxDao.upsert(any()) } returns Unit

        val result = repository.adjust(adjustment)

        assertThat(result.isSuccess()).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(adjustment)
        coVerify { adjustmentDao.insert(any()) }
        coVerify { catalogDao.adjustInventory("var-1", "store-1", 5) }
        coVerify { syncOutboxDao.upsert(any()) }
    }

    @Test
    fun `getInventoryItem joins inventory variant and product details`() = runBlocking {
        coEvery { catalogDao.getInventory("var-1", "store-1") } returns InventoryEntity(
            variantId = "var-1",
            storeId = "store-1",
            onHand = 10,
            committed = 2,
            lowStockThreshold = 5,
            reorderPoint = 10,
            updatedAt = clock.now()
        )
        coEvery { catalogDao.variantsFor("var-1") } returns listOf(
            VariantEntity(
                id = "var-1",
                productId = "prod-1",
                name = "Default",
                sku = "BUR001",
                barcode = null,
                priceMinor = 1299,
                costPriceMinor = 400,
                attributesJson = "{}"
            )
        )
        coEvery { catalogDao.getProduct("prod-1") } returns ProductEntity(
            id = "prod-1",
            name = "Burger",
            description = "",
            categoryId = "cat-1",
            type = "PHYSICAL",
            taxCategory = "STANDARD",
            ageRestriction = "NONE",
            imageUrl = null,
            defaultVariantId = "var-1",
            tags = "",
            trackInventory = true,
            isAvailable = true,
            kitchenRoutingKey = null,
            prepTimeMinutes = 0,
            updatedAt = clock.now()
        )

        val result = repository.getInventoryItem(variantId, storeId)

        assertThat(result.isSuccess()).isTrue()
        val item = result.getOrThrow()
        assertThat(item?.productName).isEqualTo("Burger")
        assertThat(item?.sku).isEqualTo("BUR001")
        assertThat(item?.available).isEqualTo(8)
        assertThat(item?.unitCost).isEqualTo(Money.ofMinor(400))
    }

    @Test
    fun `setReorderPoint creates inventory row when missing`() = runBlocking {
        coEvery { catalogDao.getInventory("var-1", "store-1") } returns null
        coEvery { catalogDao.upsertInventory(any()) } returns Unit

        val result = repository.setReorderPoint(variantId, storeId, point = 15, qty = 30)

        assertThat(result.isSuccess()).isTrue()
        coVerify {
            catalogDao.upsertInventory(match { entity ->
                entity.variantId == "var-1" &&
                    entity.storeId == "store-1" &&
                    entity.reorderPoint == 15 &&
                    entity.lowStockThreshold == 15
            })
        }
    }
}
