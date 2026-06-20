package com.enterprise.pos.feature.catalog.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enterprise.pos.core.ModifierGroupId
import com.enterprise.pos.core.StoreId
import com.enterprise.pos.core.randomModifierGroupId
import com.enterprise.pos.domain.model.ModifierGroup
import com.enterprise.pos.domain.model.ModifierOption
import com.enterprise.pos.domain.repository.CatalogRepository
import com.enterprise.pos.domain.repository.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ModifierEditorState(
    val modifierGroup: ModifierGroup? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val validationErrors: Map<String, String> = emptyMap(),
    val saveResult: Boolean? = null
)

sealed class ModifierEditorEvent {
    data class Saved(val id: ModifierGroupId) : ModifierEditorEvent()
    data class Error(val message: String) : ModifierEditorEvent()
}

@HiltViewModel
class ModifierEditorViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val storeRepo: StoreRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ModifierEditorState())
    val state: StateFlow<ModifierEditorState> = _state.asStateFlow()

    private val _events = Channel<ModifierEditorEvent>(Channel.BUFFERED)
    val events: Flow<ModifierEditorEvent> = _events.receiveAsFlow()

    private var storeId: StoreId? = null

    init {
        viewModelScope.launch {
            storeRepo.current().onSuccess { store ->
                storeId = store.id
            }
        }
    }

    fun load(id: ModifierGroupId?) {
        if (id == null) {
            createNew()
            return
        }
        _state.value = _state.value.copy(isLoading = true, error = null)
        repository.observeModifierGroup(id)
            .onEach { group ->
                if (group == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Modifier group not found"
                    )
                    return@onEach
                }
                _state.value = _state.value.copy(
                    modifierGroup = group,
                    isLoading = false
                )
            }
            .launchIn(viewModelScope)
    }

    fun createNew() {
        _state.value = ModifierEditorState(
            modifierGroup = ModifierGroup(
                id = randomModifierGroupId(),
                name = "",
                options = emptyList()
            ),
            isLoading = false
        )
    }

    fun updateName(name: String) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(modifierGroup = current.copy(name = name))
    }

    fun updateDescription(description: String) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(modifierGroup = current.copy(description = description))
    }

    fun updateRequired(required: Boolean) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(modifierGroup = current.copy(isRequired = required))
    }

    fun updateMaxSelections(max: Int) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(
            modifierGroup = current.copy(maxSelections = max.coerceAtLeast(1))
        )
    }

    fun updateMinSelections(min: Int) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(
            modifierGroup = current.copy(minSelections = min.coerceAtLeast(0))
        )
    }

    fun addOption(name: String = "New Option") {
        val current = _state.value.modifierGroup ?: return
        val newOption = ModifierOption(
            id = UUID.randomUUID().toString(),
            name = name
        )
        _state.value = _state.value.copy(
            modifierGroup = current.copy(options = current.options + newOption)
        )
    }

    fun removeOption(optionId: String) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(
            modifierGroup = current.copy(options = current.options.filter { it.id != optionId })
        )
    }

    fun updateOption(optionId: String, name: String? = null, priceAdjustment: String? = null, isAvailable: Boolean? = null) {
        val current = _state.value.modifierGroup ?: return
        _state.value = _state.value.copy(
            modifierGroup = current.copy(
                options = current.options.map { option ->
                    if (option.id == optionId) {
                        val adj = priceAdjustment?.let {
                            runCatching { com.enterprise.pos.core.Money.parse(it) }.getOrNull() ?: option.priceAdjustment
                        } ?: option.priceAdjustment
                        option.copy(
                            name = name ?: option.name,
                            priceAdjustment = adj,
                            isAvailable = isAvailable ?: option.isAvailable
                        )
                    } else option
                }
            )
        )
    }

    fun reorderOptions(fromIndex: Int, toIndex: Int) {
        val current = _state.value.modifierGroup ?: return
        val options = current.options.toMutableList()
        if (fromIndex in options.indices && toIndex in options.indices) {
            val moved = options.removeAt(fromIndex)
            options.add(toIndex, moved)
            _state.value = _state.value.copy(
                modifierGroup = current.copy(options = options)
            )
        }
    }

    fun save() {
        val group = _state.value.modifierGroup ?: return
        val errors = validate(group)
        if (errors.isNotEmpty()) {
            _state.value = _state.value.copy(validationErrors = errors)
            return
        }
        val sid = storeId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, validationErrors = emptyMap())
            repository.upsertModifierGroup(sid, group)
                .onSuccess {
                    _state.value = _state.value.copy(isSaving = false, saveResult = true)
                    _events.send(ModifierEditorEvent.Saved(group.id))
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isSaving = false, saveResult = false, error = error.message)
                    _events.send(ModifierEditorEvent.Error(error.message))
                }
        }
    }

    private fun validate(group: ModifierGroup): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (group.name.isBlank()) errors["name"] = "Name is required"
        if (group.options.isEmpty()) errors["options"] = "At least one option is required"
        val names = group.options.map { it.name }
        if (names.toSet().size != names.size) errors["options"] = "Option names must be unique"
        return errors
    }
}
