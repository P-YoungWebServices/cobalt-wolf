package com.pyoungws.bluewolfplayer.shared.di

import com.pyoungws.bluewolfplayer.shared.auth.KeystoreSessionStore
import com.pyoungws.bluewolfplayer.shared.auth.SessionStore
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/** Android bindings for [sharedModule]. Device info comes from the SDK's Android defaults. */
fun androidPlatformModule(clientName: String, clientVersion: String): Module = module {
    single<Jellyfin> {
        createJellyfin {
            context = androidContext()
            clientInfo = ClientInfo(name = clientName, version = clientVersion)
        }
    }
    single<SessionStore> { KeystoreSessionStore(androidContext()) }
}
