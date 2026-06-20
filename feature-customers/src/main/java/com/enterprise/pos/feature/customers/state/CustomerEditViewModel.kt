package com.enterprise.pos.feature.customers.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.CustomerId
import com.enterprise.pos.core.Id
import com.enterprise.pos.core.Money
import com.enterprise.pos.domain.model.Customer
import com.enterprise.pos.domain.repository.CustomerRepository
import com.enterprise.pos.domain.repository.GiftCardRepository
import com.enterprise.pos.domain.repository.OrderRepository
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

sealed class CustomerEditEvent {
    data object Saved : CustomerEditEvent()
    data class Error(val message: String) : CustomerEditEvent()
    data class ValidationFailed(val errors: Map<String, String>) : CustomerEditEvent()
}

data class CustomerEditForm(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val country: String = "USA",
    val notes: String = "",
    val tags: String = "",
    val group: String = "",
    val loyaltyNumber: String = "",
    val birthday: String = "",
    val marketingConsent: Boolean = false
)

data class CustomerEditState(
    val form: CustomerEditForm = CustomerEditForm(),
    val errors: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val customerId: String? = null
)

@HiltViewModel
class CustomerEditViewModel @Inject constructor(
    private val customerRepo: CustomerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerEditState())
    val state: StateFlow<CustomerEditState> = _state.asStateFlow()

    private val _events = Channel<CustomerEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun loadCustomer(id: CustomerId) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            customerRepo.get(id)
                .onSuccess { customer ->
                    customer?.let { c ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            customerId = c.id.value,
                            form = CustomerEditForm(
                                firstName = c.firstName,
                                lastName = c.lastName,
                                phone = c.phone ?: "",
                                email = c.email ?: "",
                                address = c.address ?: "",
                                city = c.city ?: "",
                                state = c.state ?: "",
                                zip = c.zip ?: "",
                                country = c.country ?: "USA",
                                notes = c.notes ?: "",
                                tags = c.tags.joinToString(", "),
                                group = c.group ?: "",
                                loyaltyNumber = c.loyaltyNumber ?: "",
                                birthday = c.birthday ?: "",
                                marketingConsent = c.marketingOptIn
                            )
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false)
                    _events.send(CustomerEditEvent.Error(it.message ?: "Failed to load customer"))
                }
        }
    }

    fun updateForm(update: (CustomerEditForm) -> CustomerEditForm) {
        _state.value = _state.value.copy(form = update(_state.value.form), errors = emptyMap())
    }

    fun save() {
        val validationErrors = validate(_state.value.form)
        if (validationErrors.isNotEmpty()) {
            _state.value = _state.value.copy(errors = validationErrors)
            viewModelScope.launch { _events.send(CustomerEditEvent.ValidationFailed(validationErrors)) }
            return
        }

        val form = _state.value.form
        val customerId = _state.value.customerId?.let { CustomerId(it) } ?: CustomerId(Id.random<Any>().value)
        val fullName = "${form.firstName} ${form.lastName}".trim()

        val customer = Customer(
            id = customerId,
            name = fullName,
            firstName = form.firstName.trim(),
            lastName = form.lastName.trim(),
            phone = form.phone.trim().ifBlank { null },
            email = form.email.trim().ifBlank { null },
            address = form.address.trim().ifBlank { null },
            city = form.city.trim().ifBlank { null },
            state = form.state.trim().ifBlank { null },
            zip = form.zip.trim().ifBlank { null },
            country = form.country.trim().ifBlank { null },
            notes = form.notes.trim().ifBlank { null },
            tags = form.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
            group = form.group.trim().ifBlank { null },
            loyaltyNumber = form.loyaltyNumber.trim().ifBlank { null },
            birthday = form.birthday.trim().ifBlank { null },
            marketingOptIn = form.marketingConsent
        )

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            customerRepo.upsert(customer)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false)
                    _events.send(CustomerEditEvent.Saved)
                }
                .onFailure {
                    _state.value = _state.value.copy(isSaving = false)
                    _events.send(CustomerEditEvent.Error(it.message ?: "Save failed"))
                }
        }
    }

    private fun validate(form: CustomerEditForm): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (form.firstName.isBlank()) errors["firstName"] = "First name is required"
        if (form.lastName.isBlank()) errors["lastName"] = "Last name is required"
        if (form.email.isNotBlank() && !form.email.matches(EMAIL_REGEX)) errors["email"] = "Invalid email format"
        if (form.phone.isNotBlank() && !form.phone.matches(PHONE_REGEX)) errors["phone"] = "Invalid phone format"
        return errors
    }

    companion object {
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\$")
        private val PHONE_REGEX = Regex("^\\+?[0-9\\s\\-()]{7,}\$")
    }
}
