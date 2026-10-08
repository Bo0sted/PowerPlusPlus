package com.bo0sted.powerplusplus.module.screenshot.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorEffectsTest {

    @Test
    fun strengthCoversAVeryWideRangeMonotonically() {
        assertEquals(1.5f, EditorEffects.blurRadius(0f), 0.01f)
        assertEquals(300f, EditorEffects.blurRadius(1f), 0.5f)
        assertEquals(2, EditorEffects.pixelBlock(0f))
        assertEquals(200, EditorEffects.pixelBlock(1f))
        val radii = (0..20).map { EditorEffects.blurRadius(it / 20f) }
        assertTrue(radii.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun outOfRangeStrengthIsClamped() {
        assertEquals(EditorEffects.blurRadius(1f), EditorEffects.blurRadius(5f))
        assertEquals(EditorEffects.pixelBlock(0f), EditorEffects.pixelBlock(-1f))
    }
}
