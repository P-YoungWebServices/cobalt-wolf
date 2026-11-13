package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.auth.SignInError
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import com.pyoungws.cobaltwolf.shared.seerr.DiscoverFeed
import com.pyoungws.cobaltwolf.shared.seerr.SeerrAuthState
import com.pyoungws.cobaltwolf.shared.seerr.SeerrRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSettingsRepository
import com.pyoungws.cobaltwolf.shared.seerr.SeerrSignInResult
import com.pyoungws.cobaltwolf.shared.seerr.SeerrTitle
import com.pyoungws.cobaltwolf.shared.seerr.normalizeSeerrUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class DiscoverPhase {
    /** Session state still being determined. */
    Loading,
    /** No Seerr server configured yet: ask for the URL. */
    Setup,
    /** Server configured, no Seerr session: ask for the Jellyfin password. */
    SignIn,
    /** Signed in: show the feed and search. */
    Ready,
}

enum class SeerrUrlError { Invalid, Unreachable }

data class DiscoverUiState(
    val phase: DiscoverPhase = DiscoverPhase.Loading,
    val serverUrl: String = "",
    val urlError: SeerrUrlError? = null,
    val isConnecting: Boolean = false,
    /** Jellyfin username shown next to the password prompt. */
    val username: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val signInError: SignInError? = null,
    /** A stored session existed but could not be validated (e.g. server unreachable). */
    val restoreError: LoadError? = null,
    val feed: DiscoverFeed? = null,
    val feedError: LoadError? = null,
    val query: String = "",
    val searchResults: List<SeerrTitle> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: LoadError? = null,
) {
    val isSearchingActive: Boolean get() = query.isNotBlank()
    val showNoResults: Boolean get() = isSearchingActive && !isSearching && searchError == null && searchResults.isEmpty()
}

/** Discover and search requestable titles, plus the Seerr server setup and Jellyfin sign-in. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DiscoverViewModel(
    private val repository: SeerrRepository,
    private val settings: SeerrSettingsRepository,
    debounceTime: Duration = 300.milliseconds,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState(serverUrl = settings.serverUrl.orEmpty()))
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val queries = MutableStateFlow("")
    private var feedRequested = false
    private var usernameRequested = false

    init {
        viewModelScope.launch {
            repository.authState.collect { state -> onAuthState(state) }
        }
        queries
            .debounce(debounceTime)
            .map { it.trim() }
            .distinctUntilChanged()
            .onEach { term ->
                _uiState.update {
                    it.copy(
                        isSearching = term.isNotEmpty(),
                        searchError = null,
                        searchResults = if (term.isEmpty()) emptyList() else it.searchResults,
                    )
                }
            }
            .mapLatest { term -> if (term.isEmpty()) null else repository.search(term) }
            .onEach { result ->
                when (result) {
                    null -> Unit
                    is Outcome.Success -> _uiState.update { it.copy(searchResults = result.value, isSearching = false) }
                    is Outcome.Failure -> _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, searchError = result.error) }
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun onAuthState(state: SeerrAuthState) {
        when (state) {
            SeerrAuthState.Checking -> _uiState.update { it.copy(phase = DiscoverPhase.Loading) }
            SeerrAuthState.NotConfigured -> {
                feedRequested = false
                usernameRequested = false
                _uiState.update { it.copy(phase = DiscoverPhase.Setup, feed = null) }
            }
            is SeerrAuthState.SignedOut -> {
                // A later sign-in (possibly a different user) must reload the feed and the name.
                feedRequested = false
                usernameRequested = false
                _uiState.update { it.copy(phase = DiscoverPhase.SignIn, isSigningIn = false, restoreError = state.error) }
                if (!usernameRequested) {
                    usernameRequested = true
                    val name = repository.jellyfinUsername()
                    if (name != null) _uiState.update { it.copy(username = name) }
                }
            }
            is SeerrAuthState.SignedIn -> {
                _uiState.update { it.copy(phase = DiscoverPhase.Ready, isSigningIn = false, signInError = null, password = "", restoreError = null) }
                if (!feedRequested) {
                    feedRequested = true
                    loadFeed()
                }
            }
        }
    }

    fun onServerUrlChange(value: String) = _uiState.update { it.copy(serverUrl = value, urlError = null) }

    /**
     * Saves the server URL and re-checks the session. When the input has no scheme the repository
     * probes HTTPS then HTTP; an explicit `http(s)://` is saved as-is.
     */
    fun connect() {
        if (_uiState.value.isConnecting) return
        val input = _uiState.value.serverUrl
        val explicit = normalizeSeerrUrl(input)
        if ("://" in input.trim() && explicit == null) {
            _uiState.update { it.copy(urlError = SeerrUrlError.Invalid) }
            return
        }
        _uiState.update { it.copy(isConnecting = true, urlError = null) }
        viewModelScope.launch {
            try {
                val url = repository.resolveUrl(input)
                if (url == null) {
                    val invalid = normalizeSeerrUrl("https://" + input.trim().trimEnd('/')) == null
                    _uiState.update { it.copy(urlError = if (invalid) SeerrUrlError.Invalid else SeerrUrlError.Unreachable) }
                    return@launch
                }
                settings.update { it.copy(serverUrl = url) }
                repository.restore()
            } finally {
                // Whatever happened, never leave the button permanently disabled.
                _uiState.update { it.copy(isConnecting = false) }
            }
        }
    }

    /** Back to the URL form, e.g. when the configured server turned out to be the wrong one. */
    fun editServer() = _uiState.update { it.copy(phase = DiscoverPhase.Setup) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, signInError = null) }

    fun signIn() {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
        viewModelScope.launch {
            when (val result = repository.signInWithJellyfin(_uiState.value.password)) {
                is SeerrSignInResult.Success -> Unit // authState collector moves to Ready
                is SeerrSignInResult.Failure -> _uiState.update { it.copy(isSigningIn = false, signInError = result.error) }
            }
        }
    }

    /** Retries whatever failed: session validation before Ready, the feed afterwards. */
    fun retry() {
        if (_uiState.value.phase == DiscoverPhase.Ready) loadFeed()
        else viewModelScope.launch { repository.restore() }
    }

    fun refresh() = loadFeed()

    private fun loadFeed() {
        viewModelScope.launch {
            when (val result = repository.discover()) {
                is Outcome.Success -> _uiState.update { it.copy(feed = result.value, feedError = null) }
                is Outcome.Failure -> _uiState.update { it.copy(feedError = result.error) }
            }
        }
    }

    fun onQueryChange(query: String) {
        queries.value = query
        _uiState.update { it.copy(query = query) }
    }
}
