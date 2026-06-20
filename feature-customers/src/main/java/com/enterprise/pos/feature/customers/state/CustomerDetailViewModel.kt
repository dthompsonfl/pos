package com.enterprise.pos.feature.customers.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.Customer
import com.enterprise.pos.domain.model.Order
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.GiftCardRepository
import com.enterprise.pos.domain.repository.OrderRepository
import com.enterprise.pos.domain.repository.PromotionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class CustomerDetailEvent {
    data object Deleted : CustomerDetailEvent()
    data class Error(val message: String) : CustomerDetailEvent()
}

data class CustomerDetailState(
    val customer: Customer? = null,
    val orders: List<Order> = emptyList(),
    val loyaltyPoints: Int = 0,
    val giftCardBalance: Money = Money.ZERO,
    val lifetimeValue: Money = Money.ZERO,
    val totalOrders: Int = 0,
    val averageOrderValue: Money = Money.ZERO,
    val firstOrderDate: Long? = null,
    val lastOrderDate: Long? = null,
    val favoriteItems: List<Pair<String, Int>> = emptyList(),
    val availableRewards: List<com.enterprise.pos.domain.model.LoyaltyReward> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepo: CustomerRepository,
    private val orderRepo: OrderRepository,
    private val promotionRepo: PromotionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerDetailState())
    val state: StateFlow<CustomerDetailState> = _state.asStateFlow()

    private val _events = Channel<CustomerDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun load(customerId: CustomerId, storeId: com.enterprise.pos.core.StoreId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            customerRepo.observeCustomer(customerId)
                .onEach { c ->
                    _state.value = _state.value.copy(
                        customer = c,
                        loyaltyPoints = c?.loyaltyPoints ?: 0,
                        isLoading = false
                    )
                }
                .launchIn(viewModelScope)

            customerRepo.purchaseHistory(customerId)
                .onSuccess { orders ->
                    val paidOrders = orders.filter { it.status == com.enterprise.pos.domain.model.OrderStatus.PAID }
                    val ltv = paidOrders.fold(Money.ZERO) { a, o -> a + o.grandTotal }
                    val avg = if (paidOrders.isEmpty()) Money.ZERO else Money.ofMinor(ltv.minorUnits / paidOrders.size)
                    val favorites = paidOrders.flatMap { it.lines.filter { l -> l.lineType == com.enterprise.pos.domain.model.OrderLineType.ITEM } }
                        .groupBy { it.name }
                        .map { (n, ls) -> n to ls.sumOf { it.quantity.asInt } }
                        .sortedByDescending { it.second }
                        .take(5)
                    _state.value = _state.value.copy(
                        orders = paidOrders.sortedByDescending { it.createdAt },
                        lifetimeValue = ltv,
                        totalOrders = paidOrders.size,
                        averageOrderValue = avg,
                        firstOrderDate = paidOrders.minByOrNull { it.createdAt }?.createdAt,
                        lastOrderDate = paidOrders.maxByOrNull { it.createdAt }?.createdAt,
                        favoriteItems = favorites
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(error = it.message)
                }
        }

        promotionRepo.observeLoyaltyRewards()
            .onEach { rewards ->
                _state.value = _state.value.copy(availableRewards = rewards)
            }
            .launchIn(viewModelScope)
    }

    fun deleteCustomer(customerId: CustomerId) {
        viewModelScope.launch {
            customerRepo.delete(customerId)
                .onSuccess { _events.send(CustomerDetailEvent.Deleted) }
                .onFailure { _events.send(CustomerDetailEvent.Error(it.message ?: "Delete failed")) }
        }
    }

    fun redeemReward(customerId: CustomerId, reward: com.enterprise.pos.domain.model.LoyaltyReward) {
        viewModelScope.launch {
            customerRepo.addLoyaltyPoints(customerId, -reward.pointsCost)
        }
    }

    fun addNote(customerId: CustomerId, note: String) {
        viewModelScope.launch {
            val c = _state.value.customer ?: return@launch
            customerRepo.upsert(c.copy(notes = note))
        }
    }
}
