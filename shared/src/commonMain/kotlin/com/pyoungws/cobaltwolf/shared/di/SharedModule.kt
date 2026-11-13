package com.pyoungws.cobaltwolf.shared.di

import com.pyoungws.cobaltwolf.shared.admin.AdminRepository
import com.pyoungws.cobaltwolf.shared.arrs.ArrDataSource
import com.pyoungws.cobaltwolf.shared.arrs.ArrMediaDataSource
import com.pyoungws.cobaltwolf.shared.arrs.ArrMediaRepository
import com.pyoungws.cobaltwolf.shared.arrs.ArrRepository
import com.pyoungws.cobaltwolf.shared.arrs.ArrService
import com.pyoungws.cobaltwolf.shared.arrs.ArrSettingsRepository
import com.pyoungws.cobaltwolf.shared.arrs.KtorArrDataSource
import com.pyoungws.cobaltwolf.shared.arrs.KtorArrMediaDataSource
import com.pyoungws.cobaltwolf.shared.arrs.arrHttpClient
import com.pyoungws.cobaltwolf.shared.admin.JellyfinAdminDataSource
import com.pyoungws.cobaltwolf.shared.admin.SdkJellyfinAdminDataSource
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.JellyfinAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.SdkJellyfinAuthDataSource
import com.pyoungws.cobaltwolf.shared.bazarr.BazarrDataSource
import com.pyoungws.cobaltwolf.shared.bazarr.BazarrRepository
import com.pyoungws.cobaltwolf.shared.bazarr.KtorBazarrDataSource
import com.pyoungws.cobaltwolf.shared.library.JellyfinLibraryDataSource
import com.pyoungws.cobaltwolf.shared.library.LibraryRepository
import com.pyoungws.cobaltwolf.shared.library.SdkJellyfinLibraryDataSource
import com.pyoungws.cobaltwolf.shared.library.SessionGuard
import com.pyoungws.cobaltwolf.shared.model.Library
import com.pyoungws.cobaltwolf.shared.playback.JellyfinPlaybackDataSource
import com.pyoungws.cobaltwolf.shared.playback.PlaybackRepository
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsRepository
import com.pyoungws.cobaltwolf.shared.playback.PlaybackTarget
import com.pyoungws.cobaltwolf.shared.presentation.PlaybackSettingsViewModel
import com.pyoungws.cobaltwolf.shared.playback.SdkJellyfinPlaybackDataSource
import com.pyoungws.cobaltwolf.shared.playback.playbackHttpClient
import com.pyoungws.cobaltwolf.shared.prowlarr.KtorProwlarrDataSource
import com.pyoungws.cobaltwolf.shared.prowlarr.ProwlarrDataSource
import com.pyoungws.cobaltwolf.shared.prowlarr.ProwlarrRepository
import com.pyoungws.cobaltwolf.shared.qbittorrent.KtorQBittorrentDataSource
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentDataSource
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentRepository
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentSettingsRepository
import com.pyoungws.cobaltwolf.shared.qbittorrent.qbittorrentHttpClient
import com.pyoungws.cobaltwolf.shared.presentation.AdminDashboardViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminRequestsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminDevicesViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminLibrariesViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminActivityLogViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminApiKeysViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminLogsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminPluginsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminServerSettingsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminSystemInfoViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminTasksViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AdminUsersViewModel
import com.pyoungws.cobaltwolf.shared.presentation.AppViewModel
import com.pyoungws.cobaltwolf.shared.presentation.ArrSettingsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.CalendarViewModel
import com.pyoungws.cobaltwolf.shared.presentation.DiscoverViewModel
import com.pyoungws.cobaltwolf.shared.presentation.DownloadsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.ItemDetailViewModel
import com.pyoungws.cobaltwolf.shared.presentation.LibraryHomeViewModel
import com.pyoungws.cobaltwolf.shared.presentation.ItemBrowseViewModel
import com.pyoungws.cobaltwolf.shared.presentation.LibraryViewModel
import com.pyoungws.cobaltwolf.shared.presentation.LiveTvViewModel
import com.pyoungws.cobaltwolf.shared.presentation.BazarrViewModel
import com.pyoungws.cobaltwolf.shared.presentation.MediaDetailViewModel
import com.pyoungws.cobaltwolf.shared.presentation.MediaManagerViewModel
import com.pyoungws.cobaltwolf.shared.presentation.MyRequestsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.PlayerViewModel
import com.pyoungws.cobaltwolf.shared.presentation.ProwlarrViewModel
import com.pyoungws.cobaltwolf.shared.presentation.QBittorrentSettingsViewModel
import com.pyoungws.cobaltwolf.shared.presentation.SearchViewModel
import com.pyoungws.cobaltwolf.shared.presentation.SeerrDetailViewModel
import com.pyoungws.cobaltwolf.shared.seerr.KtorSeerrDataSource
import com.pyoungws.cobaltwolf.shared.seerr.RequestWatcher
import com.pyoungws.cobaltwolf.shared.seerr.SeerrDataSource
import com.pyoungws.cobaltwolf.shared.seerr.SeerrMediaType
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsRepository
import com.pyoungws.cobaltwolf.shared.seerr.seerrHttpClient
import com.pyoungws.cobaltwolf.shared.syncplay.SdkSyncPlayDataSource
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayDataSource
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayRepository
import com.pyoungws.cobaltwolf.shared.presentation.ServersViewModel
import com.pyoungws.cobaltwolf.shared.presentation.SyncPlayViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.pyoungws.cobaltwolf.shared.presentation.HomeViewModel
import com.pyoungws.cobaltwolf.shared.presentation.ServerConnectViewModel
import com.pyoungws.cobaltwolf.shared.presentation.SignInViewModel
import com.pyoungws.cobaltwolf.shared.server.SdkServerDiscovery
import com.pyoungws.cobaltwolf.shared.server.ServerDiscovery
import com.pyoungws.cobaltwolf.shared.server.ServerRepository
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Shared bindings. The platform must also provide an [org.jellyfin.sdk.Jellyfin] instance, a
 * [com.pyoungws.cobaltwolf.shared.auth.SessionStore], a
 * [com.pyoungws.cobaltwolf.shared.playback.CapabilityDetector], a
 * [com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsStore], a
 * [com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsStore] and a
 * [com.pyoungws.cobaltwolf.shared.seerr.SeerrSessionStore].
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
    single { com.pyoungws.cobaltwolf.shared.playback.PlayQueueRepository() }
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
    viewModel { (query: com.pyoungws.cobaltwolf.shared.library.ItemQuery) -> ItemBrowseViewModel(query, get()) }
    viewModel { (itemId: String) -> ItemDetailViewModel(itemId, get()) }
    viewModel { LiveTvViewModel(get()) }
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
    viewModelOf(::AdminActivityLogViewModel)
    viewModelOf(::AdminApiKeysViewModel)
    viewModelOf(::AdminSystemInfoViewModel)
    viewModelOf(::AdminPluginsViewModel)
    viewModelOf(::AdminServerSettingsViewModel)
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
