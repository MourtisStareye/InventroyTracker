package com.example.inventorytracker.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "inventory_items",
    indices = [Index(value = ["barcode"]), Index(value = ["syncId"], unique = true)]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val brand: String? = null,
    val quantity: Int = 1,
    val category: String? = null,
    val imageUrl: String? = null,
    val location: String? = null,
    val notes: String? = null,
    val price: Double? = null,
    val expirationDate: Long? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val syncVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "1") val syncPending: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isCatalogItem: Boolean = false
)
