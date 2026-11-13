package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrDataSource
import com.pyoungws.bluewolfplayer.shared.arrs.ArrMediaDataSource
import com.pyoungws.bluewolfplayer.shared.arrs.ArrMediaRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrRepository
import com.pyoungws.bluewolfplayer.shared.arrs.ArrService
import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.arrs.KtorArrDataSource
import com.pyoungws.bluewolfplayer.shared.arrs.KtorArrMediaDataSource
import com.pyoungws.bluewolfplayer.shared.arrs.arrHttpClient
import com.pyoungws.bluewolfplayer.shared.admin.JellyfinAdminDataSource
import com.pyoungws.bluewolfplayer.shared.admin.SdkJellyfinAdminDataSource
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.JellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.SdkJellyfinAuthDataSource
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrDataSource
import com.pyoungws.bluewolfplayer.shared.bazarr.BazarrRepository
import com.pyoungws.bluewolfplayer.shared.bazarr.KtorBazarrDataSource
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
import com.pyoungws.bluewolfplayer.shared.playback.playbackHttpClient
import com.pyoungws.bluewolfplayer.shared.prowlarr.KtorProwlarrDataSource
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrDataSource
import com.pyoungws.bluewolfplayer.shared.prowlarr.ProwlarrRepository
import com.pyoungws.bluewolfplayer.shared.qbittorrent.KtorQBittorrentDataSource
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentDataSource
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentRepository
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentSettingsRepository
import com.pyoungws.bluewolfplayer.shared.qbittorrent.qbittorrentHttpClient
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDevicesViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminLibrariesViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminLogsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminSystemInfoViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminTasksViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminUsersViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ArrSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ItemBrowseViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.BazarrViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MediaDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MediaManagerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MyRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ProwlarrViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SearchViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrDetailViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.KtorSeerrDataSource
import com.pyoungws.bluewolfplayer.shared.seerr.RequestWatcher
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrDataSource
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrRepository
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrSettingsRepository
import com.pyoungws.bluewolfplayer.shared.seerr.seerrHttpClient
import com.pyoungws.bluewolfplayer.shared.syncplay.SdkSyncPlayDataSource
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayDataSource
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayRepository
import com.pyoungws.bluewolfplayer.shared.presentation.ServersViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayViewModel
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
    single { AuthRepository(get(), get(), get()) }

    single { SessionGuard(get()) }
    single<JellyfinLibraryDataSource> { SdkJellyfinLibraryDataSource(get()) }
    single<JellyfinPlaybackDataSource> { SdkJellyfinPlaybackDataSource(get(), playbackHttpClient()) }
    single<JellyfinAdminDataSource> { SdkJellyfinAdminDataSource(get()) }
    single { LibraryRepository(get(), get(), get()) }
    single { AdminRepository(get(), get()) }
    single { PlaybackSettingsRepository(get()) }
    single { PlaybackRepository(get(), get(), get(), get(), get()) }
    single { com.pyoungws.bluewolfplayer.shared.playback.PlayQueueRepository() }
    // Outlives screens so the final "playback stopped" report is not cancelled when the player closes.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<SeerrDataSource> { KtorSeerrDataSource(seerrHttpClient(get())) }
    single { SeerrSettingsRepository(get()) }
    single { SeerrRepository(get(), get(), get(), get(), get()) }
    single { RequestWatcher(get(), get()) }

    single<SyncPlayDataSource> { SdkSyncPlayDataSource(get()) }
    single { SyncPlayRepository(get(), get(), get(), get()) }

    single<ArrDataSource> { KtorArrDataSource(arrHttpClient()) }
    single<ArrMediaDataSource> { KtorArrMediaDataSource(arrHttpClient()) }
    single { ArrSettingsRepository(get()) }
    single { ArrRepository(get(), get()) }
    single { ArrMediaRepository(get(), get()) }
    single<BazarrDataSource> { KtorBazarrDataSource(arrHttpClient()) }
    single { BazarrRepository(get(), get()) }
    single<ProwlarrDataSource> { KtorProwlarrDataSource(arrHttpClient()) }
    single { ProwlarrRepository(get(), get()) }

    single<QBittorrentDataSource> { KtorQBittorrentDataSource(qbittorrentHttpClient(get())) }
    single { QBittorrentSettingsRepository(get()) }
    single { QBittorrentRepository(get(), get()) }

    viewModelOf(::AppViewModel)
    viewModelOf(::LibraryHomeViewModel)
    viewModel { SearchViewModel(get(), get()) }
    viewModel { (library: Library) -> LibraryViewModel(library, get()) }
    viewModel { (query: com.pyoungws.bluewolfplayer.shared.library.ItemQuery) -> ItemBrowseViewModel(query, get()) }
    viewModel { (itemId: String) -> ItemDetailViewModel(itemId, get()) }
    viewModel { (target: PlaybackTarget) -> PlayerViewModel(target, get(), get(), get(), get()) }
    viewModelOf(::SyncPlayViewModel)
    viewModelOf(::ServersViewModel)
    viewModelOf(::PlaybackSettingsViewModel)
    viewModelOf(::ServerConnectViewModel)
    viewModelOf(::HomeViewModel)
    viewModel { (serverAddress: String) -> SignInViewModel(serverAddress, get()) }
    viewModel { DiscoverViewModel(get(), get()) }
    viewModelOf(::MyRequestsViewModel)
    viewModelOf(::AdminDashboardViewModel)
    viewModelOf(::AdminUsersViewModel)
    viewModelOf(::AdminDevicesViewModel)
    viewModelOf(::AdminLibrariesViewModel)
    viewModelOf(::AdminTasksViewModel)
    viewModelOf(::AdminLogsViewModel)
    viewModelOf(::AdminSystemInfoViewModel)
    viewModelOf(::AdminRequestsViewModel)
    viewModel { (type: SeerrMediaType, tmdbId: Int) -> SeerrDetailViewModel(type, tmdbId, get()) }
    viewModelOf(::CalendarViewModel)
    viewModelOf(::ArrSettingsViewModel)
    viewModelOf(::MediaManagerViewModel)
    viewModel { (service: ArrService, itemId: Int) -> MediaDetailViewModel(service, itemId, get()) }
    viewModelOf(::DownloadsViewModel)
    viewModelOf(::QBittorrentSettingsViewModel)
    viewModelOf(::BazarrViewModel)
    viewModelOf(::ProwlarrViewModel)
}
