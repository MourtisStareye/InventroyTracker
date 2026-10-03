package com.example.inventorytracker.data.repository

import com.example.inventorytracker.data.local.dao.InventoryDao
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.remote.RetrofitClient
import com.example.inventorytracker.data.remote.api.OpenFoodFactsApiService
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getAllItems(): Flow<List<InventoryItem>>
    suspend fun getItemById(id: Long): InventoryItem?
    fun getItemByIdFlow(id: Long): Flow<InventoryItem?>
    suspend fun getItemByBarcodeLocal(barcode: String): InventoryItem?
    suspend fun lookupBarcode(barcode: String): BarcodeSearchResult
    suspend fun insertItem(item: InventoryItem): Long
    suspend fun updateItem(item: InventoryItem)
    suspend fun deleteItem(item: InventoryItem)
    suspend fun deleteItemById(id: Long)
    suspend fun clearInventory()
    fun searchItems(query: String): Flow<List<InventoryItem>>
}

class InventoryRepositoryImpl(
    private val inventoryDao: InventoryDao,
    private val apiService: OpenFoodFactsApiService = RetrofitClient.apiService,
    private val productsApiService: OpenFoodFactsApiService = RetrofitClient.productsApiService
) : InventoryRepository {

    override fun getAllItems(): Flow<List<InventoryItem>> {
        return inventoryDao.getAllItems()
    }

    override suspend fun getItemById(id: Long): InventoryItem? {
        return inventoryDao.getItemById(id)
    }

    override fun getItemByIdFlow(id: Long): Flow<InventoryItem?> {
        return inventoryDao.getItemByIdFlow(id)
    }

    override suspend fun getItemByBarcodeLocal(barcode: String): InventoryItem? {
        return inventoryDao.getItemByBarcode(barcode)
    }

    override suspend fun lookupBarcode(barcode: String): BarcodeSearchResult {
        val trimmedBarcode = barcode.trim()
        if (trimmedBarcode.isEmpty()) {
            return BarcodeSearchResult.Error("Barcode cannot be empty")
        }

        // 1. Search local Room database
        val localItem = inventoryDao.getItemByBarcode(trimmedBarcode)
        if (localItem != null) {
            return BarcodeSearchResult.FoundLocal(localItem)
        }

        // 2. Check food products first, then the broader non-food products catalog.
        val foodResult = lookupRemoteProduct(apiService, trimmedBarcode)
        if (foodResult is BarcodeSearchResult.FoundRemote) return foodResult

        val productsResult = lookupRemoteProduct(productsApiService, trimmedBarcode)
        if (productsResult is BarcodeSearchResult.FoundRemote) return productsResult

        return when {
            foodResult is BarcodeSearchResult.Error -> foodResult
            productsResult is BarcodeSearchResult.Error -> productsResult
            else -> BarcodeSearchResult.NotFound(trimmedBarcode)
        }
    }

    private suspend fun lookupRemoteProduct(
        service: OpenFoodFactsApiService,
        barcode: String
    ): BarcodeSearchResult = try {
        val response = service.getProductByBarcode(barcode)
        if (response.isSuccessful) {
            val body = response.body()
            val product = body?.product
            if (body?.status?.startsWith("success") == true && product != null) {
                BarcodeSearchResult.FoundRemote(
                    InventoryItem(
                        name = product.getDisplayName() ?: "Product $barcode",
                        barcode = barcode,
                        brand = product.brands,
                        category = product.categories,
                        imageUrl = product.getBestImageUrl(),
                        quantity = 1
                    )
                )
            } else {
                BarcodeSearchResult.NotFound(barcode)
            }
        } else if (response.code() == 404) {
            BarcodeSearchResult.NotFound(barcode)
        } else {
            BarcodeSearchResult.Error(
                message = "Server error: ${response.code()} ${response.message()}",
                barcode = barcode
            )
        }
    } catch (e: Exception) {
        BarcodeSearchResult.Error(
            message = e.localizedMessage ?: "Failed to reach remote server",
            barcode = barcode
        )
    }

    override suspend fun insertItem(item: InventoryItem): Long {
        return inventoryDao.insertItem(item)
    }

    override suspend fun updateItem(item: InventoryItem) {
        val existing = inventoryDao.getItemById(item.id) ?: return
        inventoryDao.updateItem(
            item.copy(
                syncId = existing.syncId,
                syncVersion = existing.syncVersion,
                syncPending = true,
                isDeleted = false
            )
        )
    }

    override suspend fun deleteItem(item: InventoryItem) {
        inventoryDao.updateItem(
            item.copy(isDeleted = true, syncPending = true, lastUpdated = System.currentTimeMillis())
        )
    }

    override suspend fun deleteItemById(id: Long) {
        val existing = inventoryDao.getItemById(id) ?: return
        inventoryDao.updateItem(
            existing.copy(isDeleted = true, syncPending = true, lastUpdated = System.currentTimeMillis())
        )
    }

    override suspend fun clearInventory() {
        inventoryDao.clearInventory(System.currentTimeMillis())
    }

    override fun searchItems(query: String): Flow<List<InventoryItem>> {
        return inventoryDao.searchItems(query)
    }
}
