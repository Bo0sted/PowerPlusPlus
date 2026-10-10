package com.bo0sted.powerplusplus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Colours for things the user picked in an overlay (text boxes, media). The theme's own colours
 * (even its error red, pale in dark mode) were too easy to miss (user, 2026-10-09), so these are a
 * fixed green and red, each tuned for the theme's brightness.
 */

/** A picked item. */
@Composable
fun pickedColor(): Color = if (isDarkSurface()) Color(0xFF6FCF7F) else Color(0xFF2E7D32)

/** A picked item the user changed. */
@Composable
fun editedColor(): Color = if (isDarkSurface()) Color(0xFFFF6B6B) else Color(0xFFC62828)

@Composable
private fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
