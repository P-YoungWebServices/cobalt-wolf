package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.arrs.ArrSettingsStore
import com.pyoungws.bluewolfplayer.shared.arrs.InMemoryArrSettingsStore
import com.pyoungws.bluewolfplayer.shared.auth.InMemoryServerProfilesStore
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.auth.ServerProfilesStore
import com.pyoungws.bluewolfplayer.shared.auth.SessionStore
import com.pyoungws.bluewolfplayer.shared.model.Library
import com.pyoungws.bluewolfplayer.shared.model.LibraryType
import com.pyoungws.bluewolfplayer.shared.playback.CapabilityDetector
import com.pyoungws.bluewolfplayer.shared.playback.InMemoryPlaybackSettingsStore
import com.pyoungws.bluewolfplayer.shared.qbittorrent.InMemoryQBittorrentSessionStore
import com.pyoungws.bluewolfplayer.shared.qbittorrent.InMemoryQBittorrentSettingsStore
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentSessionStore
import com.pyoungws.bluewolfplayer.shared.qbittorrent.QBittorrentSettingsStore
import com.pyoungws.bluewolfplayer.shared.playback.PHONE_CAPABILITIES
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsStore
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackTarget
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDashboardViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminDevicesViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminLibrariesViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminLogsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminSystemInfoViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminTasksViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AdminUsersViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.AppViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ArrSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.CalendarViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.DownloadsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.MyRequestsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.PlaybackSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.HomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ItemDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryHomeViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.LibraryViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.PlayerViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.QBittorrentSettingsViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SearchViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrDetailViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServerConnectViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.ServersViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SyncPlayViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SignInViewModel
import com.pyoungws.bluewolfplayer.shared.seerr.InMemorySeerrSessionStore
import com.pyoungws.bluewolfplayer.shared.seerr.InMemorySeerrSettingsStore
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrSessionStore
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Catches wiring mistakes (e.g. Koin trying to inject a constructor default) that fakes in unit tests cannot. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedModuleTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `every view model can be created from the shared module`() {
        val platform = module {
            single<Jellyfin> {
                createJellyfin {
                    clientInfo = ClientInfo(name = "tests", version = "0")
                    deviceInfo = DeviceInfo(id = "test-device", name = "JVM")
                }
            }
            single<SessionStore> { InMemorySessionStore() }
            single<ServerProfilesStore> { InMemoryServerProfilesStore() }
            single<CapabilityDetector> { CapabilityDetector { PHONE_CAPABILITIES } }
            single<PlaybackSettingsStore> { InMemoryPlaybackSettingsStore() }
            single<SeerrSettingsStore> { InMemorySeerrSettingsStore() }
            single<SeerrSessionStore> { InMemorySeerrSessionStore() }
            single<ArrSettingsStore> { InMemoryArrSettingsStore() }
            single<QBittorrentSettingsStore> { InMemoryQBittorrentSettingsStore() }
            single<QBittorrentSessionStore> { InMemoryQBittorrentSessionStore() }
        }
        val koin = koinApplication { modules(sharedModule, platform) }.koin
        val library = Library("l1", "Movies", LibraryType.Movies, null)

        assertNotNull(koin.get<AppViewModel>())
        assertNotNull(koin.get<ServerConnectViewModel>())
        assertNotNull(koin.get<HomeViewModel>())
        assertNotNull(koin.get<LibraryHomeViewModel>())
        assertNotNull(koin.get<SearchViewModel>())
        assertNotNull(koin.get<SignInViewModel> { parametersOf("https://jellyfin.example.com") })
        assertNotNull(koin.get<LibraryViewModel> { parametersOf(library) })
        assertNotNull(koin.get<ItemDetailViewModel> { parametersOf("item-1") })
        assertNotNull(koin.get<PlayerViewModel> { parametersOf(PlaybackTarget.Item("item-1")) })
        assertNotNull(koin.get<PlaybackSettingsViewModel>())
        assertNotNull(koin.get<DiscoverViewModel>())
        assertNotNull(koin.get<MyRequestsViewModel>())
        assertNotNull(koin.get<SeerrDetailViewModel> { parametersOf(SeerrMediaType.Movie, 1) })
        assertNotNull(koin.get<AdminDashboardViewModel>())
        assertNotNull(koin.get<AdminUsersViewModel>())
        assertNotNull(koin.get<AdminDevicesViewModel>())
        assertNotNull(koin.get<AdminLibrariesViewModel>())
        assertNotNull(koin.get<AdminTasksViewModel>())
        assertNotNull(koin.get<AdminLogsViewModel>())
        assertNotNull(koin.get<AdminSystemInfoViewModel>())
        assertNotNull(koin.get<AdminRequestsViewModel>())
        assertNotNull(koin.get<SyncPlayViewModel>())
        assertNotNull(koin.get<ServersViewModel>())
        assertNotNull(koin.get<CalendarViewModel>())
        assertNotNull(koin.get<ArrSettingsViewModel>())
        assertNotNull(koin.get<DownloadsViewModel>())
        assertNotNull(koin.get<QBittorrentSettingsViewModel>())
    }
}
