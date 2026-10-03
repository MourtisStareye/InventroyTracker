package com.example.inventorytracker.ui.scanner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventorytracker.data.repository.BarcodeSearchResult
import com.example.inventorytracker.ui.navigation.ItemDetailKey
import com.example.inventorytracker.ui.navigation.ItemEditKey

@Composable
fun ScannerContainerScreen(
    viewModel: ScannerViewModel,
    onNavigateToDetail: (itemId: Long) -> Unit,
    onNavigateToAddEdit: (ItemEditKey) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val scannedBarcode by viewModel.scannedBarcode.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()

    LaunchedEffect(scanResult) {
        val result = scanResult ?: return@LaunchedEffect
        when (result) {
            is BarcodeSearchResult.FoundLocal -> {
                val itemId = result.item.id
                viewModel.resetScanState()
                onNavigateToDetail(itemId)
            }
            is BarcodeSearchResult.FoundRemote -> {
                val remoteItem = result.item
                val editKey = ItemEditKey(
                    itemId = 0L,
                    initialBarcode = remoteItem.barcode,
                    initialName = remoteItem.name,
                    initialBrand = remoteItem.brand,
                    initialCategory = remoteItem.category,
                    initialImageUrl = remoteItem.imageUrl
                )
                viewModel.resetScanState()
                onNavigateToAddEdit(editKey)
            }
            is BarcodeSearchResult.NotFound -> {
                val barcode = result.barcode
                val editKey = ItemEditKey(
                    itemId = 0L,
                    initialBarcode = barcode
                )
                viewModel.resetScanState()
                onNavigateToAddEdit(editKey)
            }
            is BarcodeSearchResult.Error -> {
                val barcode = result.barcode
                val editKey = ItemEditKey(
                    itemId = 0L,
                    initialBarcode = barcode
                )
                viewModel.resetScanState()
                onNavigateToAddEdit(editKey)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BarcodeScannerScreen(
            throttleMillis = 1500L,
            onBarcodeScanned = { barcode ->
                if (!isProcessing) {
                    viewModel.onBarcodeScanned(barcode)
                }
            },
            onClose = onClose
        )

        if (isProcessing) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.9f)
                    .padding(bottom = 48.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Looking up product...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!scannedBarcode.isNull_or_blank()) {
                            Text(
                                text = "Barcode: $scannedBarcode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this.isNullOrBlank()
