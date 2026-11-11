package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayGroup
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayMembership
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayNotice
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayRepository
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

    fun join(group: SyncPlayGroup) = act { repository.join(group) }

    fun createAndJoin(name: String) = act { repository.createAndJoin(name) }

    fun leave() = act { repository.leave() }

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
