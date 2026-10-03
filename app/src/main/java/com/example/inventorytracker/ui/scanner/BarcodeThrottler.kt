package com.example.inventorytracker.ui.scanner

/**
 * Helper class to throttle/debounce barcode scan events to prevent duplicate spamming callbacks.
 */
class BarcodeThrottler(
    private val throttleMillis: Long = 1500L
) {
    @Volatile
    private var lastScannedCode: String? = null

    @Volatile
    private var lastScannedTime: Long = 0L

    /**
     * Checks whether the given barcode should be processed.
     * Returns true if it's a new code or if enough time has passed since the last scan.
     */
    fun shouldProcess(code: String, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        if (code.isBlank()) return false

        val isDuplicate = (code == lastScannedCode) &&
                (currentTimeMillis - lastScannedTime < throttleMillis)

        return if (!isDuplicate) {
            lastScannedCode = code
            lastScannedTime = currentTimeMillis
            true
        } else {
            false
        }
    }

    fun reset() {
        lastScannedCode = null
        lastScannedTime = 0L
    }
}
