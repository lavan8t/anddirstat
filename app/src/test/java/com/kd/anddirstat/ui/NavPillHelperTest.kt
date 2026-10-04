package com.kd.anddirstat.ui

import com.kd.anddirstat.model.AppDestinations
import com.kd.anddirstat.ui.components.NavPillHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class NavPillHelperTest {

    @Test
    fun testDestinationIndices() {
        assertEquals(0, NavPillHelper.getDestinationIndex(AppDestinations.TREE))
        assertEquals(1, NavPillHelper.getDestinationIndex(AppDestinations.EXPLORER))
        assertEquals(2, NavPillHelper.getDestinationIndex(AppDestinations.TYPES))
        assertEquals(3, NavPillHelper.getDestinationIndex(AppDestinations.DISCOVER))
        assertEquals(0, NavPillHelper.getDestinationIndex("unknown"))
    }

    @Test
    fun testEffectiveIndexWhenInactive() {
        val effective = NavPillHelper.calculateEffectiveIndex(
            currentIndex = 2,
            targetIndex = 0,
            predictiveBackProgress = 0.5f,
            isPredictiveBackActive = false
        )
        assertEquals(2.0f, effective, 0.001f)
    }

    @Test
    fun testEffectiveIndexWhenPredictiveBackActive() {
        // Halfway gesture from Explorer (index 1) to Tree (index 0)
        val halfway = NavPillHelper.calculateEffectiveIndex(
            currentIndex = 1,
            targetIndex = 0,
            predictiveBackProgress = 0.5f,
            isPredictiveBackActive = true
        )
        assertEquals(0.5f, halfway, 0.001f)

        // Fully completed gesture progress
        val completed = NavPillHelper.calculateEffectiveIndex(
            currentIndex = 1,
            targetIndex = 0,
            predictiveBackProgress = 1.0f,
            isPredictiveBackActive = true
        )
        assertEquals(0.0f, completed, 0.001f)
    }

    @Test
    fun testOffsetPxCalculation() {
        val slotPitchPx = 100f
        val offset = NavPillHelper.calculateIndicatorOffsetPx(effectiveIndex = 1.5f, slotPitchPx = slotPitchPx)
        assertEquals(150f, offset, 0.001f)
    }
}
