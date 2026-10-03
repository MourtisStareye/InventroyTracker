package com.example.inventorytracker.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.repository.InventoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModel(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val refreshKey = MutableStateFlow(0)

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val rawItemsFlow = combine(searchQuery, refreshKey) { query, _ -> query }.flatMapLatest { query ->
        if (query.isBlank()) {
            repository.getAllItems()
        } else {
            repository.searchItems(query.trim())
        }
    }

    val items: StateFlow<List<InventoryItem>> = combine(
        rawItemsFlow,
        _selectedCategory
    ) { itemList, category ->
        if (category.isNullOrBlank()) {
            itemList
        } else {
            itemList.filter { it.category.equals(category, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val availableCategories: StateFlow<List<String>> = repository.getAllItems()
        .combine(_selectedCategory) { itemList, _ ->
            itemList.mapNotNull { it.category }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshInventory() {
        refreshKey.value += 1
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = if (_selectedCategory.value == category) null else category
    }

    fun updateQuantity(item: InventoryItem, delta: Int) {
        val newQty = (item.quantity + delta).coerceAtLeast(0)
        viewModelScope.launch {
            if (newQty == 0 && delta < 0) {
                // If user reduces to 0, update item quantity to 0
                repository.updateItem(item.copy(quantity = 0, lastUpdated = System.currentTimeMillis()))
            } else {
                repository.updateItem(item.copy(quantity = newQty, lastUpdated = System.currentTimeMillis()))
            }
        }
    }

    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun clearInventory() {
        viewModelScope.launch {
            repository.clearInventory()
        }
    }

    class Factory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InventoryViewModel::class.java)) {
                return InventoryViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
