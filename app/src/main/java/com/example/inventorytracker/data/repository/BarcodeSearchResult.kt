package com.example.inventorytracker.data.repository

import com.example.inventorytracker.data.local.entity.InventoryItem

sealed class BarcodeSearchResult {
    data class FoundLocal(val item: InventoryItem) : BarcodeSearchResult()
    data class FoundRemote(val item: InventoryItem) : BarcodeSearchResult()
    data class NotFound(val barcode: String) : BarcodeSearchResult()
    data class Error(val message: String, val barcode: String? = null) : BarcodeSearchResult()
}
