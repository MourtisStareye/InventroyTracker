package com.example.inventorytracker

import android.app.Application
import com.example.inventorytracker.data.local.database.AppDatabase
import com.example.inventorytracker.data.local.database.CatalogCsvSeeder
import com.example.inventorytracker.data.repository.InventoryRepository
import com.example.inventorytracker.data.repository.InventoryRepositoryImpl
import com.example.inventorytracker.data.repository.LanSyncController
import com.example.inventorytracker.data.remote.update.GitHubUpdateController
import com.example.inventorytracker.data.remote.sync.WeeklySyncSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InventoryApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val repository: InventoryRepository by lazy {
        InventoryRepositoryImpl(database.inventoryDao())
    }

    val lanSyncController: LanSyncController by lazy {
        LanSyncController(this, database.inventoryDao())
    }

    val githubUpdateController: GitHubUpdateController by lazy {
        GitHubUpdateController(this)
    }

    override fun onCreate() {
        super.onCreate()
        WeeklySyncSchedule.enqueue(this)
        applicationScope.launch {
            try {
                CatalogCsvSeeder(this@InventoryApplication, database.inventoryDao()).seedIfNeeded()
            } catch (error: Exception) {
                android.util.Log.e("InventoryApplication", "Could not seed miniature paint catalog", error)
            }
        }
    }
}
