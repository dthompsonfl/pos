package com.enterprise.pos.feature.customers.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.domain.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersState(
    val query: String = "",
    val customers: List<com.enterprise.pos.domain.model.Customer> = emptyList(),
    val selected: com.enterprise.pos.domain.model.Customer? = null
)

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val repo: CustomerRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CustomersState())
    val state: StateFlow<CustomersState> = _state.asStateFlow()

    init {
        repo.observeCustomers("")
            .onEach { list -> _state.value = _state.value.copy(customers = list) }
            .launchIn(viewModelScope)
    }

    fun search(q: String) {
        _state.value = _state.value.copy(query = q)
        repo.observeCustomers(q)
            .onEach { list -> _state.value = _state.value.copy(customers = list) }
            .launchIn(viewModelScope)
    }

    fun select(c: com.enterprise.pos.domain.model.Customer?) { _state.value = _state.value.copy(selected = c) }

    fun addLoyalty(id: CustomerId, points: Int) {
        viewModelScope.launch { repo.addLoyaltyPoints(id, points) }
    }
}
