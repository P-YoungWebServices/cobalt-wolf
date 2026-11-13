package com.pyoungws.cobaltwolf

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"

/** True on Android TV, Google TV and Fire TV devices (leanback, no touch-first layout). */
fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(FIRE_TV_FEATURE)
}

fun Context.isFireTv(): Boolean = packageManager.hasSystemFeature(FIRE_TV_FEATURE)

/**
 * Android TV home screen channels exist on devices running the Google TV provider (Android 8+).
 * Fire TV has no TV provider and other leanback devices may lack it too, so publishing is skipped.
 */
fun Context.supportsHomeScreenChannels(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isTelevision() && !isFireTv()
