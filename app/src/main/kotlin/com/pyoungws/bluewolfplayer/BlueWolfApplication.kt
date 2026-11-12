package com.pyoungws.bluewolfplayer

import android.app.Application
import com.pyoungws.bluewolfplayer.notifications.RequestNotifier
import com.pyoungws.bluewolfplayer.shared.di.androidPlatformModule
import com.pyoungws.bluewolfplayer.shared.di.sharedModule
import com.pyoungws.bluewolfplayer.shared.seerr.RequestWatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BlueWolfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@BlueWolfApplication)
            modules(
                androidPlatformModule(clientName = getString(R.string.app_name), clientVersion = BuildConfig.VERSION_NAME),
                sharedModule,
            )
        }.koin
        RequestNotifier(this, koin.get<RequestWatcher>(), koin.get<CoroutineScope>()).start()
        DevSeed.maybeStart(this, koin)
    }
}
