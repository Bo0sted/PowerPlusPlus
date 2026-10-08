package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the image and its marks in image-pixel coordinates. Used both on screen (inside a scaling
 * transform) and for export (at 1:1), so what the user sees is exactly what gets copied.
 *
 * @param effects processed images keyed by [EditorEffects.cacheKey]; an effect whose image isn't
 *   ready yet is skipped.
 */
fun DrawScope.drawEdits(
    image: ImageBitmap,
    marks: List<Mark>,
    effects: Map<Pair<EffectKind, Int>, ImageBitmap>,
    textMeasurer: TextMeasurer,
) {
    drawImage(image)
    for (mark in marks) {
        when (mark) {
            is Mark.Pen -> drawPath(polyline(mark.points), Color(mark.color), style = roundStroke(mark.width))
            is Mark.Arrow -> drawArrow(mark)
            is Mark.Box -> drawRect(Color(mark.color), mark.rect.topLeft, mark.rect.size, style = roundStroke(mark.width))
            is Mark.Ellipse -> drawOval(Color(mark.color), mark.rect.topLeft, mark.rect.size, style = roundStroke(mark.width))
            is Mark.Label -> drawText(
                textMeasurer = textMeasurer,
                text = mark.text,
                topLeft = mark.position,
                // The size is already in image pixels; this scope's density is 1 for export and
                // the on-screen transform scales it like everything else.
                style = TextStyle(color = Color(mark.color), fontSize = (mark.size / density / fontScale).sp),
            )
            is Mark.Effect -> {
                val processed = effects[EditorEffects.cacheKey(mark.kind, mark.strength)] ?: continue
                drawEffect(processed, mark.area)
            }
        }
    }
}

private fun DrawScope.drawEffect(processed: ImageBitmap, area: EffectArea) {
    when (area) {
        is EffectArea.Box -> clipRect(area.rect.left, area.rect.top, area.rect.right, area.rect.bottom) { drawImage(processed) }
        // The stroke is drawn into a layer as a mask, then the processed image is kept only where
        // the mask is (SrcIn).
        // The layer spans the image (not this scope's size, which on screen is the view, in a
        // scaled coordinate space) so effects are never clipped.
        is EffectArea.Brush -> drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, processed.width.toFloat(), processed.height.toFloat()), Paint())
            drawPath(polyline(area.points), Color.Black, style = roundStroke(area.diameter))
            drawImage(processed, blendMode = BlendMode.SrcIn)
            canvas.restore()
        }
    }
}

private fun DrawScope.drawArrow(arrow: Mark.Arrow) {
    val color = Color(arrow.color)
    drawLine(color, arrow.start, arrow.end, arrow.width, StrokeCap.Round)
    val angle = atan2(arrow.end.y - arrow.start.y, arrow.end.x - arrow.start.x)
    val head = arrow.width * 4f
    for (side in listOf(-1f, 1f)) {
        val a = angle + Math.PI.toFloat() + side * HEAD_ANGLE
        drawLine(color, arrow.end, arrow.end + Offset(cos(a) * head, sin(a) * head), arrow.width, StrokeCap.Round)
    }
}

/** A path through [points]; a single point still draws as a dot thanks to the round cap. */
private fun polyline(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    if (points.size == 1) lineTo(points.first().x, points.first().y) else points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

private const val HEAD_ANGLE = 0.45f
