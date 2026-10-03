package com.example.inventorytracker.ui

import com.example.inventorytracker.data.FakeInventoryDao
import com.example.inventorytracker.data.FakeOpenFoodFactsApiService
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.remote.model.OpenFoodFactsResponse
import com.example.inventorytracker.data.remote.model.ProductDto
import com.example.inventorytracker.data.repository.BarcodeSearchResult
import com.example.inventorytracker.data.repository.InventoryRepositoryImpl
import com.example.inventorytracker.ui.scanner.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeInventoryDao
    private lateinit var fakeApiService: FakeOpenFoodFactsApiService
    private lateinit var fakeProductsApiService: FakeOpenFoodFactsApiService
    private lateinit var repository: InventoryRepositoryImpl
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeInventoryDao()
        fakeApiService = FakeOpenFoodFactsApiService()
        fakeProductsApiService = FakeOpenFoodFactsApiService()
        repository = InventoryRepositoryImpl(fakeDao, fakeApiService, fakeProductsApiService)
        viewModel = ScannerViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onBarcodeScanned_findsLocalItem() = runTest {
        val localItem = InventoryItem(id = 1, name = "Local Coffee", barcode = "111222")
        fakeDao.insertItem(localItem)

        viewModel.onBarcodeScanned("111222")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.scanResult.value
        assertTrue(result is BarcodeSearchResult.FoundLocal)
        assertEquals("Local Coffee", (result as BarcodeSearchResult.FoundLocal).item.name)
    }

    @Test
    fun onBarcodeScanned_findsRemoteItemWhenNotLocal() = runTest {
        fakeApiService.remoteProducts["333444"] = OpenFoodFactsResponse(
            code = "333444",
            status = "success",
            product = ProductDto(
                productName = "Remote Green Tea",
                brands = "TeaCo",
                categories = "Beverages"
            )
        )

        viewModel.onBarcodeScanned("333444")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.scanResult.value
        assertTrue(result is BarcodeSearchResult.FoundRemote)
        val remoteItem = (result as BarcodeSearchResult.FoundRemote).item
        assertEquals("Remote Green Tea", remoteItem.name)
        assertEquals("TeaCo", remoteItem.brand)
    }

    @Test
    fun resetScanState_clearsScanState() = runTest {
        viewModel.onBarcodeScanned("123")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.resetScanState()

        assertEquals(false, viewModel.isProcessing.value)
        assertEquals(null, viewModel.scannedBarcode.value)
        assertEquals(null, viewModel.scanResult.value)
    }
}
