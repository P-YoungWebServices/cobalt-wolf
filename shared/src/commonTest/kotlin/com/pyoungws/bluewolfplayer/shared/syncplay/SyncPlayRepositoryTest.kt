package com.pyoungws.bluewolfplayer.shared.syncplay

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.library.signedInAuthRepository
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayEvent.Correction
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayEvent.QueueChanged
import com.pyoungws.bluewolfplayer.shared.syncplay.SyncPlayEvent.StateChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPlayRepositoryTest {
    private val dataSource = FakeSyncPlayDataSource()
    private var now = 0L

    private suspend fun TestScope.repository(
        authRepository: AuthRepository? = null,
    ): SyncPlayRepository {
        val auth = authRepository ?: signedInAuthRepository()
        return SyncPlayRepository(
            dataSource,
            SessionGuard(auth),
            auth,
            scope = backgroundScope,
            nowMs = { now },
        )
    }

    private fun TestScope.commandsOf(repository: SyncPlayRepository): MutableList<SyncPlayPlayerCommand> {
        val commands = mutableListOf<SyncPlayPlayerCommand>()
        backgroundScope.launch { repository.playerCommands.toList(commands) }
        return commands
    }

    @Test
    fun `joining a group enters membership`() = runTest {
        val repository = repository()
        assertIs<Outcome.Success<*>>(repository.join(group("g1", "Movie night", participants = listOf("alice"))))
        runCurrent()

        val member = repository.membership.value
        assertEquals("g1", member?.groupId)
        assertEquals("Movie night", member?.groupName)
        assertEquals(listOf("alice"), member?.participants)
        assertEquals(listOf("join(g1)"), dataSource.calls)
    }

    @Test
    fun `a queue update loads the playing item`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()

        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), playingItemIndex = 0, startPositionTicks = 300_000_000, isPlaying = true))
        runCurrent()

        assertEquals(
            SyncPlayPlayerCommand.LoadItem("item-1", positionTicks = 300_000_000, playWhenReady = true),
            commands.lastOrNull(),
        )
        assertEquals("item-1", repository.membership.value?.playingItem?.itemId)
    }

    @Test
    fun `an attach replays the current item at the estimated position`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), playingItemIndex = 0, startPositionTicks = 100_000_000, isPlaying = true))
        runCurrent()

        // Three seconds later the group has moved on by the same amount.
        now += 3_000
        repository.attachPlayer()
        runCurrent()

        assertEquals(SyncPlayPlayerCommand.LoadItem("item-1", 130_000_000, playWhenReady = true), commands.last())
    }

    @Test
    fun `pausing freezes the position and playing resumes it`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 100_000_000, isPlaying = true))
        runCurrent()

        now += 2_000
        dataSource.emit(StateChanged(SyncPlayGroupState.Paused))
        runCurrent()
        assertEquals(SyncPlayPlayerCommand.Pause, commands.last())
        // The frozen position includes the 2 s of playback, and does not advance while paused.
        now += 60_000
        dataSource.emit(StateChanged(SyncPlayGroupState.Playing))
        runCurrent()
        assertEquals(SyncPlayPlayerCommand.Resume(120_000_000), commands.last())
        assertEquals(false, repository.membership.value?.let { it.isPlaying && it.groupState != SyncPlayGroupState.Playing })
    }

    @Test
    fun `a position jump on the same item seeks, a routine queue update does not`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 100_000_000, isPlaying = true))
        runCurrent()

        // The group position kept running for 2 s: no seek needed.
        now += 2_000
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 120_000_000, isPlaying = true))
        runCurrent()
        assertIs<SyncPlayPlayerCommand.LoadItem>(commands.last())

        // A jump of 30 s beyond the expected position is a real seek.
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 420_000_000, isPlaying = true))
        runCurrent()
        assertEquals(SyncPlayPlayerCommand.Seek(420_000_000), commands.last())
    }

    @Test
    fun `the echo of a local seek is not applied`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 0, isPlaying = true))
        runCurrent()
        commands.clear()

        repository.localSeek(500_000_000)
        runCurrent()
        assertEquals(500_000_000L, dataSource.lastSeekTicks)

        // The group broadcasts the same position back: the player already went there.
        dataSource.emit(Correction(CorrectionType.Seek, 501_000_000))
        runCurrent()
        assertTrue(commands.isEmpty())

        // A different position after the window would be applied.
        dataSource.emit(Correction(CorrectionType.Seek, 900_000_000))
        runCurrent()
        assertEquals(SyncPlayPlayerCommand.Seek(900_000_000), commands.last())
    }

    @Test
    fun `corrections drive the player`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        val commands = commandsOf(repository)
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1")), 0, 0, isPlaying = true))
        runCurrent()
        commands.clear()

        dataSource.emit(Correction(CorrectionType.Pause, null))
        dataSource.emit(Correction(CorrectionType.Unpause, 50_000_000))
        dataSource.emit(Correction(CorrectionType.Stop, null))
        runCurrent()

        assertEquals(
            listOf(SyncPlayPlayerCommand.Pause, SyncPlayPlayerCommand.Resume(50_000_000), SyncPlayPlayerCommand.Stop),
            commands,
        )
    }

    @Test
    fun `members joining and leaving update participants`() = runTest {
        val repository = repository()
        repository.join(group("g1", participants = listOf("me")))
        runCurrent()

        dataSource.emit(SyncPlayEvent.UserJoined("alice"))
        dataSource.emit(SyncPlayEvent.UserJoined("alice"))
        dataSource.emit(SyncPlayEvent.UserLeft("me"))
        runCurrent()

        assertEquals(listOf("alice"), repository.membership.value?.participants)
    }

    @Test
    fun `leaving the group clears membership and calls the server`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        runCurrent()

        repository.leave()
        runCurrent()

        assertNull(repository.membership.value)
        assertTrue("leave" in dataSource.calls)
    }

    @Test
    fun `a not-in-group update drops membership with a notice`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        runCurrent()
        val notices = mutableListOf<SyncPlayNotice>()
        backgroundScope.launch { repository.notices.toList(notices) }

        dataSource.emit(SyncPlayEvent.NotInGroup)
        runCurrent()

        assertNull(repository.membership.value)
        assertEquals(listOf(SyncPlayNotice.GroupClosed), notices)
    }

    @Test
    fun `a lost event stream drops membership and leaves on the server`() = runTest {
        val repository = repository()
        repository.join(group("g1"))
        runCurrent()

        dataSource.emit(SyncPlayEvent.Error("socket closed"))
        runCurrent()

        assertNull(repository.membership.value)
        assertTrue("leave" in dataSource.calls)
    }

    @Test
    fun `local transport is reported to the group, and only in one`() = runTest {
        val repository = repository()
        repository.localPause()
        repository.localPing(10)
        runCurrent()
        assertTrue(dataSource.calls.isEmpty())

        repository.join(group("g1"))
        // Let the event collector subscribe before emitting: missed socket events are not replayed.
        runCurrent()
        dataSource.emit(QueueChanged(listOf(queueItem("item-1", playlistItemId = "pl-1")), 0, 0, isPlaying = true))
        runCurrent()

        repository.localPause()
        repository.localResume()
        repository.localPing(123_000_000)
        repository.localBuffering(10, isPlaying = true)
        repository.localReady(20, isPlaying = true)
        runCurrent()

        assertEquals(
            listOf("join(g1)", "pause", "unpause", "ping", "buffering(10)", "ready(20)"),
            dataSource.calls.filter { it != "groups" },
        )
    }

    @Test
    fun `playInGroup sends the new queue`() = runTest {
        val repository = repository()
        assertIs<Outcome.Failure>(repository.playInGroup(listOf("item-1")))

        repository.join(group("g1"))
        runCurrent()
        assertIs<Outcome.Success<*>>(repository.playInGroup(listOf("item-2", "item-3"), startIndex = 1))
        assertEquals(listOf("item-2", "item-3"), dataSource.lastQueue)
    }

    @Test
    fun `signing out drops the membership`() = runTest {
        val auth = signedInAuthRepository()
        val repository = repository(auth)
        repository.join(group("g1"))
        runCurrent()

        auth.signOut()
        runCurrent()

        assertNull(repository.membership.value)
    }
}
