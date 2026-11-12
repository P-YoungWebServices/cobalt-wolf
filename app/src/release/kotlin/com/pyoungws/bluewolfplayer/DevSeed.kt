package com.pyoungws.bluewolfplayer

import android.app.Application
import org.koin.core.Koin

/** Release builds never provision dev credentials. */
object DevSeed {
    fun maybeStart(app: Application, koin: Koin) = Unit
}
