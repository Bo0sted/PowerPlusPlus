package com.bo0sted.powerplusplus.di

import com.bo0sted.powerplusplus.module.ModuleRegistry
import com.bo0sted.powerplusplus.module.PanelModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ModuleSetDeclaration {
    /** Lets the set be empty while no `:module:*` is on the classpath. */
    @Multibinds
    fun panelModules(): Set<PanelModule>
}

@Module
@InstallIn(SingletonComponent::class)
internal object ModuleRegistryModule {
    @Provides
    @Singleton
    fun registry(modules: Set<@JvmSuppressWildcards PanelModule>): ModuleRegistry = ModuleRegistry(modules)
}
