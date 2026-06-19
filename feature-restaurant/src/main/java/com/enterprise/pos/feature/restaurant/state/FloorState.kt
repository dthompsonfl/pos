package com.enterprise.pos.feature.restaurant.state

import com.enterprise.pos.core.EmployeeId
import com.enterprise.pos.core.Money
import com.enterprise.pos.core.OrderId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.domain.model.DiningMode
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.model.OrderStatus
import com.enterprise.pos.domain.model.RestaurantTable
import com.enterprise.pos.domain.model.TableStatus
import javax.inject.Inject

/** Pure UI state for the floor / table view. */
data class FloorState(
    val tables: List<RestaurantTable> = emptyList(),
    val sectionFilter: String? = null,
    val selectedTable: RestaurantTable? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/** State for the "start order" entry — appears when cashier picks a dining mode. */
data class StartOrderState(
    val diningMode: DiningMode = DiningMode.RETAIL,
    val selectedTableId: com.enterprise.pos.core.TableId? = null,
    val selectedServerId: EmployeeId? = null,
    val guestCount: Int = 2,
    val customerQuery: String = "",
    val customerSearchResults: List<com.enterprise.pos.domain.model.Customer> = emptyList(),
    val selectedCustomerId: com.enterprise.pos.core.CustomerId? = null,
    val order: Order? = null,
    val isCreating: Boolean = false,
    val error: String? = null
)

/** State of an individual table card on the floor map. */
data class TableCardState(
    val table: RestaurantTable,
    val openOrder: Order? = null,
    val serverName: String? = null,
    val elapsedTimeMs: Long = 0L
) {
    val statusColor: Long
        get() = when (table.status) {
            TableStatus.AVAILABLE -> 0xFF4CAF50
            TableStatus.SEATED -> 0xFF2196F3
            TableStatus.ORDERED -> 0xFFFF9800
            TableStatus.DINING -> 0xFF9C27B0
            TableStatus.BILL_REQUESTED -> 0xFFF44336
            TableStatus.CLEANING -> 0xFF607D8B
            TableStatus.RESERVED -> 0xFF00BCD4
        }
}
