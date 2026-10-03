package com.example.inventorytracker.ui.sync

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.inventorytracker.data.remote.sync.LanSyncPairingPayload
import com.example.inventorytracker.data.repository.LanSyncController
import com.example.inventorytracker.ui.scanner.BarcodeScannerScreen
import kotlinx.coroutines.launch

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SyncSettingsScreen(
    controller: LanSyncController,
    onBack: () -> Unit,
    autoSyncOnOpen: Boolean = false,
    syncOnlyMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(controller.desktopAddress) }
    var token by remember { mutableStateOf(controller.pairingToken) }
    var status by remember { mutableStateOf("Sync over Wi-Fi/Ethernet or through an ADB USB tunnel.") }
    var pending by remember { mutableIntStateOf(0) }
    var scanningPairingQr by remember { mutableStateOf(false) }
    var pairingRequest by remember { mutableStateOf<Pair<String, String>?>(null) }

    suspend fun updatePending() {
        pending = controller.pendingCount()
    }

    fun sync(syncAddress: String = address, syncToken: String = token) {
        val validationError = controller.savePairing(syncAddress, syncToken)
        if (validationError != null) {
            status = validationError
            return
        }
        scope.launch {
            status = if (syncAddress.startsWith("127.0.0.1:")) {
                "Connecting to desktop through the USB tunnel…"
            } else {
                "Connecting to desktop on the local network…"
            }
            when (val outcome = controller.syncNow()) {
                is LanSyncController.SyncOutcome.Success -> {
                    status = "Sync complete: ${outcome.uploaded} uploaded, ${outcome.downloaded} downloaded."
                    updatePending()
                }
                is LanSyncController.SyncOutcome.Failure -> status = outcome.message
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pairingRequest?.let { sync(it.first, it.second) }
        else status = "Allow local network access to sync with the desktop."
    }

    fun requestPermissionAndSync(syncAddress: String = address, syncToken: String = token) {
        if (syncAddress.isBlank() || syncToken.isBlank()) {
            status = "Scan the desktop pairing QR or enter its address and token first."
            return
        }
        pairingRequest = syncAddress to syncToken
        val usbTunnel = syncAddress.substringBefore(':') == "127.0.0.1"
        if (!usbTunnel && Build.VERSION.SDK_INT >= 37 &&
            context.checkSelfPermission(LOCAL_NETWORK_PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            sync(syncAddress, syncToken)
        }
    }

    LaunchedEffect(autoSyncOnOpen, syncOnlyMode) {
        updatePending()
        if (autoSyncOnOpen) requestPermissionAndSync()
    }

    if (scanningPairingQr) {
        BarcodeScannerScreen(
            modifier = Modifier.fillMaxSize(),
            instructionText = "Scan the pairing QR shown by Inventory Tracker on your desktop",
            onBarcodeScanned = { raw ->
                val payload = LanSyncPairingPayload.parse(raw)
                if (payload == null) {
                    status = "That QR code is not a valid desktop sync pairing code."
                } else {
                    scanningPairingQr = false
                    address = payload.address
                    token = payload.token
                    val error = controller.savePairing(payload.address, payload.token)
                    status = if (error != null) {
                        error
                    } else if (payload.address.startsWith("127.0.0.1:")) {
                        "USB connection saved. Keep the cable connected, then press Sync on the inventory screen."
                    } else {
                        "Desktop connection saved. Press Sync on the inventory screen to sync."
                    }
                }
            },
            onClose = { scanningPairingQr = false }
        )
    } else if (syncOnlyMode) {
        SyncRunScreen(
            status = status,
            pending = pending,
            paired = controller.desktopAddress.isNotBlank() && controller.pairingToken.isNotBlank(),
            onBack = onBack,
            modifier = modifier
        )
    } else Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("LAN sync settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                "Configure the desktop connection here. To run a database sync, return to Inventory and press Sync.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Desktop address") },
                placeholder = { Text("192.168.1.20:8765") },
                supportingText = { Text("Scan the desktop QR. USB pairing uses 127.0.0.1:8765.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Pairing token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Pending local changes: $pending", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Automatic sync is scheduled weekly on Mondays at midnight, starting October 5, 2026. The desktop app must be open, and the Wi-Fi network or USB tunnel must be available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { scanningPairingQr = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan desktop pairing QR")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val error = controller.savePairing(address, token)
                    status = error ?: "Desktop connection saved. Press Sync on the inventory screen to sync."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save connection")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    controller.disconnect()
                    address = ""
                    token = ""
                    status = "Desktop disconnected. Local inventory remains available."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect desktop")
            }
            Spacer(Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SyncRunScreen(
    status: String,
    pending: Int,
    paired: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Sync to desktop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text("Sync to desktop", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Pending local changes: $pending", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (!paired) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Set up the desktop connection in Settings → LAN sync settings, then press Sync again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
