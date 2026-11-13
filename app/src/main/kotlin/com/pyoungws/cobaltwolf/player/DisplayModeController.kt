package com.pyoungws.cobaltwolf.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import com.pyoungws.cobaltwolf.shared.playback.DisplayMode
import com.pyoungws.cobaltwolf.shared.playback.PlaybackRequest
import com.pyoungws.cobaltwolf.shared.playback.allowsDisplayModeSwitch
import com.pyoungws.cobaltwolf.shared.playback.chooseDisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val MODE_SWITCH_TIMEOUT_MS = 5_000L

/** Time a TV needs to re-sync HDMI after a mode change before picture and sound are stable. */
private const val MODE_SETTLE_MS = 1_500L

/**
 * Matches the display to the content on the player window: the refresh rate (a display mode
 * switch, where the settings and device allow one) and the window's HDR colour mode. The system
 * switches the HDMI output to the stream's HDR format itself when an HDR surface is shown.
 */
internal class DisplayModeController(private val activity: Activity) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    private fun display(): Display? = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

    /**
     * Requests the display mode that fits [frameRate] and waits until the display has switched and
     * settled. Returns the new mode, or null when the current one is kept.
     */
    suspend fun matchFrameRate(frameRate: Float, request: PlaybackRequest): DisplayMode? {
        if (!request.settings.allowsDisplayModeSwitch(request.capabilities.isTv)) return null
        val display = display() ?: return null
        val modes = display.supportedModes.map { DisplayMode(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val target = chooseDisplayMode(modes, display.mode.modeId, frameRate) ?: return null
        Log.i(TAG, "Switching display mode for $frameRate fps: ${display.mode.refreshRate} Hz -> ${target.refreshRate} Hz")
        val switched = withTimeoutOrNull(MODE_SWITCH_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit
                    override fun onDisplayRemoved(displayId: Int) = Unit
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == Display.DEFAULT_DISPLAY && display()?.mode?.modeId == target.id && continuation.isActive) {
                            displayManager?.unregisterDisplayListener(this)
                            continuation.resume(true)
                        }
                    }
                }
                displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                continuation.invokeOnCancellation { displayManager?.unregisterDisplayListener(listener) }
                activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = target.id }
            }
        }
        if (switched == null) {
            Log.w(TAG, "The display did not switch to ${target.refreshRate} Hz")
            return null
        }
        delay(MODE_SETTLE_MS)
        return target
    }

    /** Tells the system whether the window shows HDR content, so HDR output is used only while it is needed. */
    fun setHdr(hdr: Boolean) {
        if (Build.VERSION.SDK_INT < 26) return
        val mode = if (hdr && activity.resources.configuration.isScreenHdr) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
        if (activity.window.colorMode != mode) activity.window.colorMode = mode
    }

    /** Hands the display back in its default mode. */
    fun reset() {
        setHdr(false)
        val attributes = activity.window.attributes
        if (attributes.preferredDisplayModeId != 0) activity.window.attributes = attributes.apply { preferredDisplayModeId = 0 }
    }

    private companion object {
        const val TAG = "CobaltWolfPlayback"
    }
}
