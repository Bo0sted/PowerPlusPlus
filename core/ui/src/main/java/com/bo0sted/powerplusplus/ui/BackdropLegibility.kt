package com.bo0sted.powerplusplus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Keeps light text readable over whatever the backdrop shows. The blur is composited by the system
 * after we draw, so its brightness can't be measured; a soft dark shadow works on white and black
 * alike (the usual approach for labels over wallpapers).
 */
@Composable
fun TextStyle.onBackdrop(): TextStyle {
    val density = LocalDensity.current
    return copy(
        shadow = Shadow(
            color = Color.Black.copy(alpha = 0.75f),
            offset = Offset(0f, with(density) { 1.dp.toPx() }),
            blurRadius = with(density) { 6.dp.toPx() },
        ),
    )
}

/** Outline for small non-text marks (page dots) over the backdrop. */
val BackdropOutline = Color.Black.copy(alpha = 0.45f)
