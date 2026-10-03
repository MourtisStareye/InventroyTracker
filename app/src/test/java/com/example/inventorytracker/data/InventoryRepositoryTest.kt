package com.example.inventorytracker.data

import com.example.inventorytracker.data.local.dao.InventoryDao
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.remote.api.OpenFoodFactsApiService
import com.example.inventorytracker.data.remote.model.OpenFoodFactsResponse
import com.example.inventorytracker.data.remote.model.ProductDto
import com.example.inventorytracker.data.repository.BarcodeSearchResult
import com.example.inventorytracker.data.repository.InventoryRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class InventoryRepositoryTest {

    private lateinit var fakeDao: FakeInventoryDao
    private lateinit var fakeApiService: FakeOpenFoodFactsApiService
    private lateinit var fakeProductsApiService: FakeOpenFoodFactsApiService
    private lateinit var repository: InventoryRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeInventoryDao()
        fakeApiService = FakeOpenFoodFactsApiService()
        fakeProductsApiService = FakeOpenFoodFactsApiService()
        repository = InventoryRepositoryImpl(fakeDao, fakeApiService, fakeProductsApiService)
    }

    @Test
    fun lookupBarcode_returnsLocalItem_whenFoundInDatabase() = runTest {
        val localItem = InventoryItem(
            id = 1,
            name = "Local Chocolate",
            barcode = "123456",
            brand = "LocalBrand"
        )
        fakeDao.insertItem(localItem)

        val result = repository.lookupBarcode("123456")

        assertTrue(result is BarcodeSearchResult.FoundLocal)
        val found = (result as BarcodeSearchResult.FoundLocal).item
        assertEquals("Local Chocolate", found.name)
        assertEquals("123456", found.barcode)
    }

    @Test
    fun lookupBarcode_fetchesRemoteProduct_whenNotFoundLocally() = runTest {
        val barcode = "789012"
        fakeApiService.remoteProducts[barcode] = OpenFoodFactsResponse(
            code = barcode,
            status = "success",
            product = ProductDto(
                productName = "Remote Organic Cereal",
                brands = "EcoBrand",
                categories = "Breakfast",
                imageFrontUrl = "https://example.com/cereal.jpg"
            )
        )

        val result = repository.lookupBarcode(barcode)

        assertTrue(result is BarcodeSearchResult.FoundRemote)
        val found = (result as BarcodeSearchResult.FoundRemote).item
        assertEquals("Remote Organic Cereal", found.name)
        assertEquals("EcoBrand", found.brand)
        assertEquals("789012", found.barcode)
        assertEquals("https://example.com/cereal.jpg", found.imageUrl)
    }

    @Test
    fun lookupBarcode_fallsBackToOpenProductsFacts_whenFoodProductIsMissing() = runTest {
        val barcode = "456789"
        fakeProductsApiService.remoteProducts[barcode] = OpenFoodFactsResponse(
            code = barcode,
            status = "success",
            product = ProductDto(productName = "Craft Paper", brands = "MakerCo")
        )

        val result = repository.lookupBarcode(barcode)

        assertTrue(result is BarcodeSearchResult.FoundRemote)
        val found = (result as BarcodeSearchResult.FoundRemote).item
        assertEquals("Craft Paper", found.name)
        assertEquals("MakerCo", found.brand)
    }

    @Test
    fun lookupBarcode_returnsNotFound_whenNotLocalAndNotRemote() = runTest {
        val barcode = "999999"

        val result = repository.lookupBarcode(barcode)

        assertTrue(result is BarcodeSearchResult.NotFound)
        assertEquals(barcode, (result as BarcodeSearchResult.NotFound).barcode)
    }

    @Test
    fun lookupBarcode_returnsError_whenNetworkFails() = runTest {
        val barcode = "555555"
        fakeApiService.shouldThrowError = true

        val result = repository.lookupBarcode(barcode)

        assertTrue(result is BarcodeSearchResult.Error)
        val errorResult = result as BarcodeSearchResult.Error
        assertEquals("Network unavailable", errorResult.message)
    }

    @Test
    fun insertAndGetAllItems_worksCorrectly() = runTest {
        val item1 = InventoryItem(name = "Apple", barcode = "111")
        val item2 = InventoryItem(name = "Banana", barcode = "222")

        repository.insertItem(item1)
        repository.insertItem(item2)

        val items = repository.getAllItems().first()
        assertEquals(2, items.size)
    }
}

class FakeInventoryDao : InventoryDao {
    val items = mutableListOf<InventoryItem>()

    override fun getAllItems(): Flow<List<InventoryItem>> = flowOf(items.filterNot { it.isDeleted })

    override suspend fun getItemById(id: Long): InventoryItem? = items.find { it.id == id }

    override fun getItemByIdFlow(id: Long): Flow<InventoryItem?> = flowOf(items.find { it.id == id })

    override suspend fun getItemByBarcode(barcode: String): InventoryItem? = items.find { it.barcode == barcode }

    override suspend fun insertItem(item: InventoryItem): Long {
        val newId = if (item.id == 0L) (items.maxOfOrNull { it.id } ?: 0L) + 1L else item.id
        val newItem = item.copy(id = newId)
        items.removeAll { it.id == newId }
        items.add(newItem)
        return newId
    }

    override suspend fun insertSyncedItem(item: InventoryItem): Long = insertItem(item)

    override suspend fun insertCatalogItem(item: InventoryItem): Long = insertItem(item)

    override suspend fun hasBarcodeIncludingDeleted(barcode: String): Boolean = items.any { it.barcode == barcode }

    override suspend fun getPendingSyncItems(): List<InventoryItem> = items.filter { it.syncPending }

    override suspend fun getItemBySyncId(syncId: String): InventoryItem? = items.find { it.syncId == syncId }

    override suspend fun markSyncAcknowledged(syncId: String, version: Long) {
        val item = getItemBySyncId(syncId) ?: return
        updateItem(item.copy(syncVersion = version, syncPending = false))
    }

    override suspend fun updateItem(item: InventoryItem) {
        items.removeAll { it.id == item.id }
        items.add(item)
    }

    override suspend fun deleteItem(item: InventoryItem) {
        items.removeAll { it.id == item.id }
    }

    override suspend fun deleteItemById(id: Long) {
        items.removeAll { it.id == id }
    }

    override suspend fun clearInventory(updatedAt: Long): Int {
        val clearedCount = items.count { !it.isDeleted && !it.isCatalogItem }
        items.replaceAll { item ->
            if (!item.isDeleted && !item.isCatalogItem) {
                item.copy(isDeleted = true, syncPending = true, lastUpdated = updatedAt)
            } else item
        }
        return clearedCount
    }

    override fun searchItems(query: String): Flow<List<InventoryItem>> {
        return flowOf(items.filter { !it.isDeleted && (
            it.name.contains(query, ignoreCase = true) ||
                    it.barcode?.contains(query, ignoreCase = true) == true
        ) })
    }
}

class FakeOpenFoodFactsApiService : OpenFoodFactsApiService {
    val remoteProducts = mutableMapOf<String, OpenFoodFactsResponse>()
    var shouldThrowError = false

    override suspend fun getProductByBarcode(barcode: String): Response<OpenFoodFactsResponse> {
        if (shouldThrowError) {
            throw IOException("Network unavailable")
        }
        val responseBody = remoteProducts[barcode] ?: OpenFoodFactsResponse(
            code = barcode,
            status = "failure",
            statusVerbose = "product not found"
        )
        return Response.success(responseBody)
    }
}
