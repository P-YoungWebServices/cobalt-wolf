package com.pyoungws.bluewolfplayer.shared.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.admin.AdminTaskState
import com.pyoungws.bluewolfplayer.shared.admin.AdminUserEdit
import com.pyoungws.bluewolfplayer.shared.admin.FakeAdminDataSource
import com.pyoungws.bluewolfplayer.shared.admin.LogSeverity
import com.pyoungws.bluewolfplayer.shared.admin.adminDevice
import com.pyoungws.bluewolfplayer.shared.admin.adminLibrary
import com.pyoungws.bluewolfplayer.shared.admin.adminUser
import com.pyoungws.bluewolfplayer.shared.admin.logFile
import com.pyoungws.bluewolfplayer.shared.admin.scheduledTask
import com.pyoungws.bluewolfplayer.shared.admin.sampleSystemInfo
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class AdminManagementViewModelsTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Cancels the view model scope afterwards so poll loops cannot hang runTest. */
    private suspend fun <T : ViewModel> withViewModel(viewModel: T, body: suspend (T) -> Unit) {
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private suspend fun repository(dataSource: FakeAdminDataSource): AdminRepository {
        val auth = AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }
        return AdminRepository(dataSource, SessionGuard(auth))
    }

    // Users

    @Test
    fun `users screen loads the account list`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { listOf(adminUser("u1"), adminUser("u2", admin = true)) }
        }
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(listOf("u1", "u2"), state.users.map { it.id })
            assertNull(state.error)
        }
    }

    @Test
    fun `creating a user reloads the list and reports success`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { listOf(adminUser("u1")) }
            onCreateUser = { name, _ ->
                users = { listOf(adminUser("u1"), adminUser("u-new", name)) }
                adminUser("u-new", name)
            }
        }
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.createUser("bob", "secret")
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Succeeded(AdminUsersViewModel.CREATE_KEY), state.action)
            assertEquals(listOf("u1", "u-new"), state.users.map { it.id })
            assertTrue("createUser(bob)" in dataSource.calls)
        }
    }

    @Test
    fun `a blank user name never reaches the server`() = runTest {
        val dataSource = FakeAdminDataSource()
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.createUser("   ", "secret")
            runCurrent()

            assertNull(viewModel.uiState.value.action)
            assertTrue(dataSource.calls.none { it.startsWith("createUser") })
        }
    }

    @Test
    fun `editing a user applies the change and reports success`() = runTest {
        val dataSource = FakeAdminDataSource().apply { users = { listOf(adminUser("u1")) } }
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.updateUser("u1", AdminUserEdit(name = "renamed", isAdministrator = true))
            runCurrent()

            assertEquals(AdminActionStatus.Succeeded("${AdminUsersViewModel.EDIT_KEY}u1"), viewModel.uiState.value.action)
            assertTrue(dataSource.calls.any { it.startsWith("updateUser(u1") && "isAdministrator=true" in it })
        }
    }

    @Test
    fun `a failed delete surfaces the error and keeps the list`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { listOf(adminUser("u1")) }
            onDeleteUser = { throw InvalidStatusException(403) }
        }
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.deleteUser("u1")
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Failed("${AdminUsersViewModel.DELETE_KEY}u1", LoadError.Forbidden), state.action)
            assertEquals(listOf("u1"), state.users.map { it.id })
        }
    }

    @Test
    fun `a permission denied user list shows the error`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { throw InvalidStatusException(403) }
        }
        withViewModel(AdminUsersViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertEquals(LoadError.Forbidden, state.error)
            assertTrue(state.users.isEmpty())
        }
    }

    // Devices

    @Test
    fun `renaming a device reloads the list`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            devices = { listOf(adminDevice("d1", "Shield")) }
            onRenameDevice = { _, name -> devices = { listOf(adminDevice("d1", name)) } }
        }
        withViewModel(AdminDevicesViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.renameDevice("d1", "Living room")
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(AdminActionStatus.Succeeded("${AdminDevicesViewModel.RENAME_KEY}d1"), state.action)
            assertEquals("Living room", state.devices.single().name)
        }
    }

    @Test
    fun `deleting a device removes it`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            devices = { listOf(adminDevice("d1"), adminDevice("d2")) }
            onDeleteDevice = { id ->
                val rest = devices().filterNot { it.id == id }
                devices = { rest }
            }
        }
        withViewModel(AdminDevicesViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.deleteDevice("d1")
            runCurrent()

            assertEquals(listOf("d2"), viewModel.uiState.value.devices.map { it.id })
        }
    }

    // Libraries

    @Test
    fun `libraries screen loads the virtual folders`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            libraries = { listOf(adminLibrary("l1"), adminLibrary("l2")) }
        }
        withViewModel(AdminLibrariesViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            assertEquals(listOf("l1", "l2"), viewModel.uiState.value.libraries.map { it.id })
        }
    }

    // Scheduled tasks

    @Test
    fun `tasks poll keeps progress live`() = runTest {
        var calls = 0
        val dataSource = FakeAdminDataSource().apply {
            tasks = {
                calls++
                listOf(scheduledTask("t1", state = AdminTaskState.Running, progress = calls * 0.1f))
            }
        }
        withViewModel(AdminTasksViewModel(repository(dataSource))) { viewModel ->
            runCurrent()
            assertEquals(0.1f, viewModel.uiState.value.tasks.single().progressFraction)

            advanceTimeBy(AdminTasksViewModel.POLL_SECONDS * 1_000L)
            runCurrent()
            assertEquals(0.2f, viewModel.uiState.value.tasks.single().progressFraction)
        }
    }

    @Test
    fun `starting a task calls the server and reloads`() = runTest {
        var running = false
        val dataSource = FakeAdminDataSource().apply {
            tasks = { listOf(scheduledTask("t1", state = if (running) AdminTaskState.Running else AdminTaskState.Idle)) }
            onStartTask = { running = true }
        }
        withViewModel(AdminTasksViewModel(repository(dataSource))) { viewModel ->
            runCurrent()
            assertEquals(AdminTaskState.Idle, viewModel.uiState.value.tasks.single().state)

            viewModel.startTask("t1")
            runCurrent()

            assertEquals(AdminActionStatus.Succeeded("${AdminTasksViewModel.START_KEY}t1"), viewModel.uiState.value.action)
            assertEquals(AdminTaskState.Running, viewModel.uiState.value.tasks.single().state)
        }
    }

    @Test
    fun `a failed stop is surfaced`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            tasks = { listOf(scheduledTask("t1", state = AdminTaskState.Running, progress = 0.3f)) }
            onStopTask = { throw InvalidStatusException(404) }
        }
        withViewModel(AdminTasksViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.stopTask("t1")
            runCurrent()

            assertEquals(AdminActionStatus.Failed("${AdminTasksViewModel.STOP_KEY}t1", LoadError.NotFound), viewModel.uiState.value.action)
        }
    }

    // Logs

    private companion object {
        val LOG_CONTENT = """
            [2026-11-10 12:00:00.000 +00:00] [INF] first
            [2026-11-10 12:00:01.000 +00:00] [ERR] second
        """.trimIndent()
    }

    @Test
    fun `logs screen selects the newest file and shows its lines`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("today.log"), logFile("yesterday.log")) }
            logContent = { LOG_CONTENT }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals("today.log", state.selectedFile)
            assertEquals(2, state.lines.size)
            assertEquals(listOf(LogSeverity.Info, LogSeverity.Error), state.lines.map { it.severity })
        }
    }

    @Test
    fun `switching log file loads its content`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("today.log"), logFile("yesterday.log")) }
            logContent = { name -> "[2026-11-10 12:00:00.000 +00:00] [INF] $name" }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.selectFile("yesterday.log")
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals("yesterday.log", state.selectedFile)
            assertEquals(1, state.lines.size)
            assertTrue(state.lines.single().text.endsWith("yesterday.log"))
        }
    }

    @Test
    fun `severity and text filters restrict the visible lines`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("today.log")) }
            logContent = { LOG_CONTENT }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            viewModel.setMinSeverity(LogSeverity.Error)
            assertEquals(1, viewModel.uiState.value.visibleLines.size)

            viewModel.setMinSeverity(null)
            viewModel.setQuery("first")
            assertEquals(1, viewModel.uiState.value.visibleLines.size)

            viewModel.setQuery("")
            assertEquals(2, viewModel.uiState.value.visibleLines.size)
        }
    }

    @Test
    fun `the open log file re-fetches on the poll interval`() = runTest {
        var fetches = 0
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("today.log")) }
            logContent = {
                fetches++
                "[2026-11-10 12:00:0${fetches}.000 +00:00] [INF] line $fetches"
            }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            advanceTimeBy(AdminLogsViewModel.POLL_SECONDS * 1_000L)
            runCurrent()

            assertTrue(fetches >= 2)
            assertTrue(viewModel.uiState.value.lines.single().text.endsWith("line $fetches"))
        }
    }

    @Test
    fun `the log view keeps only the last MAX_LINES`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("big.log")) }
            logContent = { (1..AdminLogsViewModel.MAX_LINES + 50).joinToString("\n") { "[2026-11-10 12:00:00.000 +00:00] [INF] line $it" } }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val lines = viewModel.uiState.value.lines
            assertEquals(AdminLogsViewModel.MAX_LINES, lines.size)
            assertTrue(lines.last().text.endsWith("line ${AdminLogsViewModel.MAX_LINES + 50}"))
        }
    }

    @Test
    fun `a removed selected file falls back to the newest`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            logFiles = { listOf(logFile("today.log"), logFile("old.log")) }
            logContent = { LOG_CONTENT }
        }
        withViewModel(AdminLogsViewModel(repository(dataSource))) { viewModel ->
            runCurrent()
            viewModel.selectFile("old.log")
            runCurrent()

            dataSource.logFiles = { listOf(logFile("today.log")) }
            viewModel.refresh()
            runCurrent()

            assertEquals("today.log", viewModel.uiState.value.selectedFile)
        }
    }

    // System info

    @Test
    fun `system info loads server details and update status`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            systemInfo = { sampleSystemInfo(updateAvailable = true) }
        }
        withViewModel(AdminSystemInfoViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val info = viewModel.uiState.value.info
            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals("12.0.0", info?.version)
            assertTrue(info?.hasUpdateAvailable == true)
        }
    }

    @Test
    fun `a server error on system info is shown`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            systemInfo = { throw InvalidStatusException(500) }
        }
        withViewModel(AdminSystemInfoViewModel(repository(dataSource))) { viewModel ->
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(false, state.isLoading)
            assertIs<LoadError.Unexpected>(state.error)
            assertNull(state.info)
        }
    }
}
