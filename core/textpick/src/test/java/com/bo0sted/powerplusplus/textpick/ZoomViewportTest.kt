package com.bo0sted.powerplusplus.textpick

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomViewportTest {

    private val size = Size(1000f, 2000f)

    @Test
    fun pinchKeepsThePointUnderTheFingers() {
        val centroid = Offset(300f, 800f)
        val zoomed = ZoomViewport().transformed(centroid, zoomChange = 2f, panChange = Offset.Zero, size = size)
        assertEquals(2f, zoomed.zoom)
        assertEquals(centroid, zoomed.toContent(centroid) * zoomed.zoom + zoomed.pan)
        assertEquals(Offset(300f, 800f), zoomed.toContent(centroid))
    }

    @Test
    fun zoomStaysBetweenOneAndTheMaximum() {
        assertEquals(1f, ZoomViewport().transformed(Offset(10f, 10f), 0.5f, Offset.Zero, size).zoom)
        assertEquals(ZoomViewport.MAX_ZOOM, ZoomViewport(5f).transformed(Offset.Zero, 3f, Offset.Zero, size).zoom)
    }

    @Test
    fun panningStopsAtTheContentEdges() {
        val zoomed = ZoomViewport(zoom = 2f, pan = Offset(-500f, -1000f))
        // Dragging far right/down can only bring the content's top-left corner to the view's.
        assertEquals(Offset.Zero, zoomed.transformed(Offset(500f, 1000f), 1f, Offset(5000f, 5000f), size).pan)
        // And far left/up only to its bottom-right corner.
        assertEquals(Offset(-1000f, -2000f), zoomed.transformed(Offset(500f, 1000f), 1f, Offset(-5000f, -5000f), size).pan)
        // Unzoomed, nothing pans.
        assertEquals(Offset.Zero, ZoomViewport().transformed(Offset(500f, 1000f), 1f, Offset(200f, 0f), size).pan)
    }

    @Test
    fun tapsMapBackToContent() {
        assertEquals(Offset(150f, 300f), ZoomViewport(zoom = 2f, pan = Offset(-100f, -200f)).toContent(Offset(200f, 400f)))
    }
}
