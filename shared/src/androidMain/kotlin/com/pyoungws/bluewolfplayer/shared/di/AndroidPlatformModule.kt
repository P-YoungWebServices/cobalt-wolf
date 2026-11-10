package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.auth.KeystoreSessionStore
import com.pyoungws.bluewolfplayer.shared.auth.SessionStore
import com.pyoungws.bluewolfplayer.shared.playback.AndroidCapabilityDetector
import com.pyoungws.bluewolfplayer.shared.playback.CapabilityDetector
import com.pyoungws.bluewolfplayer.shared.playback.PlaybackSettingsStore
import com.pyoungws.bluewolfplayer.shared.playback.SharedPreferencesPlaybackSettingsStore
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
        single<CapabilityDetector> { AndroidCapabilityDetector(androidContext()) }
        single<PlaybackSettingsStore> { SharedPreferencesPlaybackSettingsStore(androidContext()) }
    }
}

private const val KOTLIN_LOGGING_ANDROID_NATIVE = "kotlin-logging-to-android-native"
