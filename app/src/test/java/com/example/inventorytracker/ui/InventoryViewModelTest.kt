package com.example.inventorytracker.ui

import com.example.inventorytracker.data.FakeInventoryDao
import com.example.inventorytracker.data.FakeOpenFoodFactsApiService
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.repository.InventoryRepositoryImpl
import com.example.inventorytracker.ui.inventory.InventoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeInventoryDao
    private lateinit var repository: InventoryRepositoryImpl
    private lateinit var viewModel: InventoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeInventoryDao()
        repository = InventoryRepositoryImpl(fakeDao, FakeOpenFoodFactsApiService())
        viewModel = InventoryViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateSearchQuery_filtersItems() = runTest {
        val collectJob = backgroundScope.launch { viewModel.items.collect {} }

        val item1 = InventoryItem(id = 1, name = "Apple Juice", barcode = "1001", category = "Beverages")
        val item2 = InventoryItem(id = 2, name = "Banana Bread", barcode = "1002", category = "Bakery")
        fakeDao.insertItem(item1)
        fakeDao.insertItem(item2)

        viewModel.updateSearchQuery("Apple")
        testDispatcher.scheduler.advanceUntilIdle()

        val items = viewModel.items.value
        assertEquals(1, items.size)
        assertEquals("Apple Juice", items[0].name)

        collectJob.cancel()
    }

    @Test
    fun updateQuantity_updatesItemInRepository() = runTest {
        val item = InventoryItem(id = 1, name = "Cereal", quantity = 5)
        fakeDao.insertItem(item)

        viewModel.updateQuantity(item, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = fakeDao.getItemById(1)
        assertEquals(7, updated?.quantity)
    }

    @Test
    fun deleteItem_removesItemFromRepository() = runTest {
        val item = InventoryItem(id = 1, name = "Milk", quantity = 2)
        fakeDao.insertItem(item)

        viewModel.deleteItem(item)
        testDispatcher.scheduler.advanceUntilIdle()

        val remaining = fakeDao.getItemById(1)
        assertEquals(null, remaining)
    }
}
