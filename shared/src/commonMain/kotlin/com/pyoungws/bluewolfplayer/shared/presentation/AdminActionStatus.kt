package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.model.LoadError

/**
 * Status of a mutating admin action (create, edit, delete, start, stop, rename).
 * [key] identifies the action so the right row or dialog can show progress; the screen
 * consumes [Succeeded]/[Failed] and clears the status.
 */
sealed interface AdminActionStatus {
    data class Running(val key: String) : AdminActionStatus
    data class Succeeded(val key: String) : AdminActionStatus
    data class Failed(val key: String, val error: LoadError) : AdminActionStatus
}
