package com.example.inventorytracker.ui

import com.example.inventorytracker.data.FakeInventoryDao
import com.example.inventorytracker.data.FakeOpenFoodFactsApiService
import com.example.inventorytracker.data.local.entity.InventoryItem
import com.example.inventorytracker.data.repository.InventoryRepositoryImpl
import com.example.inventorytracker.ui.edit.ItemEditViewModel
import com.example.inventorytracker.ui.navigation.ItemEditKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ItemEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeInventoryDao
    private lateinit var repository: InventoryRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeInventoryDao()
        repository = InventoryRepositoryImpl(fakeDao, FakeOpenFoodFactsApiService())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_prefillsStateFromEditKey() = runTest {
        val editKey = ItemEditKey(
            itemId = 0L,
            initialBarcode = "12345",
            initialName = "Pre-filled Name",
            initialBrand = "Pre-filled Brand",
            initialCategory = "Groceries",
            initialImageUrl = "https://example.com/img.jpg"
        )
        val viewModel = ItemEditViewModel(repository, editKey)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("12345", viewModel.barcode.value)
        assertEquals("Pre-filled Name", viewModel.name.value)
        assertEquals("Pre-filled Brand", viewModel.brand.value)
        assertEquals("Groceries", viewModel.category.value)
        assertEquals("https://example.com/img.jpg", viewModel.imageUrl.value)
    }

    @Test
    fun saveItem_validationFailsWhenNameIsBlank() = runTest {
        val editKey = ItemEditKey(itemId = 0L)
        val viewModel = ItemEditViewModel(repository, editKey)
        testDispatcher.scheduler.advanceUntilIdle()

        var saved = false
        viewModel.saveItem { saved = true }

        assertEquals("Item name is required", viewModel.nameError.value)
        assertEquals(false, saved)
    }

    @Test
    fun saveItem_insertsNewItemWhenValid() = runTest {
        val editKey = ItemEditKey(itemId = 0L)
        val viewModel = ItemEditViewModel(repository, editKey)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateName("Fresh Tomatoes")
        viewModel.updateBarcode("98765")
        viewModel.updateCategory("Produce")

        var savedItemId = 0L
        viewModel.saveItem { id -> savedItemId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        val savedItem = fakeDao.getItemById(savedItemId)
        assertNotNull(savedItem)
        assertEquals("Fresh Tomatoes", savedItem?.name)
        assertEquals("98765", savedItem?.barcode)
        assertEquals("Produce", savedItem?.category)
    }

    @Test
    fun saveItem_updatesExistingItemWhenEditing() = runTest {
        val existingItem = InventoryItem(id = 10, name = "Old Name", quantity = 1)
        fakeDao.insertItem(existingItem)

        val editKey = ItemEditKey(itemId = 10)
        val viewModel = ItemEditViewModel(repository, editKey)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateName("New Updated Name")
        viewModel.setQuantity(5)

        var savedItemId = 0L
        viewModel.saveItem { id -> savedItemId = id }
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = fakeDao.getItemById(10)
        assertEquals(10L, savedItemId)
        assertEquals("New Updated Name", updated?.name)
        assertEquals(5, updated?.quantity)
    }
}
