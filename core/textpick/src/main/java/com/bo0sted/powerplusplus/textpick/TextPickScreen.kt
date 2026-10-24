package com.bo0sted.powerplusplus.textpick

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.ZoomOut
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.toSize
import com.bo0sted.powerplusplus.ui.OverlayChoiceBar
import com.bo0sted.powerplusplus.ui.editedColor
import com.bo0sted.powerplusplus.ui.pickedColor
import kotlin.math.roundToInt

/** How a [TextPickScreen] picks text: whole blocks, or single words (separated by spaces). */
enum class PickMode(@get:StringRes val label: Int) {
    Words(R.string.textpick_mode_words),
    Blocks(R.string.textpick_mode_blocks),
}

/**
 * What a [TextPickScreen] has picked and which pick is being edited. The window's owner keeps it,
 * so Back can leave editing ([cancelEdit]) instead of closing.
 *
 * Word selection is offered, and is the default, when the blocks come with [TextBlock.words]. Each
 * mode keeps its own picks, so switching back and forth loses nothing; Copy uses the current mode's.
 */
@Stable
class TextPickState(val blocks: List<TextBlock>) {
    /** Every block's words, in reading order. */
    private val words: List<TextBlock> = blocks.flatMap { it.words.orEmpty() }

    /** The index in [blocks] of each of [words]. */
    private val blockOfWord: IntArray = blocks.flatMapIndexed { index, block -> List(block.words.orEmpty().size) { index } }.toIntArray()

    val modes: List<PickMode> = if (blocks.any { it.words != null }) PickMode.entries else listOf(PickMode.Blocks)

    var mode by mutableStateOf(modes.first())
        private set

    private var wordPicks by mutableStateOf(PickList())
    private var blockPicks by mutableStateOf(PickList())

    /** The current mode's picks; indices are into [targets]. */
    var picks: PickList
        get() = if (mode == PickMode.Words) wordPicks else blockPicks
        internal set(value) {
            if (mode == PickMode.Words) wordPicks = value else blockPicks = value
        }

    /** What can be picked in the current mode: the words or the blocks. */
    val targets: List<TextBlock>
        get() = if (mode == PickMode.Words) words else blocks

    /** The index of the target being edited, or `null`. */
    var editing by mutableStateOf<Int?>(null)
        private set

    internal fun selectMode(next: PickMode) {
        if (next in modes) mode = next
    }

    /**
     * What Copy puts on the clipboard, edits included. Blocks: one per line, in pick order. Words:
     * one line per block, in the order its first word was picked, with that block's picked words in
     * text order, separated by spaces.
     */
    internal fun copiedText(): String =
        if (mode == PickMode.Words) picks.joinedLines(words) { blockOfWord[it] } else picks.joinedText(blocks)

    internal fun startEdit(index: Int) {
        editing = index
    }

    fun cancelEdit() {
        editing = null
    }

    internal fun finishEdit(text: String) {
        val index = editing ?: return
        picks = picks.edited(index, text, targets[index].text)
        editing = null
    }
}

/**
 * Full-screen picker over the state's targets (its words or blocks, [TextPickState.mode]): each gets
 * a box. Tapping a box adds its text to the list (in tap order) or removes it; tapping elsewhere
 * closes unless [closeOnTapOutside] is off. The bottom toolbar opens the list, which can be scrolled
 * and whose entries can be edited or removed. Picked boxes are green, edited ones red. Copy copies
 * the picks, edits included ([TextPickState.copiedText]). When both modes are offered, a Word · Block
 * row under the toolbar switches between them.
 *
 * Editing shows only a card at the top with a text field: the owner fits the window above the
 * keyboard ([onEditingChange]), which the system draws below overlays.
 *
 * Over a [background], the image and its boxes can be pinched to zoom (up to 6×) and panned, like
 * a photo; taps still pick the box under the finger. While zoomed at all, Reset zoom appears just
 * above the list (or the toolbar, when the list is closed). Zoom survives editing.
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
    // Above the editing branch, so the zoom is still there after editing.
    var viewport by remember { mutableStateOf(ZoomViewport()) }
    LaunchedEffect(editing != null) { onEditingChange(editing != null) }
    if (editing != null) {
        EditCard(state, editing)
        return
    }

    val targets = state.targets
    val picks = state.picks
    // Block bounds are in screen pixels; this is where the window sits on screen.
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    var listOpen by remember { mutableStateOf(false) }
    val view = LocalView.current
    val density = LocalDensity.current
    val slopPx = with(density) { 10.dp.roundToPx() }
    val corner = with(density) { 4.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    val pickedStrokePx = with(density) { 2.dp.toPx() }
    val pickedAccent = pickedColor()
    val editedAccent = editedColor()
    val scrim = Color.Black.copy(alpha = 0.16f)

    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
            .onGloballyPositioned {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                origin = IntOffset(location[0], location[1])
            },
    ) {
        // Gestures are read on this untransformed box, in screen pixels; only the canvas is zoomed.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(background != null) {
                    if (background == null) return@pointerInput
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        viewport = viewport.transformed(centroid, zoomChange, pan, size.toSize())
                    }
                }
                .pointerInput(targets, closeOnTapOutside) {
                    detectTapGestures { tap ->
                        val point = viewport.toContent(tap)
                        val slop = (slopPx / viewport.zoom).roundToInt()
                        val hit = hitTest(targets, point.x.roundToInt() + origin.x, point.y.roundToInt() + origin.y, slop)
                        if (hit != null) state.picks = state.picks.toggled(hit) else if (closeOnTapOutside) onClose()
                    }
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = viewport.zoom
                        scaleY = viewport.zoom
                        translationX = viewport.pan.x
                        translationY = viewport.pan.y
                    },
            ) {
                // Outlines keep their on-screen width at any zoom.
                val stroke = Stroke(width = strokePx / viewport.zoom)
                val pickedStroke = Stroke(width = pickedStrokePx / viewport.zoom)
                if (background != null) {
                    drawImage(background, dstOffset = IntOffset(-origin.x, -origin.y), dstSize = IntSize(background.width, background.height))
                    drawRect(scrim)
                }
                targets.forEachIndexed { index, block ->
                    val topLeft = Offset((block.bounds.left - origin.x).toFloat(), (block.bounds.top - origin.y).toFloat())
                    val size = Size(block.bounds.width.toFloat(), block.bounds.height.toFloat())
                    val radius = CornerRadius(corner)
                    if (index in picks) {
                        val color = if (picks.isEdited(index)) editedAccent else pickedAccent
                        drawRoundRect(color.copy(alpha = 0.33f), topLeft, size, radius)
                        drawRoundRect(color.copy(alpha = 0.87f), topLeft, size, radius, pickedStroke)
                    } else {
                        drawRoundRect(Color.White.copy(alpha = 0.09f), topLeft, size, radius)
                        drawRoundRect(Color.White.copy(alpha = 0.27f), topLeft, size, radius, stroke)
                    }
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
            if (viewport.zoom > 1f) {
                FilledTonalButton(onClick = { viewport = ZoomViewport() }) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = null)
                    Text(stringResource(R.string.textpick_reset_zoom), Modifier.padding(start = 8.dp))
                }
            }
            if (listOpen && picks.size > 0) PickListPanel(state, Modifier.heightIn(max = LIST_MAX_HEIGHT))
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
                            text = stringResource(if (state.mode == PickMode.Words) R.string.textpick_hint_words else R.string.textpick_hint),
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
                    val allPicked = picks.size == targets.size
                    FilledTonalButton(onClick = { state.picks = if (allPicked) PickList() else picks.withAll(targets.size) }) {
                        Text(stringResource(if (allPicked) R.string.textpick_deselect_all else R.string.textpick_select_all))
                    }
                    Button(
                        enabled = picks.size > 0,
                        onClick = { onCopy(state.copiedText()) },
                    ) { Text(stringResource(R.string.textpick_copy)) }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.textpick_close))
                    }
                }
            }
            if (state.modes.size > 1) {
                OverlayChoiceBar(state.modes, state.mode, { stringResource(it.label) }, state::selectMode)
            }
        }
    }
}

/** About three entries (each at least 56 dp: the 48 dp buttons plus padding), then the list scrolls. */
private val LIST_MAX_HEIGHT = 56.dp * 3 + 2.dp + 16.dp

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
                        .heightIn(min = 56.dp)
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.picks.textOf(pick, state.targets),
                        style = pickTextStyle(),
                        color = if (pick.edited != null) editedColor() else MaterialTheme.colorScheme.onSurface,
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
    val original = state.targets[index].text
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
