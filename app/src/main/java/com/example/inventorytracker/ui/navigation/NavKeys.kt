package com.example.inventorytracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object InventoryListKey : NavKey

@Serializable
data object ScannerKey : NavKey

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
    val initialImageUrl: String? = null
) : NavKey
