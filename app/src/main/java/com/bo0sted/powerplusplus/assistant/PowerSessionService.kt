package com.bo0sted.powerplusplus.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.bo0sted.powerplusplus.MainActivity
import com.bo0sted.powerplusplus.panel.PanelController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PowerSessionService : VoiceInteractionSessionService() {

    @Inject lateinit var panel: PanelController

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = PowerSession(this, panel)
}

/**
 * Started by the system when the assistant is invoked (press and hold power). It never shows a
 * window of its own: it hands off to the panel overlay and hides immediately.
 */
private class PowerSession(context: Context, private val panel: PanelController) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        if (!panel.show()) {
            // The accessibility service isn't connected, so there is nothing to draw with.
            startAssistantActivity(Intent(context, MainActivity::class.java))
        }
        hide()
    }
}
