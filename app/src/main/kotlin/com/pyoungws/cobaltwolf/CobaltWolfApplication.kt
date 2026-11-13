package com.pyoungws.cobaltwolf

import android.app.Application
import com.pyoungws.cobaltwolf.notifications.RequestNotifier
import com.pyoungws.cobaltwolf.shared.di.androidPlatformModule
import com.pyoungws.cobaltwolf.shared.di.sharedModule
import com.pyoungws.cobaltwolf.shared.seerr.RequestWatcher
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CobaltWolfApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@CobaltWolfApplication)
            modules(
                androidPlatformModule(clientName = getString(R.string.app_name), clientVersion = BuildConfig.VERSION_NAME),
                sharedModule,
                org.koin.dsl.module { single { com.pyoungws.cobaltwolf.ui.downloads.OfflineDownloads(androidContext()) } },
            )
        }.koin
        RequestNotifier(this, koin.get<RequestWatcher>(), koin.get<CoroutineScope>()).start()
        DevSeed.maybeStart(this, koin)
    }
}
