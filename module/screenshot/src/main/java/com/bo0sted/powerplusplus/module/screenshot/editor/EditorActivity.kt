package com.bo0sted.powerplusplus.module.screenshot.editor

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bo0sted.powerplusplus.module.screenshot.R
import com.bo0sted.powerplusplus.module.screenshot.ScreenshotOutput
import com.bo0sted.powerplusplus.ui.PowerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 2 of Smart screenshot. An activity rather than an overlay: accessibility overlays sit above
 * the keyboard, which the Text tool needs, and an activity gets normal Back handling.
 */
class EditorActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (viewModel.image == null) {
            finish()
            return
        }
        setContent {
            PowerTheme(darkTheme = true) {
                EditorScreen(viewModel, onClose = ::finish, onOutput = ::output)
            }
        }
    }

    private fun output(target: EditorOutput) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            val image = viewModel.render()
            if (image == null) {
                busy = false
                return@launch
            }
            when (target) {
                EditorOutput.Copy -> {
                    val copied = withContext(Dispatchers.IO) { ScreenshotOutput.copy(applicationContext, image) }
                    // Android 13+ shows its own clipboard confirmation.
                    if (!copied) toast(R.string.screenshot_failed)
                    else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) toast(R.string.screenshot_copied)
                }
                EditorOutput.Save -> {
                    val saved = withContext(Dispatchers.IO) { ScreenshotOutput.save(applicationContext, image) }
                    toast(if (saved) R.string.screenshot_saved else R.string.screenshot_failed)
                }
                EditorOutput.Share -> {
                    val intent = withContext(Dispatchers.IO) { ScreenshotOutput.shareIntent(applicationContext, image) }
                    if (intent == null) toast(R.string.screenshot_failed) else startActivity(intent)
                }
            }
            image.recycle()
            finish()
        }
    }

    private fun toast(message: Int) = Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}
