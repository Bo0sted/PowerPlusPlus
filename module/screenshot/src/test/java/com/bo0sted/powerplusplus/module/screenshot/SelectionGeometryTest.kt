package com.bo0sted.powerplusplus.module.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionGeometryTest {

    private val geometry = SelectionGeometry(Size(1000f, 2000f), minSize = 100f)

    @Test
    fun stripsSpanTheFullImageAndOnlyMoveAcross() {
        val horizontal = geometry.default(SelectionShape.HorizontalStrip)
        assertEquals(0f, horizontal.left)
        assertEquals(1000f, horizontal.right)

        val moved = geometry.move(horizontal, Offset(300f, 50f), SelectionShape.HorizontalStrip)
        assertEquals(horizontal.translate(0f, 50f), moved)

        val vertical = geometry.reshape(Rect(100f, 500f, 400f, 900f), SelectionShape.VerticalStrip)
        assertEquals(Rect(100f, 0f, 400f, 2000f), vertical)
    }

    @Test
    fun stripHandlesOnlyResizeTheAdjustableSide() {
        val strip = Rect(0f, 800f, 1000f, 1200f)
        assertEquals(Rect(0f, 700f, 1000f, 1200f), geometry.resize(strip, Handle.Top, Offset(40f, -100f), SelectionShape.HorizontalStrip))
        // Can't collapse below the minimum size.
        assertEquals(Rect(0f, 800f, 1000f, 900f), geometry.resize(strip, Handle.Bottom, Offset(0f, -1000f), SelectionShape.HorizontalStrip))
    }

    @Test
    fun circleStaysSquareAnchoredAtTheOppositeCornerAndInsideTheImage() {
        val circle = Rect(Offset(400f, 400f), Size(200f, 200f))
        assertEquals(Rect(Offset(400f, 400f), Size(300f, 300f)), geometry.resize(circle, Handle.BottomRight, Offset(100f, 20f), SelectionShape.Circle))
        // Growing toward the left edge stops at the image.
        assertEquals(Rect(Offset(0f, 0f), Size(600f, 600f)), geometry.resize(circle, Handle.TopLeft, Offset(-900f, -900f), SelectionShape.Circle))
    }

    @Test
    fun movingIsClampedToTheImage() {
        val rect = Rect(100f, 100f, 300f, 300f)
        assertEquals(Rect(0f, 0f, 200f, 200f), geometry.move(rect, Offset(-500f, -500f), SelectionShape.Rectangle))
        assertEquals(Rect(800f, 1800f, 1000f, 2000f), geometry.move(rect, Offset(5000f, 5000f), SelectionShape.Rectangle))
    }

    @Test
    fun reshapingToCircleCentersASquareOnTheSelection() {
        val circle = geometry.reshape(Rect(100f, 100f, 500f, 300f), SelectionShape.Circle)
        assertEquals(Rect(200f, 100f, 400f, 300f), circle)
    }
}
