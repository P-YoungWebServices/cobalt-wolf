package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.JellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.SdkJellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.library.JellyfinLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.library.SdkJellyfinLibraryDataSource
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.playback.JellyfinPlaybackDataSource
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsRepository
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.presentation.PlaybackSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.playback.SdkJellyfinPlaybackDataSource
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MyRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SearchViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrDetailViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.KtorSeerrDataSource
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrDataSource
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRepository
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.seerr.seerrHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * Shared bindings. The platform must also provide an [org.jellyfin.sdk.Jellyfin] instance, a
 * [com.pyoungws.bluewolfplayer.shared.auth.SessionStore], a
 * [com.pyoungws.bluewolfplayer.shared.playback.CapabilityDetector], a
 * [com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsStore], a
 * [com.pyoungws.bluewolfplayer.shared.seerr.SeerrSettingsStore] and a
 * [com.pyoungws.bluewolfplayer.shared.seerr.SeerrSessionStore].
 */
val sharedModule = module {
    single<ServerDiscovery> { SdkServerDiscovery(get()) }
    single<JellyfinAuthDataSource> { SdkJellyfinAuthDataSource(get()) }
    single { ServerRepository(get()) }
    single { AuthRepository(get(), get()) }

    single { SessionGuard(get()) }
    single<JellyfinLibraryDataSource> { SdkJellyfinLibraryDataSource(get()) }
    single<JellyfinPlaybackDataSource> { SdkJellyfinPlaybackDataSource(get()) }
    single { LibraryRepository(get(), get()) }
    single { PlaybackSettingsRepository(get()) }
    single { PlaybackRepository(get(), get(), get(), get(), get()) }
    // Outlives screens so the final "playback stopped" report is not cancelled when the player closes.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { seerrHttpClient(get()) }
    single<SeerrDataSource> { KtorSeerrDataSource(get()) }
    single { SeerrSettingsRepository(get()) }
    single { SeerrRepository(get(), get(), get(), get(), get()) }

    viewModelOf(::AppViewModel)
    viewModelOf(::LibraryHomeViewModel)
    viewModel { SearchViewModel(get()) }
    viewModel { (library: Library) -> LibraryViewModel(library, get()) }
    viewModel { (itemId: String) -> ItemDetailViewModel(itemId, get()) }
    viewModel { (target: PlaybackTarget) -> PlayerViewModel(target, get(), get()) }
    viewModelOf(::PlaybackSettingsViewModel)
    viewModelOf(::ServerConnectViewModel)
    viewModelOf(::HomeViewModel)
    viewModel { (serverAddress: String) -> SignInViewModel(serverAddress, get()) }
    viewModel { DiscoverViewModel(get(), get()) }
    viewModelOf(::MyRequestsViewModel)
    viewModel { (type: SeerrMediaType, tmdbId: Int) -> SeerrDetailViewModel(type, tmdbId, get()) }
}
