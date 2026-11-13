package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayGroup
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayMembership
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayNotice
import com.pyoungws.cobaltwolf.shared.syncplay.SyncPlayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SyncPlayUiState {
    data object Loading : SyncPlayUiState
    data class Ready(
        val groups: List<SyncPlayGroup>,
        /** Non-null while this device is in a group. */
        val membership: SyncPlayMembership?,
        /** A join, create or leave call is in flight. */
        val busy: Boolean = false,
        /** The last action failed; cleared on retry. */
        val actionError: LoadError? = null,
    ) : SyncPlayUiState
    data class Error(val error: LoadError) : SyncPlayUiState
}

/**
 * The "watch together" group browser: lists the server's SyncPlay groups, creates and joins them
 * and tracks the current membership. The player follows [SyncPlayRepository.playerCommands]
 * directly; this screen only needs the membership for display.
 */
class SyncPlayViewModel(
    private val repository: SyncPlayRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SyncPlayUiState>(SyncPlayUiState.Loading)
    val uiState: StateFlow<SyncPlayUiState> = _uiState.asStateFlow()

    /** Notices like "the group was closed"; the UI shows them as transient messages. */
    val notices: SharedFlow<SyncPlayNotice> = repository.notices

    init {
        refresh()
        viewModelScope.launch {
            repository.membership.collect { membership ->
                _uiState.update { state ->
                    // Only a loaded screen shows membership: a null emission must not wipe
                    // Loading or Error while the groups are still being fetched.
                    (state as? SyncPlayUiState.Ready)?.copy(membership = membership) ?: state
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = repository.listGroups()) {
                is Outcome.Success -> _uiState.update { state ->
                    (state as? SyncPlayUiState.Ready)?.copy(groups = result.value)
                        ?: SyncPlayUiState.Ready(groups = result.value, membership = repository.membership.value)
                }
                is Outcome.Failure -> _uiState.update { state ->
                    if (state is SyncPlayUiState.Ready) state.copy(actionError = result.error)
                    else SyncPlayUiState.Error(result.error)
                }
            }
        }
    }

    /**
     * Joins [group]; when [queueItemId] is given (the "watch together" entry on a media
     * detail page) that item becomes the group's queue so it starts for everyone.
     */
    fun join(group: SyncPlayGroup, queueItemId: String? = null) = act {
        repository.join(group).thenQueue(queueItemId)
    }

    fun createAndJoin(name: String, queueItemId: String? = null) = act {
        repository.createAndJoin(name).thenQueue(queueItemId)
    }

    /** Replaces the queue of the group this device is in with a single item. */
    fun queueItem(itemId: String) = act { repository.playInGroup(listOf(itemId)) }

    fun leave() = act { repository.leave() }

    private suspend fun Outcome<Unit>.thenQueue(itemId: String?): Outcome<Unit> =
        if (this is Outcome.Success && itemId != null) repository.playInGroup(listOf(itemId)) else this

    private fun act(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            _uiState.update { (it as? SyncPlayUiState.Ready)?.copy(busy = true, actionError = null) ?: it }
            val result = block()
            _uiState.update { state ->
                (state as? SyncPlayUiState.Ready)?.copy(
                    busy = false,
                    actionError = (result as? Outcome.Failure)?.error,
                ) ?: state
            }
        }
    }
}
