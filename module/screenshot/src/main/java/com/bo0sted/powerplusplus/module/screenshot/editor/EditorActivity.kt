package com.bo0sted.powerplusplus.module.screenshot.editor

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.bo0sted.powerplusplus.module.screenshot.R
import com.bo0sted.powerplusplus.media.ImageOutput
import com.bo0sted.powerplusplus.ui.MESSAGE_CLOSE_DELAY_MS
import com.bo0sted.powerplusplus.ui.PowerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 2 of Smart screenshot. An activity rather than an overlay: accessibility overlays sit above
 * the keyboard, which the Text tool needs, and an activity gets normal Back handling.
 */
class EditorActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()
    private var busy = false

    /** Shown above the bottom panel instead of a toast, which Android hides while notifications are off. */
    private var message by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (viewModel.image == null) {
            finish()
            return
        }
        setContent {
            PowerTheme(darkTheme = true) {
                EditorScreen(viewModel, onClose = ::finish, onOutput = ::output, message = message?.let { stringResource(it) })
            }
        }
    }

    private fun output(target: EditorOutput) {
        if (busy) return
        busy = true
        message = null
        lifecycleScope.launch {
            val image = viewModel.render()
            if (image == null) {
                busy = false
                return@launch
            }
            // A confirmation is shown before closing; a failure keeps the editor open to try again.
            val outcome: Int? = when (target) {
                EditorOutput.Copy -> {
                    val copied = withContext(Dispatchers.IO) { ImageOutput.copy(applicationContext, image) }
                    when {
                        !copied -> FAILED
                        // Android 13+ shows its own clipboard confirmation.
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 -> R.string.screenshot_copied
                        else -> null
                    }
                }
                EditorOutput.Save -> {
                    val saved = withContext(Dispatchers.IO) { ImageOutput.save(applicationContext, image) }
                    if (saved) R.string.screenshot_saved else FAILED
                }
                EditorOutput.Share -> {
                    val intent = withContext(Dispatchers.IO) { ImageOutput.shareIntent(applicationContext, image) }
                    if (intent == null) FAILED else null.also { startActivity(intent) }
                }
            }
            image.recycle()
            when (outcome) {
                null -> finish()
                FAILED -> {
                    message = FAILED
                    busy = false
                }
                else -> {
                    message = outcome
                    delay(MESSAGE_CLOSE_DELAY_MS)
                    finish()
                }
            }
        }
    }

    private companion object {
        val FAILED = R.string.screenshot_failed
    }
}
