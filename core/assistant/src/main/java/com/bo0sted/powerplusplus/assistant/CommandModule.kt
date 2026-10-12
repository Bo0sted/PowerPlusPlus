package com.bo0sted.powerplusplus.assistant

import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost

/**
 * A panel module that opens a command's modal inside the panel, as a spoken command does: the panel
 * stays open, and Back, Cancel or Confirm return to it. The panel shows [newDraft] itself, so [run]
 * is never called.
 */
interface CommandModule : ActionModule {
    fun newDraft(): CommandDraft

    override suspend fun run(host: ModuleHost) = Unit
}
