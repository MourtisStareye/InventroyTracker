package com.example.inventorytracker.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.repository.InventoryRepository
import com.example.inventorytracker.ui.navigation.ItemEditKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemEditViewModel(
    private val repository: InventoryRepository,
    editKey: ItemEditKey
) : ViewModel() {

    var existingItemId: Long = editKey.itemId
        private set

    val barcode = MutableStateFlow(editKey.initialBarcode ?: "")
    val name = MutableStateFlow(editKey.initialName ?: "")
    val brand = MutableStateFlow(editKey.initialBrand ?: "")
    val category = MutableStateFlow(editKey.initialCategory ?: "")
    val type = MutableStateFlow("")
    val existingCategories: StateFlow<List<String>> = repository.getAllItems()
        .map { items ->
            items.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val existingTypes: StateFlow<List<String>> = repository.getAllItems()
        .map { items ->
            items.mapNotNull { it.type?.trim()?.takeIf(String::isNotEmpty) }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val quantity = MutableStateFlow(1)
    val imageUrl = MutableStateFlow(editKey.initialImageUrl ?: "")
    val location = MutableStateFlow("")
    val notes = MutableStateFlow("")
    val price = MutableStateFlow("")
    val expirationDate = MutableStateFlow<Long?>(null)

    private val _nameError = MutableStateFlow<String?>(null)
    val nameError: StateFlow<String?> = _nameError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        if (existingItemId > 0) {
            viewModelScope.launch {
                val item = repository.getItemById(existingItemId)
                if (item != null) {
                    barcode.value = item.barcode ?: ""
                    name.value = item.name
                    brand.value = item.brand ?: ""
                    category.value = item.category ?: ""
                    type.value = item.type ?: ""
                    quantity.value = item.quantity
                    imageUrl.value = item.imageUrl ?: ""
                    location.value = item.location ?: ""
                    notes.value = item.notes ?: ""
                    price.value = item.price?.toString() ?: ""
                    expirationDate.value = item.expirationDate
                }
                _isLoaded.value = true
            }
        } else {
            _isLoaded.value = true
        }
    }

    fun updateBarcode(newBarcode: String) {
        barcode.value = newBarcode
    }

    fun updateName(newName: String) {
        name.value = newName
        if (newName.isNotBlank()) {
            _nameError.value = null
        }
    }

    fun updateBrand(newBrand: String) {
        brand.value = newBrand
    }

    fun updateCategory(newCategory: String) {
        category.value = newCategory
    }

    fun updateType(newType: String) {
        type.value = newType
    }

    fun updateQuantity(delta: Int) {
        quantity.value = (quantity.value + delta).coerceAtLeast(0)
    }

    fun setQuantity(value: Int) {
        quantity.value = value.coerceAtLeast(0)
    }

    fun updateImageUrl(url: String) {
        imageUrl.value = url
    }

    fun updateLocation(loc: String) {
        location.value = loc
    }

    fun updateNotes(n: String) {
        notes.value = n
    }

    fun updatePrice(p: String) {
        price.value = p
    }

    fun saveItem(onSaved: (itemId: Long) -> Unit) {
        val trimmedName = name.value.trim()
        if (trimmedName.isEmpty()) {
            _nameError.value = "Item name is required"
            return
        }

        val parsedPrice = price.value.trim().toDoubleOrNull()
        val item = InventoryItem(
            id = if (existingItemId > 0) existingItemId else 0,
            name = trimmedName,
            barcode = barcode.value.trim().takeIf { it.isNotEmpty() },
            brand = brand.value.trim().takeIf { it.isNotEmpty() },
            category = category.value.trim().takeIf { it.isNotEmpty() },
            type = type.value.trim().takeIf { it.isNotEmpty() },
            quantity = quantity.value,
            imageUrl = imageUrl.value.trim().takeIf { it.isNotEmpty() },
            location = location.value.trim().takeIf { it.isNotEmpty() },
            notes = notes.value.trim().takeIf { it.isNotEmpty() },
            price = parsedPrice,
            expirationDate = expirationDate.value,
            lastUpdated = System.currentTimeMillis()
        )

        viewModelScope.launch {
            _isSaving.value = true
            val savedId = if (existingItemId > 0) {
                repository.updateItem(item)
                existingItemId
            } else {
                repository.insertItem(item)
            }
            _isSaving.value = false
            onSaved(savedId)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        if (existingItemId <= 0) return
        viewModelScope.launch {
            repository.deleteItemById(existingItemId)
            onDeleted()
        }
    }

    class Factory(
        private val repository: InventoryRepository,
        private val editKey: ItemEditKey
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ItemEditViewModel::class.java)) {
                return ItemEditViewModel(repository, editKey) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
