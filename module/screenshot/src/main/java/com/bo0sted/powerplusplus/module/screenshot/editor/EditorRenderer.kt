package com.bo0sted.powerplusplus.module.screenshot.editor

import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * Drawing for the editor, in image-pixel coordinates. The same code renders the screen (inside a
 * fit-to-view transform) and the export (at 1:1 with Density(1f)), so the output matches the screen.
 */

/** Processed effect images, keyed by [EditorEffects.cacheKey]. */
typealias EffectImages = Map<Pair<EffectKind, Int>, ImageBitmap>

/** Draws [image] and then [marks] in order. */
fun DrawScope.drawEdits(image: ImageBitmap, marks: List<Mark>, effects: EffectImages, textMeasurer: TextMeasurer) {
    drawImage(image)
    marks.forEach { drawMark(it, effects, textMeasurer, image) }
}

/**
 * Draws one mark. An effect whose processed image isn't ready is skipped, or — with
 * [placeholderWhilePending], used for the stroke being drawn — shown as a translucent fill so the
 * user sees it immediately.
 */
fun DrawScope.drawMark(
    mark: Mark,
    effects: EffectImages,
    textMeasurer: TextMeasurer,
    image: ImageBitmap,
    placeholderWhilePending: Boolean = false,
) {
    when (mark) {
        is Mark.Pen -> drawPath(polyline(mark.points), Color(mark.color), style = roundStroke(mark.width))
        is Mark.Arrow -> drawArrow(mark)
        is Mark.Box -> drawRect(Color(mark.color), mark.rect.topLeft, mark.rect.size, style = roundStroke(mark.width))
        is Mark.Ellipse -> drawOval(Color(mark.color), mark.rect.topLeft, mark.rect.size, style = roundStroke(mark.width))
        is Mark.Label -> drawLabel(mark, textMeasurer)
        is Mark.Effect -> {
            val processed = effects[EditorEffects.cacheKey(mark.kind, mark.strength)]
            when {
                processed != null -> drawEffect(processed, mark.area, image)
                placeholderWhilePending -> drawEffectPlaceholder(mark.area)
            }
        }
    }
}

/** Lays out [label]'s text; the layout's size is in image pixels whatever [density] is. */
fun measureLabel(textMeasurer: TextMeasurer, label: Mark.Label, density: Density): TextLayoutResult =
    textMeasurer.measure(label.text, labelStyle(label, density), density = density)

/** The area a label occupies, including its background box. Used for hit testing and selection. */
fun labelBounds(layout: TextLayoutResult, label: Mark.Label): Rect {
    val padding = labelPadding(label)
    return Rect(
        label.position.x - padding,
        label.position.y - padding,
        label.position.x + layout.size.width + padding,
        label.position.y + layout.size.height + padding,
    )
}

private fun DrawScope.drawLabel(label: Mark.Label, textMeasurer: TextMeasurer) {
    val layout = measureLabel(textMeasurer, label, this)
    if (label.background) {
        val box = labelBounds(layout, label)
        drawRoundRect(Color(label.color), box.topLeft, box.size, CornerRadius(label.size * 0.25f))
    }
    drawText(layout, color = labelTextColor(label), topLeft = label.position)
}

/** Font size in sp such that the text is [Mark.Label.size] image pixels tall under [density]. */
private fun labelStyle(label: Mark.Label, density: Density) =
    TextStyle(fontSize = (label.size / density.density / density.fontScale).sp)

private fun labelPadding(label: Mark.Label) = if (label.background) label.size * 0.3f else 0f

private fun labelTextColor(label: Mark.Label): Color {
    val color = Color(label.color)
    if (!label.background) return color
    return if (color.luminance() > 0.5f) Color.Black else Color.White
}

private fun DrawScope.drawEffect(processed: ImageBitmap, area: EffectArea, image: ImageBitmap) {
    when (area) {
        is EffectArea.Box -> clipRect(area.rect.left, area.rect.top, area.rect.right, area.rect.bottom) { drawImage(processed) }
        // The stroke goes into a layer as a mask, then the processed image is kept only where the
        // mask is (SrcIn). The layer spans the image, not this scope's size: on screen that is the
        // view, in a scaled coordinate space, and would clip the effect.
        is EffectArea.Brush -> drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), Paint())
            drawPath(polyline(area.points), Color.Black, style = roundStroke(area.diameter))
            drawImage(processed, blendMode = BlendMode.SrcIn)
            canvas.restore()
        }
    }
}

private fun DrawScope.drawEffectPlaceholder(area: EffectArea) {
    val fill = Color.White.copy(alpha = 0.35f)
    when (area) {
        is EffectArea.Box -> drawRect(fill, area.rect.topLeft, area.rect.size)
        is EffectArea.Brush -> drawPath(polyline(area.points), fill, style = roundStroke(area.diameter))
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
