package com.kd.anddirstat.scanner

import java.util.Locale

class ScanEtaEstimator(
    val warmupMs: Long = 1500L,
    val smoothingAlpha: Double = 0.20
) {
    private var isStarted: Boolean = false
    private var startTimeMs: Long = 0L
    private var lastSampleTimeMs: Long = 0L
    private var lastSampleBytes: Long = 0L
    private var smoothedBytesPerSec: Double = 0.0
    private var smoothedEtaSeconds: Int = -1

    fun start(nowMs: Long = System.currentTimeMillis()) {
        isStarted = true
        startTimeMs = nowMs
        lastSampleTimeMs = nowMs
        lastSampleBytes = 0L
        smoothedBytesPerSec = 0.0
        smoothedEtaSeconds = -1
    }

    fun update(
        currentBytes: Long,
        totalTargetBytes: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (!isStarted) {
            start(nowMs)
        }

        val elapsed = nowMs - startTimeMs

        val dt = nowMs - lastSampleTimeMs
        if (dt >= 200L) {
            val deltaBytes = currentBytes - lastSampleBytes
            if (deltaBytes >= 0L) {
                val instantRate = (deltaBytes.toDouble() / dt.toDouble()) * 1000.0
                smoothedBytesPerSec = if (smoothedBytesPerSec <= 0.0) {
                    instantRate
                } else {
                    smoothingAlpha * instantRate + (1.0 - smoothingAlpha) * smoothedBytesPerSec
                }
                lastSampleTimeMs = nowMs
                lastSampleBytes = currentBytes
            }
        }

        if (elapsed < warmupMs || currentBytes <= 0L) {
            return ""
        }

        if (smoothedBytesPerSec <= 0.0) {
            return ""
        }

        val remainingBytes = maxOf(0L, totalTargetBytes - currentBytes)
        val rawEtaSec = (remainingBytes.toDouble() / smoothedBytesPerSec).toInt()

        smoothedEtaSeconds = if (smoothedEtaSeconds < 0) {
            rawEtaSec
        } else {
            val minEta = (smoothedEtaSeconds * 0.7).toInt()
            val maxEta = (smoothedEtaSeconds * 1.3).toInt() + 1
            val clamped = rawEtaSec.coerceIn(minEta, maxEta)
            ((smoothedEtaSeconds * 0.75) + (clamped * 0.25)).toInt()
        }

        val eta = smoothedEtaSeconds
        return if (eta < 60) " (~${eta}s left)" else " (~${eta / 60}m ${eta % 60}s left)"
    }

    fun getFormattedSpeed(): String {
        val mbps = smoothedBytesPerSec / (1024.0 * 1024.0)
        return if (mbps >= 0.1) {
            String.format(Locale.US, "%.1f MB/s", mbps)
        } else {
            ""
        }
    }
}
