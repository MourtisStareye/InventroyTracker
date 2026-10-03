package com.example.inventorytracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ItemDetailViewModel(
    private val repository: InventoryRepository,
    private val itemId: Long
) : ViewModel() {

    val item: StateFlow<InventoryItem?> = repository.getItemByIdFlow(itemId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun updateQuantity(delta: Int) {
        val currentItem = item.value ?: return
        val newQuantity = (currentItem.quantity + delta).coerceAtLeast(0)
        val updatedItem = currentItem.copy(
            quantity = newQuantity,
            lastUpdated = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.updateItem(updatedItem)
        }
    }

    fun deleteItem(onDeleted: () -> Unit) {
        val currentItem = item.value ?: return
        viewModelScope.launch {
            repository.deleteItem(currentItem)
            onDeleted()
        }
    }

    class Factory(
        private val repository: InventoryRepository,
        private val itemId: Long
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ItemDetailViewModel::class.java)) {
                return ItemDetailViewModel(repository, itemId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
