package com.pyoungws.cobaltwolf.shared.di

import com.pyoungws.cobaltwolf.shared.arrs.ArrSettingsStore
import com.pyoungws.cobaltwolf.shared.arrs.SharedPreferencesArrSettingsStore
import com.pyoungws.cobaltwolf.shared.auth.KeystoreServerProfilesStore
import com.pyoungws.cobaltwolf.shared.auth.KeystoreSessionStore
import com.pyoungws.cobaltwolf.shared.auth.ServerProfilesStore
import com.pyoungws.cobaltwolf.shared.auth.SessionStore
import com.pyoungws.cobaltwolf.shared.playback.AndroidCapabilityDetector
import com.pyoungws.cobaltwolf.shared.playback.CapabilityDetector
import com.pyoungws.cobaltwolf.shared.playback.PlaybackSettingsStore
import com.pyoungws.cobaltwolf.shared.playback.SharedPreferencesPlaybackSettingsStore
import com.pyoungws.cobaltwolf.shared.qbittorrent.KeystoreQBittorrentSessionStore
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentSessionStore
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentSettingsStore
import com.pyoungws.cobaltwolf.shared.qbittorrent.SharedPreferencesQBittorrentSettingsStore
import com.pyoungws.cobaltwolf.shared.seerr.KeystoreSeerrSessionStore
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSessionStore
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsStore
import com.pyoungws.cobaltwolf.shared.seerr.SharedPreferencesSeerrSettingsStore
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android bindings for [sharedModule]. Device info comes from the SDK's Android defaults. */
fun androidPlatformModule(clientName: String, clientVersion: String): Module {
    // The SDK logs via kotlin-logging, which defaults to SLF4J (not on Android's classpath) and
    // crashes on first use. Route it to android.util.Log; must be set before any logger exists.
    System.setProperty(KOTLIN_LOGGING_ANDROID_NATIVE, "true")
    return module {
        single<Jellyfin> {
            createJellyfin {
                context = androidContext()
                clientInfo = ClientInfo(name = clientName, version = clientVersion)
            }
        }
        single<SessionStore> { KeystoreSessionStore(androidContext()) }
        single<ServerProfilesStore> { KeystoreServerProfilesStore(androidContext()) }
        single<CapabilityDetector> { AndroidCapabilityDetector(androidContext()) }
        single<PlaybackSettingsStore> { SharedPreferencesPlaybackSettingsStore(androidContext()) }
        single<SeerrSettingsStore> { SharedPreferencesSeerrSettingsStore(androidContext()) }
        single<SeerrSessionStore> { KeystoreSeerrSessionStore(androidContext()) }
        single<ArrSettingsStore> { SharedPreferencesArrSettingsStore(androidContext()) }
        single<QBittorrentSettingsStore> { SharedPreferencesQBittorrentSettingsStore(androidContext()) }
        single<QBittorrentSessionStore> { KeystoreQBittorrentSessionStore(androidContext()) }
    }
}

private const val KOTLIN_LOGGING_ANDROID_NATIVE = "kotlin-logging-to-android-native"
