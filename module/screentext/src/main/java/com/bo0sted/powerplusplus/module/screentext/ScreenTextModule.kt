package com.bo0sted.powerplusplus.module.screentext

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TextFields
import com.bo0sted.powerplusplus.module.ActionModule
import com.bo0sted.powerplusplus.module.ModuleHost
import com.bo0sted.powerplusplus.module.PanelModule
import com.bo0sted.powerplusplus.textpick.showTextPick
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Boxes every piece of on-screen text read through accessibility; the user selects and copies. */
class ScreenTextModule @Inject constructor() : ActionModule {
    override val typeId = "screen_text"
    override val title = R.string.module_screen_text
    override val icon = Icons.Rounded.TextFields

    override suspend fun run(host: ModuleHost) {
        val blocks = withContext(Dispatchers.Default) { ScreenTextReader.read(host.service) }
        showTextPick(host.service, blocks)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface ScreenTextBindings {
    @Binds
    @IntoSet
    fun bind(module: ScreenTextModule): PanelModule
}
