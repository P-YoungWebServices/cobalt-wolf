package com.pyoungws.bluewolfplayer.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverPhase
import com.pyoungws.bluewolfplayer.shared.presentation.DiscoverViewModel
import com.pyoungws.bluewolfplayer.shared.presentation.SeerrUrlError
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrMediaType
import com.pyoungws.bluewolfplayer.shared.seerr.SeerrTitle
import com.pyoungws.bluewolfplayer.ui.common.Artwork
import com.pyoungws.bluewolfplayer.ui.common.CardRow
import com.pyoungws.bluewolfplayer.ui.common.ErrorBox
import com.pyoungws.bluewolfplayer.ui.common.LoadingBox
import com.pyoungws.bluewolfplayer.ui.common.SectionTitle
import com.pyoungws.bluewolfplayer.ui.message
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onBack: () -> Unit,
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
    onMyRequests: () -> Unit,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    if (state.phase == DiscoverPhase.Ready) {
                        TextButton(onClick = onMyRequests) { Text(stringResource(R.string.discover_my_requests)) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                DiscoverPhase.Loading -> LoadingBox()
                DiscoverPhase.Setup -> SetupForm(state, viewModel)
                DiscoverPhase.SignIn -> SignInForm(state, viewModel)
                DiscoverPhase.Ready -> DiscoverContent(state, viewModel, onOpenTitle)
            }
        }
    }
}

@Composable
private fun SetupForm(state: com.pyoungws.bluewolfplayer.shared.presentation.DiscoverUiState, viewModel: DiscoverViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.seerr_setup_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.seerr_setup_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            singleLine = true,
            isError = state.urlError != null,
            label = { Text(stringResource(R.string.seerr_url_label)) },
            placeholder = { Text(stringResource(R.string.seerr_url_hint)) },
            supportingText = {
                state.urlError?.let {
                    Text(
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
            if (state.isConnecting) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.seerr_connect))
        }
    }
}

@Composable
private fun SignInForm(state: com.pyoungws.bluewolfplayer.shared.presentation.DiscoverUiState, viewModel: DiscoverViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.seerr_signin_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.seerr_signin_subtitle, state.username.ifBlank { state.serverUrl }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.restoreError?.let { error ->
            Text(error.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.home_retry)) }
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            singleLine = true,
            label = { Text(stringResource(R.string.sign_in_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        state.signInError?.let { Text(it.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(onClick = viewModel::signIn, enabled = !state.isSigningIn) {
            if (state.isSigningIn) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.seerr_signin_button))
        }
        TextButton(onClick = viewModel::editServer) { Text(stringResource(R.string.seerr_change_server)) }
    }
}

@Composable
private fun DiscoverContent(
    state: com.pyoungws.bluewolfplayer.shared.presentation.DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenTitle: (SeerrMediaType, Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.seerr_search_hint)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        if (state.isSearchingActive) {
            Box(Modifier.fillMaxSize()) {
                when {
                    state.searchError != null -> ErrorBox(state.searchError!!, onRetry = null)
                    state.isSearching && state.searchResults.isEmpty() ->
                        CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(16.dp))
                    state.showNoResults ->
                        Text(stringResource(R.string.seerr_search_empty), Modifier.align(Alignment.TopCenter).padding(16.dp))
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.searchResults, key = { "${it.type}-${it.tmdbId}" }) { title ->
                            SearchResultRow(title, onClick = { onOpenTitle(title.type, title.tmdbId) })
                        }
                    }
                }
            }
        } else {
            val feed = state.feed
            when {
                feed == null && state.feedError != null -> ErrorBox(state.feedError!!, onRetry = viewModel::retry)
                feed == null -> LoadingBox()
                else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        R.string.seerr_section_trending to feed.trending,
                        R.string.seerr_section_movies to feed.movies,
                        R.string.seerr_section_series to feed.series,
                    ).forEach { (label, items) ->
                        if (items.isNotEmpty()) {
                            item(key = label) {
                                Column {
                                    SectionTitle(stringResource(label))
                                    CardRow(items, key = { "${it.type}-${it.tmdbId}" }) {
                                        SeerrPosterCard(it, onClick = { onOpenTitle(it.type, it.tmdbId) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(title: SeerrTitle, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(title.posterUrl, aspectRatio = 2f / 3f, modifier = Modifier.width(56.dp))
        Column(Modifier.weight(1f)) {
            Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            title.year?.let {
                Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                stringResource(if (title.type == SeerrMediaType.Movie) R.string.request_kind_movie else R.string.request_kind_series),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        SeerrStatusBadge(title)
    }
}
