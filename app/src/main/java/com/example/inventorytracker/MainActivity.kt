package com.example.inventorytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.inventorytracker.data.remote.update.GitHubRelease
import com.example.inventorytracker.data.remote.update.GitHubUpdateController
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.example.inventorytracker.data.repository.InventoryRepository
import com.example.inventorytracker.data.repository.LanSyncController
import com.example.inventorytracker.ui.components.EmptyDetailPlaceholder
import com.example.inventorytracker.ui.detail.ItemDetailScreen
import com.example.inventorytracker.ui.detail.ItemDetailViewModel
import com.example.inventorytracker.ui.edit.ItemEditScreen
import com.example.inventorytracker.ui.edit.ItemEditViewModel
import com.example.inventorytracker.ui.inventory.InventoryListScreen
import com.example.inventorytracker.ui.inventory.InventoryViewModel
import com.example.inventorytracker.ui.navigation.InventoryListKey
import com.example.inventorytracker.ui.navigation.AppSettingsKey
import com.example.inventorytracker.ui.navigation.ItemDetailKey
import com.example.inventorytracker.ui.navigation.ItemEditKey
import com.example.inventorytracker.ui.navigation.ScannerKey
import com.example.inventorytracker.ui.navigation.SyncNowKey
import com.example.inventorytracker.ui.navigation.SyncSettingsKey
import com.example.inventorytracker.ui.scanner.ScannerContainerScreen
import com.example.inventorytracker.ui.scanner.ScannerViewModel
import com.example.inventorytracker.ui.settings.AppSettingsScreen
import com.example.inventorytracker.ui.sync.SyncSettingsScreen
import com.example.inventorytracker.ui.theme.InventoryTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as InventoryApplication
        val repository = app.repository

        setContent {
            InventoryTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InventoryAppNav(
                        repository = repository,
                        syncController = app.lanSyncController,
                        updateController = app.githubUpdateController
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun InventoryAppNav(
    repository: InventoryRepository,
    syncController: LanSyncController,
    updateController: GitHubUpdateController
) {
    val backStack = rememberNavBackStack(InventoryListKey)
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val updateScope = androidx.compose.runtime.rememberCoroutineScope()
    var launchUpdate by remember { mutableStateOf<GitHubRelease?>(null) }

    LaunchedEffect(updateController) {
        launchUpdate = runCatching { updateController.checkForUpdate() }.getOrNull()
    }

    launchUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = { launchUpdate = null },
            title = { Text("Inventory Tracker ${release.tag} is available") },
            text = { Text(release.notes.ifBlank { "No release notes were provided." }) },
            confirmButton = {
                TextButton(onClick = {
                    launchUpdate = null
                    updateScope.launch {
                        runCatching {
                            val apk = updateController.downloadAndInstall(release)
                            updateController.installApk(apk)
                        }.onFailure { Toast.makeText(context, it.message ?: "Update could not be installed.", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Download and install") }
            },
            dismissButton = { TextButton(onClick = { launchUpdate = null }) { Text("Later") } }
        )
    }

    LaunchedEffect(lifecycleOwner, syncController) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val syncRequested = syncController.awaitDesktopSyncRequest()
                if (syncRequested) syncController.syncNow()
                else delay(1_000)
            }
        }
    }

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
        },
        sceneStrategy = listDetailStrategy,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<InventoryListKey>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = {
                        EmptyDetailPlaceholder(
                            onScanClick = { backStack.add(ScannerKey) },
                            onAddClick = { backStack.add(ItemEditKey()) }
                        )
                    }
                )
            ) {
                val inventoryViewModel: InventoryViewModel = viewModel(
                    factory = InventoryViewModel.Factory(repository)
                )

                InventoryListScreen(
                    viewModel = inventoryViewModel,
                    onItemClick = { itemId ->
                        backStack.add(ItemDetailKey(itemId))
                    },
                    onScanClick = {
                        backStack.add(ScannerKey)
                    },
                    onAddClick = {
                        backStack.add(ItemEditKey())
                    },
                    onSyncClick = {
                        backStack.add(SyncNowKey)
                    },
                    onSettingsClick = {
                        backStack.add(AppSettingsKey)
                    }
                )
            }

            entry<SyncSettingsKey>() {
                SyncSettingsScreen(
                    controller = syncController,
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<SyncNowKey>() {
                SyncSettingsScreen(
                    controller = syncController,
                    autoSyncOnOpen = true,
                    syncOnlyMode = true,
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<AppSettingsKey>() {
                AppSettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onLanSyncSettings = { backStack.add(SyncSettingsKey) },
                    updateController = updateController
                )
            }

            entry<ItemDetailKey>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { key ->
                val detailViewModel: ItemDetailViewModel = viewModel(
                    key = "ItemDetailViewModel_${key.itemId}",
                    factory = ItemDetailViewModel.Factory(repository, key.itemId)
                )

                ItemDetailScreen(
                    viewModel = detailViewModel,
                    onBackClick = {
                        backStack.removeLastOrNull()
                    },
                    onEditClick = { itemId ->
                        backStack.add(ItemEditKey(itemId = itemId))
                    },
                    onDeleted = {
                        backStack.removeLastOrNull()
                    }
                )
            }

            entry<ItemEditKey>(
                metadata = ListDetailSceneStrategy.detailPane()
            ) { key ->
                val editViewModel: ItemEditViewModel = viewModel(
                    key = "ItemEditViewModel_${key.itemId}_${key.initialBarcode}",
                    factory = ItemEditViewModel.Factory(repository, key)
                )

                ItemEditScreen(
                    viewModel = editViewModel,
                    onBackClick = {
                        backStack.removeLastOrNull()
                    },
                    onScanBarcodeClick = {
                        backStack.add(ScannerKey)
                    },
                    onSaved = { savedItemId ->
                        backStack.removeLastOrNull()
                        if (key.itemId == 0L) {
                            backStack.add(ItemDetailKey(savedItemId))
                        }
                    },
                    onDeleted = {
                        backStack.removeLastOrNull()
                    }
                )
            }

            entry<ScannerKey>(
                metadata = ListDetailSceneStrategy.extraPane()
            ) {
                val scannerViewModel: ScannerViewModel = viewModel(
                    factory = ScannerViewModel.Factory(repository)
                )

                ScannerContainerScreen(
                    viewModel = scannerViewModel,
                    onNavigateToDetail = { itemId ->
                        backStack.removeLastOrNull()
                        backStack.add(ItemDetailKey(itemId))
                    },
                    onNavigateToAddEdit = { editKey ->
                        backStack.removeLastOrNull()
                        backStack.add(editKey)
                    },
                    onClose = {
                        backStack.removeLastOrNull()
                    }
                )
            }
        }
    )
}
