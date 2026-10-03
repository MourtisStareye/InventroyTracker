package com.example.inventorytracker.ui.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BarcodeThrottlerTest {

    private lateinit var throttler: BarcodeThrottler

    @Before
    fun setUp() {
        throttler = BarcodeThrottler(throttleMillis = 1500L)
    }

    @Test
    fun shouldProcess_firstScan_returnsTrue() {
        val result = throttler.shouldProcess("123456789012", currentTimeMillis = 1000L)
        assertTrue(result)
    }

    @Test
    fun shouldProcess_duplicateScanWithinThrottle_returnsFalse() {
        throttler.shouldProcess("123456789012", currentTimeMillis = 1000L)
        val duplicateResult = throttler.shouldProcess("123456789012", currentTimeMillis = 1500L) // 500ms later
        assertFalse(duplicateResult)
    }

    @Test
    fun shouldProcess_duplicateScanAfterThrottle_returnsTrue() {
        throttler.shouldProcess("123456789012", currentTimeMillis = 1000L)
        val resultAfterThrottle = throttler.shouldProcess("123456789012", currentTimeMillis = 2600L) // 1600ms later
        assertTrue(resultAfterThrottle)
    }

    @Test
    fun shouldProcess_differentBarcodeWithinThrottle_returnsTrue() {
        throttler.shouldProcess("123456789012", currentTimeMillis = 1000L)
        val differentResult = throttler.shouldProcess("987654321098", currentTimeMillis = 1200L) // different code
        assertTrue(differentResult)
    }

    @Test
    fun shouldProcess_blankBarcode_returnsFalse() {
        val blankResult = throttler.shouldProcess("   ", currentTimeMillis = 1000L)
        assertFalse(blankResult)
    }

    @Test
    fun reset_allowsRescanningImmediately() {
        throttler.shouldProcess("123456789012", currentTimeMillis = 1000L)
        throttler.reset()
        val resultAfterReset = throttler.shouldProcess("123456789012", currentTimeMillis = 1100L)
        assertTrue(resultAfterReset)
    }
}
