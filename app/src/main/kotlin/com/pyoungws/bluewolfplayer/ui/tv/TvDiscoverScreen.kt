package com.pyoungws.bluewolfplayer.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.OutlinedButton
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverPhase
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverUiState
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrUrlError
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

/** Seerr requests on TV: server setup, Jellyfin sign-in, the discover feed and search. */
@Composable
fun TvDiscoverScreen(
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        when (state.phase) {
            DiscoverPhase.Loading -> TvLoadingBox()
            DiscoverPhase.Setup -> TvSetupForm(state, viewModel)
            DiscoverPhase.SignIn -> TvSeerrSignInForm(state, viewModel)
            DiscoverPhase.Ready -> TvDiscoverContent(state, viewModel, onOpenTitle)
        }
    }
}

@Composable
private fun TvForm(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = TvContentPadding, vertical = 24.dp).widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) { content() }
}

@Composable
private fun TvSetupForm(state: DiscoverUiState, viewModel: DiscoverViewModel) {
    TvForm {
        Text(stringResource(R.string.seerr_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.seerr_setup_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            singleLine = true,
            isError = state.urlError != null,
            label = { androidx.compose.material3.Text(stringResource(R.string.seerr_url_label)) },
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.seerr_url_hint)) },
            supportingText = {
                state.urlError?.let {
                    androidx.compose.material3.Text(
                        stringResource(
                            when (it) {
                                SeerrUrlError.Invalid -> R.string.seerr_url_invalid
                                SeerrUrlError.Unreachable -> R.string.seerr_url_unreachable
                            },
                        ),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::connect, enabled = state.serverUrl.isNotBlank() && !state.isConnecting) {
            Text(stringResource(R.string.seerr_connect))
        }
        if (state.isConnecting) CircularProgressIndicator()
    }
}

@Composable
private fun TvSeerrSignInForm(state: DiscoverUiState, viewModel: DiscoverViewModel) {
    TvForm {
        Text(stringResource(R.string.seerr_signin_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.seerr_signin_subtitle, state.username.ifBlank { state.serverUrl }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.restoreError?.let { error ->
            Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.home_retry)) }
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            singleLine = true,
            label = { androidx.compose.material3.Text(stringResource(R.string.sign_in_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.signInError?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::signIn, enabled = !state.isSigningIn) {
            Text(stringResource(R.string.seerr_signin_button))
        }
        if (state.isSigningIn) CircularProgressIndicator()
        OutlinedButton(onClick = viewModel::editServer) { Text(stringResource(R.string.seerr_change_server)) }
    }
}

@Composable
private fun TvDiscoverContent(
    state: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { androidx.compose.material3.Text(stringResource(R.string.seerr_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = TvContentPadding, vertical = 8.dp),
        )
        if (state.isSearchingActive) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.searchError != null -> TvErrorBox(state.searchError!!, onRetry = null)
                    state.isSearching && state.searchResults.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(16.dp))
                    state.showNoResults ->
                        Text(stringResource(R.string.seerr_search_empty), Modifier.align(Alignment.TopCenter).padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(start = TvContentPadding, end = TvContentPadding, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.searchResults.size, key = { "${state.searchResults[it].type}-${state.searchResults[it].tmdbId}" }) {
                            val title = state.searchResults[it]
                            TvSeerrCard(title, onClick = { onOpenTitle(title.type, title.tmdbId) })
                        }
                    }
                }
            }
        } else {
            val feed = state.feed
            when {
                feed == null && state.feedError != null -> TvErrorBox(state.feedError!!, onRetry = viewModel::retry)
                feed == null -> TvLoadingBox()
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
                    listOf(
                        R.string.seerr_section_trending to feed.trending,
                        R.string.seerr_section_movies to feed.movies,
                        R.string.seerr_section_series to feed.series,
                    ).forEach { (label, items) ->
                        if (items.isNotEmpty()) {
                            item(key = label) {
                                TvSectionTitle(stringResource(label))
                                TvCardRow(items, key = { "${it.type}-${it.tmdbId}" }) {
                                    TvSeerrCard(it, onClick = { onOpenTitle(it.type, it.tmdbId) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
