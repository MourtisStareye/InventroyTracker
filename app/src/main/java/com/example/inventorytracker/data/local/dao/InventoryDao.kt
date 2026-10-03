package com.example.inventorytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.inventorytracker.data.local.entity.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items WHERE isDeleted = 0 AND isCatalogItem = 0 ORDER BY lastUpdated DESC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id AND isDeleted = 0")
    suspend fun getItemById(id: Long): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE id = :id AND isDeleted = 0")
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?>

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode AND isDeleted = 0 ORDER BY isCatalogItem ASC LIMIT 1")
    suspend fun getItemByBarcode(barcode: String): InventoryItem?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSyncedItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCatalogItem(item: InventoryItem): Long

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_items WHERE barcode = :barcode)")
    suspend fun hasBarcodeIncludingDeleted(barcode: String): Boolean

    @Transaction
    suspend fun insertCatalogItemsIfMissing(items: List<InventoryItem>) {
        items.forEach { item ->
            val code = item.barcode ?: return@forEach
            if (!hasBarcodeIncludingDeleted(code)) insertCatalogItem(item)
        }
    }

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Query("SELECT * FROM inventory_items WHERE syncPending = 1 AND isCatalogItem = 0")
    suspend fun getPendingSyncItems(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE syncId = :syncId LIMIT 1")
    suspend fun getItemBySyncId(syncId: String): InventoryItem?

    @Query("UPDATE inventory_items SET syncPending = 0, syncVersion = :version WHERE syncId = :syncId")
    suspend fun markSyncAcknowledged(syncId: String, version: Long)

    @Query("UPDATE inventory_items SET isDeleted = 1, syncPending = 1, lastUpdated = :updatedAt WHERE isDeleted = 0 AND isCatalogItem = 0")
    suspend fun clearInventory(updatedAt: Long): Int

    @Delete
    suspend fun deleteItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("SELECT * FROM inventory_items WHERE isDeleted = 0 AND isCatalogItem = 0 AND (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%') ORDER BY lastUpdated DESC")
    fun searchItems(query: String): Flow<List<InventoryItem>>
}
