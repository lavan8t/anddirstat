package com.kd.anddirstat.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEtaEstimatorTest {

    @Test
    fun testWarmupReturnsEmptyString() {
        val estimator = ScanEtaEstimator(warmupMs = 1500L)
        estimator.start(nowMs = 1000L)

        // Elapsed = 500ms (< 1500ms warmup)
        val result1 = estimator.update(currentBytes = 10_000_000L, totalTargetBytes = 100_000_000L, nowMs = 1500L)
        assertEquals("", result1)

        // Elapsed = 1400ms (< 1500ms warmup)
        val result2 = estimator.update(currentBytes = 20_000_000L, totalTargetBytes = 100_000_000L, nowMs = 2400L)
        assertEquals("", result2)

        // When currentBytes <= 0 even after warmup, should still return ""
        val resultZero = estimator.update(currentBytes = 0L, totalTargetBytes = 100_000_000L, nowMs = 3000L)
        assertEquals("", resultZero)

        // Elapsed = 1600ms (>= 1500ms warmup) with valid bytes, should return non-empty ETA
        val result3 = estimator.update(currentBytes = 30_000_000L, totalTargetBytes = 100_000_000L, nowMs = 2600L)
        assertTrue(result3.isNotEmpty())
        assertTrue(result3.startsWith(" (~"))
    }

    @Test
    fun testSpikeDamping() {
        val estimator = ScanEtaEstimator(warmupMs = 1000L)
        estimator.start(nowMs = 0L)

        // Warm up with steady ~10 MB/s rate over 1000MB target
        estimator.update(currentBytes = 10_000_000L, totalTargetBytes = 1_000_000_000L, nowMs = 1000L)
        val etaBeforeBurst = estimator.update(currentBytes = 20_000_000L, totalTargetBytes = 1_000_000_000L, nowMs = 2000L)
        assertTrue("Expected remaining time before burst, got: $etaBeforeBurst", etaBeforeBurst.contains("m") || etaBeforeBurst.contains("s"))

        // Sudden 500MB burst in 200ms: 20MB -> 520MB scanned
        val etaAfterBurst = estimator.update(currentBytes = 520_000_000L, totalTargetBytes = 1_000_000_000L, nowMs = 2200L)

        // ETA must not collapse to 0s or 1s instantly due to damping
        assertFalse("ETA should not collapse to 0s instantly", etaAfterBurst.contains("(~0s left)"))
        assertFalse("ETA should not collapse to 1s instantly", etaAfterBurst.contains("(~1s left)"))
        assertTrue("Damped ETA should still retain smooth estimate: $etaAfterBurst", etaAfterBurst.contains("m") || etaAfterBurst.contains("s"))
    }

    @Test
    fun testEtaFormatting() {
        val estimator = ScanEtaEstimator(warmupMs = 0L)
        estimator.start(nowMs = 0L)

        // Case 1: Under 60 seconds (1 MB/s, 45 MB remaining -> 45s left)
        val oneMb = 1024L * 1024L
        val total46Mb = 46L * oneMb
        val resultSeconds = estimator.update(currentBytes = oneMb, totalTargetBytes = total46Mb, nowMs = 1000L)
        assertEquals(" (~45s left)", resultSeconds)

        // Case 2: Over 60 seconds (1 MB/s, 135 MB remaining -> 2m 15s left)
        val estimator2 = ScanEtaEstimator(warmupMs = 0L)
        estimator2.start(nowMs = 0L)
        val total136Mb = 136L * oneMb
        val resultMinutes = estimator2.update(currentBytes = oneMb, totalTargetBytes = total136Mb, nowMs = 1000L)
        assertEquals(" (~2m 15s left)", resultMinutes)

        // Speed formatting test
        val speedStr = estimator2.getFormattedSpeed()
        assertEquals("1.0 MB/s", speedStr)

        // Speed formatting under 0.1 MB/s test
        val estimatorSlow = ScanEtaEstimator(warmupMs = 0L)
        estimatorSlow.start(nowMs = 0L)
        estimatorSlow.update(currentBytes = 1024L, totalTargetBytes = 10_000_000L, nowMs = 1000L)
        assertEquals("", estimatorSlow.getFormattedSpeed())
    }
}
