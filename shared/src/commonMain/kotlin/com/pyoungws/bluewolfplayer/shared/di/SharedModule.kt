package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.JellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.SdkJellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.HomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServerConnectViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SignInViewModel
import com.pyoungws.bluewolfplayer.shared.server.SdkServerDiscovery
import com.pyoungws.bluewolfplayer.shared.server.ServerDiscovery
import com.pyoungws.bluewolfplayer.shared.server.ServerRepository
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Shared bindings. The platform must also provide an [org.jellyfin.sdk.Jellyfin] instance and a
 * [com.pyoungws.bluewolfplayer.shared.auth.SessionStore].
 */
val sharedModule = module {
    single<ServerDiscovery> { SdkServerDiscovery(get()) }
    single<JellyfinAuthDataSource> { SdkJellyfinAuthDataSource(get()) }
    single { ServerRepository(get()) }
    single { AuthRepository(get(), get()) }

    viewModelOf(::AppViewModel)
    viewModelOf(::ServerConnectViewModel)
    viewModelOf(::HomeViewModel)
    viewModel { (serverAddress: String) -> SignInViewModel(serverAddress, get()) }
}
