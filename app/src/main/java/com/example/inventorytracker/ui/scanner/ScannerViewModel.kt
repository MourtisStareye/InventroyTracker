package com.example.inventorytracker.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.inventorytracker.data.repository.BarcodeSearchResult
import com.example.inventorytracker.data.repository.InventoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(
    private val repository: InventoryRepository
) : ViewModel() {

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val _scanResult = MutableStateFlow<BarcodeSearchResult?>(null)
    val scanResult: StateFlow<BarcodeSearchResult?> = _scanResult.asStateFlow()

    fun onBarcodeScanned(barcode: String) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty() || _isProcessing.value) return

        _isProcessing.value = true
        _scannedBarcode.value = trimmed

        viewModelScope.launch {
            try {
                val result = repository.lookupBarcode(trimmed)
                _scanResult.value = result
            } catch (e: Exception) {
                _scanResult.value = BarcodeSearchResult.Error(
                    message = e.localizedMessage ?: "Unknown lookup error",
                    barcode = trimmed
                )
            }
        }
    }

    fun resetScanState() {
        _isProcessing.value = false
        _scannedBarcode.value = null
        _scanResult.value = null
    }

    class Factory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                return ScannerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
