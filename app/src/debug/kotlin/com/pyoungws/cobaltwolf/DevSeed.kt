package com.pyoungws.cobaltwolf

import android.app.Application
import android.content.Context
import android.util.Log
import com.pyoungws.cobaltwolf.shared.arrs.ArrService
import com.pyoungws.cobaltwolf.shared.arrs.ArrSettingsRepository
import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.SessionState
import com.pyoungws.cobaltwolf.shared.auth.SignInResult
import com.pyoungws.cobaltwolf.shared.qbittorrent.QBittorrentSettingsRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrAuthState
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSessionStore
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.Koin

private const val TAG = "DevSeed"
private const val PREFS = "dev_seed"
private const val KEY_SIGN_IN_DONE = "sign_in_done"

/**
 * Debug builds only: provisions a fresh install with the local dev credentials from
 * dev.credentials.properties (baked into BuildConfig by app/build.gradle.kts). Service
 * settings are refreshed on every start; the Jellyfin sign-in runs once per install so a
 * deliberate sign-out is never undone.
 */
object DevSeed {

    fun maybeStart(app: Application, koin: Koin) {
        if (BuildConfig.DEV_SERVER.isBlank()) return
        koin.get<CoroutineScope>().launch {
            seedServices(koin)
            seedSignIn(app, koin)
        }
    }

    private suspend fun seedServices(koin: Koin) {
        if (BuildConfig.DEV_SEERR_SERVER.isNotBlank()) {
            // The settings form normalizes user input via resolveUrl; do the same here so a
            // scheme-less "host:port" value becomes a usable URL.
            val url = if ("://" in BuildConfig.DEV_SEERR_SERVER) {
                BuildConfig.DEV_SEERR_SERVER
            } else {
                koin.get<SeerrRepository>().resolveUrl(BuildConfig.DEV_SEERR_SERVER)
            }
            if (url != null) {
                koin.get<SeerrSettingsRepository>().update { it.copy(serverUrl = url) }
            } else {
                Log.w(TAG, "Could not resolve the dev Seerr server")
            }
        }
        if (BuildConfig.DEV_RADARR_URL.isNotBlank()) {
            koin.get<ArrSettingsRepository>().update(ArrService.Radarr, BuildConfig.DEV_RADARR_URL, BuildConfig.DEV_RADARR_APIKEY)
        }
        if (BuildConfig.DEV_SONARR_URL.isNotBlank()) {
            koin.get<ArrSettingsRepository>().update(ArrService.Sonarr, BuildConfig.DEV_SONARR_URL, BuildConfig.DEV_SONARR_APIKEY)
        }
        if (BuildConfig.DEV_BAZARR_URL.isNotBlank()) {
            koin.get<ArrSettingsRepository>().update(ArrService.Bazarr, BuildConfig.DEV_BAZARR_URL, BuildConfig.DEV_BAZARR_APIKEY)
        }
        if (BuildConfig.DEV_PROWLARR_URL.isNotBlank()) {
            koin.get<ArrSettingsRepository>().update(ArrService.Prowlarr, BuildConfig.DEV_PROWLARR_URL, BuildConfig.DEV_PROWLARR_APIKEY)
        }
        if (BuildConfig.DEV_QBITTORRENT_URL.isNotBlank()) {
            koin.get<QBittorrentSettingsRepository>().update(
                BuildConfig.DEV_QBITTORRENT_URL,
                BuildConfig.DEV_QBITTORRENT_USERNAME,
                BuildConfig.DEV_QBITTORRENT_PASSWORD,
            )
        }
        Log.i(TAG, "Dev service settings seeded")
    }

    private suspend fun seedSignIn(app: Application, koin: Koin) {
        val auth = koin.get<AuthRepository>()
        val seerr = koin.get<SeerrRepository>()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_SIGN_IN_DONE, false) && BuildConfig.DEV_USERNAME.isNotBlank()) {
            auth.restoreSession()
            if (auth.sessionState.value is SessionState.SignedOut) {
                when (auth.signIn(BuildConfig.DEV_SERVER, BuildConfig.DEV_USERNAME, BuildConfig.DEV_PASSWORD)) {
                    is SignInResult.Success -> Log.i(TAG, "Dev sign-in succeeded")
                    is SignInResult.Failure -> {
                        Log.w(TAG, "Dev sign-in failed; leaving the normal sign-in screen")
                        return
                    }
                }
            }
            prefs.edit().putBoolean(KEY_SIGN_IN_DONE, true).apply()
        }

        // Seerr shares the Jellyfin account; sign in whenever there is a session but no
        // Seerr cookie yet so search/requests work on a fresh install. The settings update
        // above makes the repository reset and re-restore its session asynchronously, so
        // wait for that to settle first, then retry until a cookie actually survives.
        val jellyfin = auth.sessionState.first { it !is SessionState.Unknown }
        if (jellyfin is SessionState.SignedIn && BuildConfig.DEV_SEERR_SERVER.isNotBlank()) {
            kotlinx.coroutines.delay(600)
            var attempts = 0
            while (attempts < 4 && seerr.authState.first { it !is SeerrAuthState.Checking } is SeerrAuthState.SignedOut) {
                attempts++
                seerr.signInWithJellyfin(BuildConfig.DEV_PASSWORD)
                    .also { Log.i(TAG, "Dev Seerr sign-in: $it") }
                Log.i(TAG, "Dev Seerr cookie stored: ${koin.get<SeerrSessionStore>().load() != null}")
                // A restore() racing the sign-in can still overwrite the state; pause so a
                // late one lands before the next check rather than after the loop.
                kotlinx.coroutines.delay(800)
            }
        }
    }
}
