package com.enterprise.pos.data.repository

import com.enterprise.pos.data.db.CatalogDao
import com.enterprise.pos.data.db.SyncOutboxDao
import com.enterprise.pos.data.db.entity.InventoryEntity
import com.enterprise.pos.data.sync.SyncOperation
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

class InventoryManagementRepositoryImplTest {

    private val catalogDao = mockk<CatalogDao>(relaxed = true)
    private val syncOutboxDao = mockk<SyncOutboxDao>(relaxed = true)
    private val repository = InventoryManagementRepositoryImpl(catalogDao, syncOutboxDao)

    @Test
    fun `getInventory returns inventory for variant`() = runBlocking {
        val inventory = InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 10, committed = 2,
            lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0
        )
        coEvery { catalogDao.inventory("var-1", "store-1") } returns inventory

        val result = repository.getInventory("var-1", "store-1")
        assertThat(result).isEqualTo(inventory)
    }

    @Test
    fun `getInventory returns null when not found`() = runBlocking {
        coEvery { catalogDao.inventory("var-1", "store-1") } returns null

        val result = repository.getInventory("var-1", "store-1")
        assertThat(result).isNull()
    }

    @Test
    fun `adjustInventory increments onHand`() = runBlocking {
        val inventory = InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 10, committed = 0,
            lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0
        )
        coEvery { catalogDao.adjustInventory("var-1", "store-1", 5) } returns inventory.copy(onHand = 15)

        val result = repository.adjustInventory("var-1", "store-1", 5)
        assertThat(result.onHand).isEqualTo(15)
        coVerify { syncOutboxDao.enqueue(SyncOperation.INVENTORY_ADJUST, "var-1", "store-1") }
    }

    @Test
    fun `adjustInventory decrements onHand`() = runBlocking {
        val inventory = InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 10, committed = 0,
            lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0
        )
        coEvery { catalogDao.adjustInventory("var-1", "store-1", -3) } returns inventory.copy(onHand = 7)

        val result = repository.adjustInventory("var-1", "store-1", -3)
        assertThat(result.onHand).isEqualTo(7)
        coVerify { syncOutboxDao.enqueue(SyncOperation.INVENTORY_ADJUST, "var-1", "store-1") }
    }

    @Test
    fun `getLowStock returns items below threshold`() = runBlocking {
        val lowStock = listOf(
            InventoryEntity(variantId = "var-1", storeId = "store-1", onHand = 3, committed = 0, lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0),
            InventoryEntity(variantId = "var-2", storeId = "store-1", onHand = 2, committed = 0, lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0)
        )
        coEvery { catalogDao.lowStock("store-1", 5) } returns lowStock

        val result = repository.getLowStock("store-1", threshold = 5)
        assertThat(result).hasSize(2)
        assertThat(result[0].variantId).isEqualTo("var-1")
    }

    @Test
    fun `getLowStock returns empty when none below threshold`() = runBlocking {
        coEvery { catalogDao.lowStock("store-1", 5) } returns emptyList()

        val result = repository.getLowStock("store-1", threshold = 5)
        assertThat(result).isEmpty()
    }

    @Test
    fun `getInventoryForStore returns all inventory items`() = runBlocking {
        val inventory = listOf(
            InventoryEntity(variantId = "var-1", storeId = "store-1", onHand = 10, committed = 0, lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0),
            InventoryEntity(variantId = "var-2", storeId = "store-1", onHand = 20, committed = 0, lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0)
        )
        coEvery { catalogDao.inventoryForStore("store-1") } returns inventory

        val result = repository.getInventoryForStore("store-1")
        assertThat(result).hasSize(2)
    }

    @Test
    fun `setReorderPoint updates threshold and enqueues sync`() = runBlocking {
        coEvery { catalogDao.setReorderPoint("var-1", "store-1", 15) } returns 1

        val result = repository.setReorderPoint("var-1", "store-1", 15)
        assertThat(result).isEqualTo(1)
        coVerify { syncOutboxDao.enqueue(SyncOperation.INVENTORY_UPDATE, "var-1", "store-1") }
    }

    @Test
    fun `commitInventory removes from committed and enqueues sync`() = runBlocking {
        val inventory = InventoryEntity(
            variantId = "var-1", storeId = "store-1", onHand = 8, committed = 2,
            lowStockThreshold = 5, reorderPoint = 10, updatedAt = 0
        )
        coEvery { catalogDao.commitInventory("var-1", "store-1", 2) } returns inventory.copy(onHand = 6, committed = 0)

        val result = repository.commitInventory("var-1", "store-1", 2)
        assertThat(result.onHand).isEqualTo(6)
        assertThat(result.committed).isEqualTo(0)
        coVerify { syncOutboxDao.enqueue(SyncOperation.INVENTORY_COMMIT, "var-1", "store-1") }
    }
}
