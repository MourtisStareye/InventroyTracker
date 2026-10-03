package com.example.inventorytracker.ui.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.serialization.Serializable

@Serializable
enum class BarcodeScanMode {
    UPC,
    QR,
    ALL
}

/**
 * ImageAnalysis.Analyzer implementation that uses ML Kit Barcode Scanning
 * to detect UPC/EAN and standard barcodes from live camera feed.
 *
 * Includes throttle/debounce logic to prevent rapid duplicate callbacks.
 */
class BarcodeAnalyzer(
    throttleMillis: Long = 1500L,
    scanMode: BarcodeScanMode = BarcodeScanMode.ALL,
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val throttler = BarcodeThrottler(throttleMillis)

    private val options = BarcodeScannerOptions.Builder().apply {
        val formats = when (scanMode) {
            BarcodeScanMode.UPC -> intArrayOf(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13
            )
            BarcodeScanMode.QR -> intArrayOf(Barcode.FORMAT_QR_CODE)
            BarcodeScanMode.ALL -> intArrayOf(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE
            )
        }
        setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
    }.build()

    private val scanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val currentTime = System.currentTimeMillis()
                for (barcode in barcodes) {
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank() && throttler.shouldProcess(rawValue, currentTime)) {
                        onBarcodeDetected(rawValue)
                        break
                    }
                }
            }
            .addOnFailureListener {
                // Ignore failure for individual frame processing
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
