package com.example.inventorytracker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventorytracker.data.repository.InventoryRepository
import com.example.inventorytracker.data.remote.update.GitHubRelease
import com.example.inventorytracker.data.remote.update.GitHubUpdateController
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppSettingsScreen(
    onBack: () -> Unit,
    onLanSyncSettings: () -> Unit,
    updateController: GitHubUpdateController,
    repository: InventoryRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var releaseOffer by remember { mutableStateOf<GitHubRelease?>(null) }
    var status by remember { mutableStateOf("") }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var deletingCategory by remember { mutableStateOf<String?>(null) }
    var categorySearch by remember { mutableStateOf("") }
    var editingType by remember { mutableStateOf<String?>(null) }
    var typeName by remember { mutableStateOf("") }
    var deletingType by remember { mutableStateOf<String?>(null) }
    var typeSearch by remember { mutableStateOf("") }
    val inventoryItems by repository.getAllItems().collectAsStateWithLifecycle(initialValue = emptyList())
    val categories = inventoryItems.asSequence()
        .mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()
    val visibleCategories = categories.filter { it.contains(categorySearch.trim(), ignoreCase = true) }
    val types = inventoryItems.asSequence()
        .mapNotNull { it.type?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()
    val visibleTypes = types.filter { it.contains(typeSearch.trim(), ignoreCase = true) }

    editingCategory?.let { original ->
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("Rename category") },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val renamed = categoryName.trim()
                    if (renamed.isNotEmpty()) {
                        scope.launch {
                            repository.renameCategory(original, renamed)
                            editingCategory = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingCategory = null }) { Text("Cancel") } }
        )
    }

    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text("Delete category?") },
            text = { Text("Remove ‘$category’ from the category list and clear it from matching inventory items?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteCategory(category)
                        deletingCategory = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingCategory = null }) { Text("Cancel") } }
        )
    }

    editingType?.let { original ->
        AlertDialog(
            onDismissRequest = { editingType = null },
            title = { Text("Rename type") },
            text = {
                OutlinedTextField(
                    value = typeName,
                    onValueChange = { typeName = it },
                    label = { Text("Type name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val renamed = typeName.trim()
                    if (renamed.isNotEmpty()) {
                        scope.launch {
                            repository.renameType(original, renamed)
                            editingType = null
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingType = null }) { Text("Cancel") } }
        )
    }

    deletingType?.let { itemType ->
        AlertDialog(
            onDismissRequest = { deletingType = null },
            title = { Text("Delete type?") },
            text = { Text("Remove ‘$itemType’ from the type list and clear it from matching inventory items?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.deleteType(itemType)
                        deletingType = null
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingType = null }) { Text("Cancel") } }
        )
    }

    releaseOffer?.let { release ->
        AlertDialog(
            onDismissRequest = { releaseOffer = null },
            title = { Text("Inventory Tracker ${release.tag} is available") },
            text = { Text(release.notes.ifBlank { "No release notes were provided." }) },
            confirmButton = {
                TextButton(onClick = {
                    releaseOffer = null
                    scope.launch {
                        runCatching {
                            val apk = updateController.downloadAndInstall(release)
                            updateController.installApk(apk)
                        }.onFailure { Toast.makeText(context, it.message ?: "Update could not be installed.", Toast.LENGTH_LONG).show() }
                    }
                }) { Text("Download and install") }
            },
            dismissButton = { TextButton(onClick = { releaseOffer = null }) { Text("Later") } }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Phone and desktop connection", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pair the desktop and configure Wi-Fi or USB sync. While this app is open, the desktop can request an immediate sync. Opening these settings does not start a sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLanSyncSettings, modifier = Modifier.fillMaxWidth()) {
                Text("LAN sync settings")
            }
            Spacer(Modifier.height(24.dp))
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Manage categories used by the add-item picklist. Renaming or deleting a category updates matching items and syncs the change.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                OutlinedTextField(
                    value = categorySearch,
                    onValueChange = { categorySearch = it },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    label = { Text("Search categories") },
                    singleLine = true
                )
                if (categories.isEmpty()) {
                    Text("No categories yet. Add one while creating or editing an item.", modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (visibleCategories.isEmpty()) {
                    Text("No categories match your search.", modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                        visibleCategories.forEach { category ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(category, modifier = Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = {
                                    categoryName = category
                                    editingCategory = category
                                }) { Text("Rename") }
                                TextButton(onClick = { deletingCategory = category }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Types", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Manage types used by the add-item picklist. Renaming or deleting a type updates matching items and syncs the change.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                OutlinedTextField(
                    value = typeSearch,
                    onValueChange = { typeSearch = it },
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    label = { Text("Search types") },
                    singleLine = true
                )
                if (types.isEmpty()) {
                    Text("No types yet. Add one while creating or editing an item.", modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (visibleTypes.isEmpty()) {
                    Text("No types match your search.", modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                        visibleTypes.forEach { itemType ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(itemType, modifier = Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = {
                                    typeName = itemType
                                    editingType = itemType
                                }) { Text("Rename") }
                                TextButton(onClick = { deletingType = itemType }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Software updates", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Checks the public GitHub Releases repository when the app starts. No token is needed. Updates show the version and release notes and wait for your approval before downloading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !checking, onClick = {
                    checking = true
                    status = "Checking GitHub Releases…"
                    scope.launch {
                        runCatching { updateController.checkForUpdate() }
                            .onSuccess { release ->
                                releaseOffer = release
                                status = if (release == null) "This app is up to date." else "Update ${release.tag} is available."
                            }
                            .onFailure { status = it.message ?: "Update check failed." }
                        checking = false
                    }
                }) { Text("Check for updates") }
                if (checking) CircularProgressIndicator(modifier = Modifier.height(30.dp))
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
