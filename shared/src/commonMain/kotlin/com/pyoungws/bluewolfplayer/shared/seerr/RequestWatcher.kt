package com.pyoungws.bluewolfplayer.shared.seerr

import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A request change worth surfacing to the user, e.g. as a local notification. */
sealed interface RequestEvent {
    val request: SeerrRequest

    /** Admin only: a request from someone else is waiting for approval. */
    data class NewPending(override val request: SeerrRequest) : RequestEvent

    /** The user's own request was approved. */
    data class Approved(override val request: SeerrRequest) : RequestEvent

    /** The user's own request is now in the library. */
    data class Available(override val request: SeerrRequest) : RequestEvent
}

/**
 * Polls Seerr while a session is signed in and reports request changes on [events]: new pending
 * requests for admins, and approvals/availability for the user's own requests. The first
 * successful poll only seeds the baseline, so signing in does not flood the user with events for
 * requests they already know about. Delivery is left to the platform (local notifications).
 */
class RequestWatcher(
    private val repository: SeerrRepository,
    private val scope: CoroutineScope,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
) {
    private val _events = MutableSharedFlow<RequestEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<RequestEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var knownPending: Set<Int>? = null
    private var knownOwn: Map<Int, SeerrRequest>? = null

    /** Starts polling; repeated calls are no-ops. Polls only while signed in to Seerr. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            repository.authState.collectLatest { state ->
                val account = (state as? SeerrAuthState.SignedIn)?.account
                if (account == null) {
                    knownPending = null
                    knownOwn = null
                    return@collectLatest
                }
                while (isActive) {
                    poll(account)
                    delay(pollInterval)
                }
            }
        }
    }

    private suspend fun poll(account: SeerrAccount) {
        if (account.isAdmin) {
            when (val result = repository.pendingRequests()) {
                is Outcome.Success -> {
                    val baseline = knownPending
                    if (baseline != null) {
                        result.value
                            .filter { it.id !in baseline && it.requestedById != account.id }
                            .forEach { _events.emit(RequestEvent.NewPending(it)) }
                    }
                    knownPending = result.value.mapTo(HashSet()) { it.id }
                }
                // A failed poll keeps the baseline: a retry must not re-report known requests.
                is Outcome.Failure -> Unit
            }
        }
        when (val result = repository.myRequests()) {
            is Outcome.Success -> {
                val baseline = knownOwn
                if (baseline != null) {
                    result.value.forEach { request -> diff(request, baseline[request.id])?.let { _events.emit(it) } }
                }
                knownOwn = result.value.associateBy { it.id }
            }
            is Outcome.Failure -> Unit
        }
    }

    /**
     * The change between the previously seen [before] state of an own request and [current].
     * Availability wins over approval; a request seen for the first time ([before] null) is only
     * reported when it is already approved or available (made on another device, or auto-approved),
     * never when it is pending — the user just created it themselves.
     */
    private fun diff(current: SeerrRequest, before: SeerrRequest?): RequestEvent? = when {
        before == null -> when {
            current.isAvailable -> RequestEvent.Available(current)
            current.status == SeerrRequestStatus.Approved -> RequestEvent.Approved(current)
            else -> null
        }
        !before.isAvailable && current.isAvailable -> RequestEvent.Available(current)
        before.status == SeerrRequestStatus.Pending && current.status == SeerrRequestStatus.Approved ->
            RequestEvent.Approved(current)
        else -> null
    }

    companion object {
        val DEFAULT_POLL_INTERVAL = 1.minutes
        private const val EVENT_BUFFER = 32
    }
}
