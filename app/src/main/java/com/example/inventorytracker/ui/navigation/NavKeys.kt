package com.example.inventorytracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.example.inventorytracker.ui.scanner.BarcodeScanMode
import kotlinx.serialization.Serializable

@Serializable
data object InventoryListKey : NavKey

@Serializable
data class ScannerKey(val mode: BarcodeScanMode = BarcodeScanMode.UPC) : NavKey

@Serializable
data object SyncSettingsKey : NavKey

@Serializable
data object SyncNowKey : NavKey

@Serializable
data object AppSettingsKey : NavKey

@Serializable
data class ItemDetailKey(val itemId: Long) : NavKey

@Serializable
data class ItemEditKey(
    val itemId: Long = 0L,
    val initialBarcode: String? = null,
    val initialName: String? = null,
    val initialBrand: String? = null,
    val initialCategory: String? = null,
    val initialImageUrl: String? = null,
    val openPhotoPickerOnLaunch: Boolean = false
) : NavKey
