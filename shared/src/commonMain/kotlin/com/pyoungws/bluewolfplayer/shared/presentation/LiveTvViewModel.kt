package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.library.LibraryRepository
import com.pyoungws.bluewolfplayer.shared.model.LiveTimer
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.MediaItem
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LiveTvUiState {
    data object Loading : LiveTvUiState
    data class Error(val error: LoadError) : LiveTvUiState
    data class Loaded(
        val channels: List<MediaItem> = emptyList(),
        /** Channel id -> its guide programs in the loaded window, airtime order. */
        val guide: Map<String, List<MediaItem>> = emptyMap(),
        val guideStart: java.time.LocalDateTime? = null,
        val guideEnd: java.time.LocalDateTime? = null,
        val recordings: List<MediaItem> = emptyList(),
        val timers: List<LiveTimer> = emptyList(),
        val seriesTimers: List<LiveTimer> = emptyList(),
    ) : LiveTvUiState
}

/** Live TV surface: channel lineup, program guide, recordings and recording schedules. */
class LiveTvViewModel(private val repository: LibraryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<LiveTvUiState>(LiveTvUiState.Loading)
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = LiveTvUiState.Loading
        viewModelScope.launch {
            val channels = when (val result = repository.liveTvChannels()) {
                is Outcome.Success -> result.value
                is Outcome.Failure -> {
                    _uiState.value = LiveTvUiState.Error(result.error)
                    return@launch
                }
            }
            _uiState.value = LiveTvUiState.Loaded(channels = channels)
            // Supporting sections are best-effort so a partial server (no guide data) still works.
            refreshGuide()
            _uiState.update { state ->
                (state as? LiveTvUiState.Loaded)?.copy(
                    recordings = (repository.liveTvRecordings() as? Outcome.Success)?.value.orEmpty(),
                    timers = (repository.liveTvTimers() as? Outcome.Success)?.value.orEmpty(),
                    seriesTimers = (repository.liveTvSeriesTimers() as? Outcome.Success)?.value.orEmpty(),
                ) ?: state
            }
        }
    }

    /** Loads the program grid for the channels we know about: now through +12h. */
    fun refreshGuide() {
        val channels = (_uiState.value as? LiveTvUiState.Loaded)?.channels.orEmpty()
        if (channels.isEmpty()) return
        viewModelScope.launch {
            val start = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2)
            val end = start.plusHours(14)
            val programs = (repository.liveTvPrograms(channels.map { it.id }, start, end) as? Outcome.Success)?.value.orEmpty()
            _uiState.update { state ->
                (state as? LiveTvUiState.Loaded)?.copy(
                    guide = programs.groupBy { it.programInfo?.channelId.orEmpty() },
                    guideStart = start,
                    guideEnd = end,
                ) ?: state
            }
        }
    }

    fun recordProgram(program: MediaItem) = act { repository.recordProgram(program.id) }
    fun recordSeries(program: MediaItem) = act { repository.recordSeries(program.id) }
    fun cancelTimer(timer: LiveTimer) = act { repository.cancelTimer(timer.id) }
    fun cancelSeriesTimer(timer: LiveTimer) = act { repository.cancelSeriesTimer(timer.id) }
    fun deleteRecording(recording: MediaItem) = act { repository.deleteRecording(recording.id) }

    private fun act(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch {
            block()
            // Timers/recordings change server-side; refresh the bookkeeping sections.
            _uiState.update { state ->
                (state as? LiveTvUiState.Loaded)?.copy(
                    recordings = (repository.liveTvRecordings() as? Outcome.Success)?.value.orEmpty(),
                    timers = (repository.liveTvTimers() as? Outcome.Success)?.value.orEmpty(),
                    seriesTimers = (repository.liveTvSeriesTimers() as? Outcome.Success)?.value.orEmpty(),
                ) ?: state
            }
        }
    }
}
