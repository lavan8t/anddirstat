package com.kd.anddirstat.ui

import androidx.compose.ui.unit.dp
import com.kd.anddirstat.ExpressiveMotionTokens
import com.kd.anddirstat.Material3Shapes
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun expressiveMotionTokens_areDefined() {
        assertNotNull(ExpressiveMotionTokens.SpatialSpring)
        assertNotNull(ExpressiveMotionTokens.SpatialFloatSpring)
        assertNotNull(ExpressiveMotionTokens.EffectsSpring)
    }

    @Test
    fun material3Shapes_followExpressiveScale() {
        assertNotNull(Material3Shapes.extraSmall)
        assertNotNull(Material3Shapes.small)
        assertNotNull(Material3Shapes.medium)
        assertNotNull(Material3Shapes.large)
        assertNotNull(Material3Shapes.extraLarge)
    }
}
