package com.bo0sted.powerplusplus.textpick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What a [TextPickScreen] has picked and which pick is being edited. The window's owner keeps it,
 * so Back can leave editing ([cancelEdit]) instead of closing.
 */
@Stable
class TextPickState(val blocks: List<TextBlock>) {
    var picks by mutableStateOf(PickList())
        internal set

    /** The index of the block being edited, or `null`. */
    var editing by mutableStateOf<Int?>(null)
        private set

    internal fun startEdit(index: Int) {
        editing = index
    }

    fun cancelEdit() {
        editing = null
    }

    internal fun finishEdit(text: String) {
        val index = editing ?: return
        picks = picks.edited(index, text, blocks[index].text)
        editing = null
    }
}

/**
 * Full-screen picker over the state's blocks: each block gets a box. Tapping a box adds its text to
 * the list (in tap order) or removes it; tapping elsewhere closes unless [closeOnTapOutside] is off.
 * The bottom toolbar opens the list, which can be scrolled and whose entries can be edited or
 * removed; edited boxes get their own colour. Copy copies the list, edits included, one per line.
 *
 * Editing shows only a card at the top with a text field: the owner shrinks the window
 * ([onEditingChange]) so the keyboard, which the system draws below overlays, stays visible.
 *
 * @param background drawn under the boxes instead of the live screen: a display-sized capture, in
 *   the same screen pixels as the block bounds.
 * @param closeOnTapOutside off where the results took a while to get (OCR), so a stray tap can't
 *   throw them away; Back and Close still close.
 */
@Composable
fun TextPickScreen(
    state: TextPickState,
    onCopy: (String) -> Unit,
    onClose: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    background: ImageBitmap? = null,
    closeOnTapOutside: Boolean = true,
) {
    val editing = state.editing
    LaunchedEffect(editing != null) { onEditingChange(editing != null) }
    if (editing != null) {
        EditCard(state, editing)
        return
    }

    val blocks = state.blocks
    val picks = state.picks
    // Block bounds are in screen pixels; this is where the window sits on screen.
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var listOpen by remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val slopPx = with(density) { 10.dp.roundToPx() }
    val corner = with(density) { 4.dp.toPx() }
    val stroke = Stroke(width = with(density) { 1.dp.toPx() })
    val pickedStroke = Stroke(width = with(density) { 2.dp.toPx() })
    val accent = MaterialTheme.colorScheme.primary
    val editedAccent = MaterialTheme.colorScheme.tertiary
    val scrim = Color.Black.copy(alpha = 0.16f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(scrim)
            .onGloballyPositioned {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                origin = IntOffset(location[0], location[1])
            },
    ) {
        val listMaxHeight = maxHeight * 0.4f
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(blocks, closeOnTapOutside) {
                    detectTapGestures { tap ->
                        val hit = hitTest(blocks, tap.x.toInt() + origin.x, tap.y.toInt() + origin.y, slopPx)
                        if (hit != null) state.picks = state.picks.toggled(hit) else if (closeOnTapOutside) onClose()
                    }
                },
        ) {
            if (background != null) {
                drawImage(background, dstOffset = IntOffset(-origin.x, -origin.y), dstSize = IntSize(background.width, background.height))
                drawRect(scrim)
            }
            blocks.forEachIndexed { index, block ->
                val topLeft = Offset((block.bounds.left - origin.x).toFloat(), (block.bounds.top - origin.y).toFloat())
                val size = Size(block.bounds.width.toFloat(), block.bounds.height.toFloat())
                val radius = CornerRadius(corner)
                if (index in picks) {
                    val color = if (picks.isEdited(index)) editedAccent else accent
                    drawRoundRect(color.copy(alpha = 0.33f), topLeft, size, radius)
                    drawRoundRect(color.copy(alpha = 0.87f), topLeft, size, radius, pickedStroke)
                } else {
                    drawRoundRect(Color.White.copy(alpha = 0.09f), topLeft, size, radius)
                    drawRoundRect(Color.White.copy(alpha = 0.27f), topLeft, size, radius, stroke)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                // Taps on the list and toolbar must not reach the canvas.
                .pointerInput(Unit) { detectTapGestures { } },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (listOpen && picks.size > 0) PickListPanel(state, Modifier.heightIn(max = listMaxHeight))
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (picks.size == 0) {
                        Text(
                            text = stringResource(R.string.textpick_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    } else {
                        TextButton(onClick = { listOpen = !listOpen }) {
                            Text(pluralStringResource(R.plurals.textpick_selected, picks.size, picks.size))
                            Icon(
                                if (listOpen) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                contentDescription = stringResource(if (listOpen) R.string.textpick_hide_list else R.string.textpick_show_list),
                            )
                        }
                    }
                    val allPicked = picks.size == blocks.size
                    FilledTonalButton(onClick = { state.picks = if (allPicked) PickList() else picks.withAll(blocks.size) }) {
                        Text(stringResource(if (allPicked) R.string.textpick_deselect_all else R.string.textpick_select_all))
                    }
                    Button(
                        enabled = picks.size > 0,
                        onClick = { onCopy(picks.joinedText(blocks)) },
                    ) { Text(stringResource(R.string.textpick_copy)) }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.textpick_close))
                    }
                }
            }
        }
    }
}

/** Large, slightly spaced text, so each character can be checked. */
@Composable
private fun pickTextStyle(): TextStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, letterSpacing = 0.5.sp)

/** The picks in order; scrolls, and follows each new pick to the end. Tap an entry to edit it. */
@Composable
private fun PickListPanel(state: TextPickState, modifier: Modifier) {
    val picks = state.picks.picks
    val listState = rememberLazyListState()
    LaunchedEffect(picks.size) { if (picks.isNotEmpty()) listState.animateScrollToItem(picks.lastIndex) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
        modifier = modifier.fillMaxWidth(),
    ) {
        LazyColumn(state = listState, modifier = Modifier.padding(vertical = 8.dp)) {
            itemsIndexed(picks, key = { _, pick -> pick.index }) { position, pick ->
                if (position > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.startEdit(pick.index) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.picks.textOf(pick, state.blocks),
                        style = pickTextStyle(),
                        color = if (pick.edited != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    IconButton(onClick = { state.startEdit(pick.index) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.textpick_edit))
                    }
                    IconButton(onClick = { state.picks = state.picks.toggled(pick.index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.textpick_remove))
                    }
                }
            }
        }
    }
}

/** The editor for one pick, alone at the top of the (shrunk) window, above the keyboard. */
@Composable
private fun EditCard(state: TextPickState, index: Int) {
    val original = state.blocks[index].text
    val current = state.picks.picks.firstOrNull { it.index == index }?.edited ?: original
    var value by remember(index) { mutableStateOf(TextFieldValue(current, selection = TextRange(current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(index) { focus.requestFocus() }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.textpick_original, original),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                textStyle = pickTextStyle(),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (value.text != original) {
                    TextButton(onClick = { value = TextFieldValue(original, selection = TextRange(original.length)) }) {
                        Text(stringResource(R.string.textpick_restore))
                    }
                }
                TextButton(onClick = state::cancelEdit) { Text(stringResource(R.string.textpick_cancel)) }
                Button(onClick = { state.finishEdit(value.text) }) { Text(stringResource(R.string.textpick_done)) }
            }
        }
    }
}
