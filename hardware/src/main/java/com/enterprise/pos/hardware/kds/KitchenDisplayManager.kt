package com.enterprise.pos.hardware.kds

import android.content.Context
import android.media.RingtoneManager
import com.enterprise.pos.core.AppError
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.Result
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderLineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kitchen Display System (KDS) interface.
 *
 * Manages orders sent to the kitchen, tracks preparation status, and supports
 * bumping, recalling, and color-coded aging based on elapsed time.
 */
interface KitchenDisplayManager {
    val orders: StateFlow<List<KitchenOrder>>

    suspend fun sendOrder(order: Order): Result<Unit>
    suspend fun bumpOrder(orderId: OrderId): Result<Unit>
    suspend fun recallOrder(orderId: OrderId): Result<Unit>
    suspend fun clearOrder(orderId: OrderId): Result<Unit>
    fun getOrderColor(order: KitchenOrder): KitchenColor
    suspend fun playNewOrderSound(): Result<Unit>
}

/** Kitchen order representation optimized for KDS display. */
data class KitchenOrder(
    val orderId: OrderId,
    val items: List<KitchenItem>,
    val sentAt: Long,
    val bumpedAt: Long? = null,
    val recalledAt: Long? = null,
    val tableName: String? = null,
    val guestCount: Int = 0,
    val notes: String? = null
) {
    val elapsedMinutes: Long
        get() = (System.currentTimeMillis() - sentAt) / 60_000

    val isBumped: Boolean get() = bumpedAt != null && (recalledAt == null || bumpedAt > recalledAt)
    val isActive: Boolean get() = !isBumped
}

data class KitchenItem(
    val name: String,
    val quantity: Int,
    val notes: String? = null,
    val routingKey: String? = null
)

enum class KitchenColor {
    GREEN, YELLOW, ORANGE, RED
}

/**
 * In-memory KDS implementation with color-coded aging and sound alerts.
 * Suitable for single-device or small-kitchen deployments.
 */
class InMemoryKitchenDisplayManager(
    private val context: Context,
    private val logger: com.enterprise.pos.core.Logger
) : KitchenDisplayManager {

    private val _orders = MutableStateFlow<List<KitchenOrder>>(emptyList())
    override val orders: StateFlow<List<KitchenOrder>> = _orders.asStateFlow()

    override suspend fun sendOrder(order: Order): Result<Unit> = Result.catching {
        val items = order.lines
            .filter { it.lineType == OrderLineType.ITEM }
            .map { line ->
                KitchenItem(
                    name = line.name,
                    quantity = line.quantity.asInt,
                    notes = line.notes,
                    routingKey = line.kitchenRoutingKey
                )
            }
            .filter { it.quantity > 0 }

        if (items.isEmpty()) {
            throw IllegalStateException("No kitchen-routable items in order ${order.id.value}")
        }

        val kitchenOrder = KitchenOrder(
            orderId = order.id,
            items = items,
            sentAt = System.currentTimeMillis(),
            tableName = order.tableName,
            guestCount = order.guestCount,
            notes = order.notes
        )

        _orders.value = _orders.value + kitchenOrder
        logger.i(TAG, "Order sent to kitchen: ${order.id.value} (${items.size} items)")
        playNewOrderSound()
    }

    override suspend fun bumpOrder(orderId: OrderId): Result<Unit> = Result.catching {
        val existing = _orders.value.find { it.orderId == orderId }
            ?: throw IllegalStateException("Order ${orderId.value} not found on KDS")

        val updated = existing.copy(bumpedAt = System.currentTimeMillis())
        _orders.value = _orders.value.map {
            if (it.orderId == orderId) updated else it
        }
        logger.i(TAG, "Order bumped: ${orderId.value}")
    }

    override suspend fun recallOrder(orderId: OrderId): Result<Unit> = Result.catching {
        val existing = _orders.value.find { it.orderId == orderId }
            ?: throw IllegalStateException("Order ${orderId.value} not found on KDS")

        val updated = existing.copy(recalledAt = System.currentTimeMillis())
        _orders.value = _orders.value.map {
            if (it.orderId == orderId) updated else it
        }
        logger.i(TAG, "Order recalled: ${orderId.value}")
    }

    override suspend fun clearOrder(orderId: OrderId): Result<Unit> = Result.catching {
        _orders.value = _orders.value.filter { it.orderId != orderId }
        logger.i(TAG, "Order cleared from KDS: ${orderId.value}")
    }

    override fun getOrderColor(order: KitchenOrder): KitchenColor {
        val elapsed = order.elapsedMinutes
        return when {
            elapsed < 5 -> KitchenColor.GREEN
            elapsed < 10 -> KitchenColor.YELLOW
            elapsed < 15 -> KitchenColor.ORANGE
            else -> KitchenColor.RED
        }
    }

    override suspend fun playNewOrderSound(): Result<Unit> = Result.catching {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone.play()
            logger.d(TAG, "Played new order sound")
        } catch (e: Exception) {
            logger.w(TAG, "Failed to play sound: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "InMemoryKDS"
    }
}

/**
 * Simulated KDS for development. Identical to [InMemoryKitchenDisplayManager] but
 * without sound alerts to avoid noise in test environments.
 */
class SimulatedKitchenDisplayManager(
    private val logger: com.enterprise.pos.core.Logger
) : KitchenDisplayManager {

    private val _orders = MutableStateFlow<List<KitchenOrder>>(emptyList())
    override val orders: StateFlow<List<KitchenOrder>> = _orders.asStateFlow()

    override suspend fun sendOrder(order: Order): Result<Unit> = Result.catching {
        val items = order.lines
            .filter { it.lineType == OrderLineType.ITEM }
            .map { line ->
                KitchenItem(
                    name = line.name,
                    quantity = line.quantity.asInt,
                    notes = line.notes,
                    routingKey = line.kitchenRoutingKey
                )
            }

        val kitchenOrder = KitchenOrder(
            orderId = order.id,
            items = items,
            sentAt = System.currentTimeMillis(),
            tableName = order.tableName,
            guestCount = order.guestCount
        )

        _orders.value = _orders.value + kitchenOrder
        logger.i(TAG, "[SIMULATED] Order sent to kitchen: ${order.id.value}")
    }

    override suspend fun bumpOrder(orderId: OrderId): Result<Unit> = Result.catching {
        _orders.value = _orders.value.map {
            if (it.orderId == orderId) it.copy(bumpedAt = System.currentTimeMillis()) else it
        }
        logger.i(TAG, "[SIMULATED] Order bumped: ${orderId.value}")
    }

    override suspend fun recallOrder(orderId: OrderId): Result<Unit> = Result.catching {
        _orders.value = _orders.value.map {
            if (it.orderId == orderId) it.copy(recalledAt = System.currentTimeMillis()) else it
        }
        logger.i(TAG, "[SIMULATED] Order recalled: ${orderId.value}")
    }

    override suspend fun clearOrder(orderId: OrderId): Result<Unit> = Result.catching {
        _orders.value = _orders.value.filter { it.orderId != orderId }
        logger.i(TAG, "[SIMULATED] Order cleared: ${orderId.value}")
    }

    override fun getOrderColor(order: KitchenOrder): KitchenColor {
        val elapsed = order.elapsedMinutes
        return when {
            elapsed < 5 -> KitchenColor.GREEN
            elapsed < 10 -> KitchenColor.YELLOW
            elapsed < 15 -> KitchenColor.ORANGE
            else -> KitchenColor.RED
        }
    }

    override suspend fun playNewOrderSound(): Result<Unit> = Result.success(Unit)

    companion object {
        private const val TAG = "SimulatedKDS"
    }
}
