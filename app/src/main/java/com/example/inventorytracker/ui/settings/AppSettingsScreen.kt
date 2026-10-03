package com.example.inventorytracker.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.inventorytracker.data.remote.update.GitHubRelease
import com.example.inventorytracker.data.remote.update.GitHubUpdateController
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppSettingsScreen(
    onBack: () -> Unit,
    onLanSyncSettings: () -> Unit,
    updateController: GitHubUpdateController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(updateController.savedToken()) }
    var checking by remember { mutableStateOf(false) }
    var releaseOffer by remember { mutableStateOf<GitHubRelease?>(null) }
    var status by remember { mutableStateOf("") }

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
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
            Text("Software updates", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Checks the private GitHub Releases repository when the app starts. Updates show the version and release notes and require your approval before downloading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Fine-grained GitHub token") },
                supportingText = { Text("Repository Contents: read only. Encrypted on this device. Blank keeps saved access.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    if (token.isNotBlank()) {
                        updateController.saveToken(token)
                        token = ""
                        status = "GitHub update access saved on this device."
                    } else status = "No new token entered; saved access was kept."
                }) { Text("Save access") }
                Button(onClick = {
                    updateController.clearToken()
                    token = ""
                    status = "Saved GitHub update access was cleared."
                }) { Text("Clear access") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(enabled = !checking, onClick = {
                    if (token.isNotBlank()) updateController.saveToken(token)
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
