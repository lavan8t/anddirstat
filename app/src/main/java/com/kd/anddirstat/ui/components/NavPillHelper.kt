package com.kd.anddirstat.ui.components

import com.kd.anddirstat.model.AppDestinations

object NavPillHelper {

    fun getDestinationIndex(destination: String): Int {
        return when (destination) {
            AppDestinations.EXPLORER -> 0
            AppDestinations.TYPES -> 1
            AppDestinations.DISCOVER -> 2
            else -> -1
        }
    }

    fun calculateEffectiveIndex(
        currentIndex: Int,
        targetIndex: Int = 0,
        predictiveBackProgress: Float = 0f,
        isPredictiveBackActive: Boolean = false
    ): Float {
        if (currentIndex !in 0..2) return -1f
        return if (isPredictiveBackActive && targetIndex in 0..2) {
            val clampedProgress = predictiveBackProgress.coerceIn(0f, 1f)
            currentIndex + (targetIndex - currentIndex) * clampedProgress
        } else {
            currentIndex.toFloat()
        }
    }

    fun calculateIndicatorOffsetPx(
        effectiveIndex: Float,
        slotPitchPx: Float
    ): Float {
        return effectiveIndex * slotPitchPx
    }
}
