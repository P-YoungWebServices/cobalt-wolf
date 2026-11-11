package com.pyoungws.bluewolfplayer.shared.presentation

import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.syncplay.FakeSyncPlayDataSource
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayDataSource
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayRepository
import com.pyoungws.bluewolfplayer.shared.syncplay.group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayViewModelTest {
    private val dataSource = FakeSyncPlayDataSource()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.viewModel(): SyncPlayViewModel {
        val auth = signedInAuthRepository()
        return SyncPlayViewModel(SyncPlayRepository(dataSource, SessionGuard(auth), auth, scope = backgroundScope))
    }

    @Test
    fun `loads the groups on start`() = runTest {
        dataSource.groups = listOf(group("g1", "One", participants = listOf("a")), group("g2", "Two"))
        val viewModel = viewModel()
        assertEquals(SyncPlayUiState.Loading, viewModel.uiState.value)
        runCurrent()

        val state = viewModel.uiState.value as SyncPlayUiState.Ready
        assertEquals(listOf("g1", "g2"), state.groups.map { it.id })
        assertNull(state.membership)
    }

    @Test
    fun `a failed load surfaces the error`() = runTest {
        val failing = object : SyncPlayDataSource by dataSource {
            override suspend fun listGroups(session: Session) = throw InvalidStatusException(500)
        }
        val auth = signedInAuthRepository()
        val viewModel = SyncPlayViewModel(SyncPlayRepository(failing, SessionGuard(auth), auth, scope = backgroundScope))
        runCurrent()

        val error = (viewModel.uiState.value as? SyncPlayUiState.Error)?.error
        assertIs<LoadError.Unexpected>(error)
    }

    @Test
    fun `joining a group shows the membership`() = runTest {
        val viewModel = viewModel()
        runCurrent()

        viewModel.join(group("g9", "Night", participants = listOf("me")))
        runCurrent()

        val state = viewModel.uiState.value as SyncPlayUiState.Ready
        assertEquals("g9", state.membership?.groupId)
        assertEquals(false, state.busy)
    }

    @Test
    fun `a failed join keeps the action error`() = runTest {
        dataSource.joinResult = { throw InvalidStatusException(403) }
        val viewModel = viewModel()
        runCurrent()

        viewModel.join(group("g1"))
        runCurrent()

        val state = viewModel.uiState.value as SyncPlayUiState.Ready
        assertEquals(LoadError.Forbidden, state.actionError)
        assertNull(state.membership)
    }

    @Test
    fun `leaving clears the membership`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        viewModel.join(group("g1"))
        runCurrent()

        viewModel.leave()
        runCurrent()

        assertNull((viewModel.uiState.value as SyncPlayUiState.Ready).membership)
    }

    @Test
    fun `an empty name creates a group with the default name`() = runTest {
        var createdName: String? = null
        dataSource.created = { name -> createdName = name; group("g-new", name) }
        val viewModel = viewModel()
        runCurrent()

        viewModel.createAndJoin("")
        runCurrent()

        assertEquals("Cobalt Wolf", createdName)
        val member = (viewModel.uiState.value as SyncPlayUiState.Ready).membership
        assertEquals("g-new", member?.groupId)
        assertTrue(dataSource.calls.contains("join(g-new)"))
    }
}
