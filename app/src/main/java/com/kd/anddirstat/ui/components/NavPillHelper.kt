package com.kd.anddirstat.ui.components

import com.kd.anddirstat.model.AppDestinations

object NavPillHelper {

    fun getDestinationIndex(destination: String): Int {
        return when (destination) {
            AppDestinations.TREE -> 0
            AppDestinations.EXPLORER -> 1
            AppDestinations.TYPES -> 2
            AppDestinations.DISCOVER -> 3
            else -> 0
        }
    }

    fun calculateEffectiveIndex(
        currentIndex: Int,
        targetIndex: Int = 0,
        predictiveBackProgress: Float = 0f,
        isPredictiveBackActive: Boolean = false
    ): Float {
        return if (isPredictiveBackActive) {
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
