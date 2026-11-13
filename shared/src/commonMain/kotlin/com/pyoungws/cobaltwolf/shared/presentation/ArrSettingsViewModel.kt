package com.pyoungws.cobaltwolf.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.cobaltwolf.shared.arrs.ArrRepository
import com.pyoungws.cobaltwolf.shared.arrs.ArrService
import com.pyoungws.cobaltwolf.shared.arrs.forService
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable connection form for one service. */
data class ArrServiceForm(
    val serverUrl: String = "",
    val apiKey: String = "",
    val testing: Boolean = false,
    /** Result of the last connection test; cleared when the fields change. */
    val testSucceeded: Boolean? = null,
    val testError: LoadError? = null,
    val saved: Boolean = false,
)

data class ArrSettingsUiState(
    val radarr: ArrServiceForm = ArrServiceForm(),
    val sonarr: ArrServiceForm = ArrServiceForm(),
    val bazarr: ArrServiceForm = ArrServiceForm(),
    val prowlarr: ArrServiceForm = ArrServiceForm(),
) {
    fun form(service: ArrService): ArrServiceForm = when (service) {
        ArrService.Radarr -> radarr
        ArrService.Sonarr -> sonarr
        ArrService.Bazarr -> bazarr
        ArrService.Prowlarr -> prowlarr
    }
}

/** URL + API-key settings for the *arr services, with save and connection test per service. */
class ArrSettingsViewModel(
    private val repository: ArrRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ArrSettingsUiState(
            radarr = repository.settings.value.radarr.toForm(),
            sonarr = repository.settings.value.sonarr.toForm(),
            bazarr = repository.settings.value.bazarr.toForm(),
            prowlarr = repository.settings.value.prowlarr.toForm(),
        ),
    )
    val uiState = _uiState.asStateFlow()

    fun onServerUrlChange(service: ArrService, value: String) = updateForm(service) {
        it.copy(serverUrl = value, testSucceeded = null, testError = null, saved = false)
    }

    fun onApiKeyChange(service: ArrService, value: String) = updateForm(service) {
        it.copy(apiKey = value, testSucceeded = null, testError = null, saved = false)
    }

    fun save(service: ArrService) {
        val form = _uiState.value.form(service)
        repository.saveService(service, form.serverUrl, form.apiKey)
        updateForm(service) { it.copy(saved = true) }
    }

    fun test(service: ArrService) {
        val form = _uiState.value.form(service)
        updateForm(service) { it.copy(testing = true, testSucceeded = null, testError = null) }
        viewModelScope.launch {
            when (val outcome = repository.testConnection(service, form.serverUrl, form.apiKey)) {
                is Outcome.Success -> updateForm(service) { it.copy(testing = false, testSucceeded = true) }
                is Outcome.Failure -> updateForm(service) { it.copy(testing = false, testSucceeded = false, testError = outcome.error) }
            }
        }
    }

    private fun updateForm(service: ArrService, transform: (ArrServiceForm) -> ArrServiceForm) {
        _uiState.update { state ->
            when (service) {
                ArrService.Radarr -> state.copy(radarr = transform(state.radarr))
                ArrService.Sonarr -> state.copy(sonarr = transform(state.sonarr))
                ArrService.Bazarr -> state.copy(bazarr = transform(state.bazarr))
                ArrService.Prowlarr -> state.copy(prowlarr = transform(state.prowlarr))
            }
        }
    }

    private fun com.pyoungws.cobaltwolf.shared.arrs.ArrServiceSettings.toForm() =
        ArrServiceForm(serverUrl = serverUrl.orEmpty(), apiKey = apiKey.orEmpty())
}
