package com.pyoungws.bluewolfplayer.shared.admin

import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.FakeAuthDataSource
import com.pyoungws.bluewolfplayer.shared.auth.InMemorySessionStore
import com.pyoungws.bluewolfplayer.shared.library.SESSION
import com.pyoungws.bluewolfplayer.shared.library.SessionGuard
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminManagementRepositoryTest {

    private suspend fun repository(dataSource: FakeAdminDataSource): AdminRepository =
        AdminRepository(dataSource, SessionGuard(signedIn()))

    @Test
    fun `lists users, devices, libraries, tasks, log files and system info`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { listOf(adminUser("u1"), adminUser("u2", admin = true)) }
            devices = { listOf(adminDevice("d1")) }
            libraries = { listOf(adminLibrary("l1")) }
            tasks = { listOf(scheduledTask("t1", state = AdminTaskState.Running, progress = 0.5f)) }
            logFiles = { listOf(logFile("b.log"), logFile("a.log")) }
            systemInfo = { sampleSystemInfo(updateAvailable = true) }
        }
        val repo = repository(dataSource)

        assertEquals(listOf("u1", "u2"), assertIs<Outcome.Success<List<AdminUser>>>(repo.users()).value.map { it.id })
        assertEquals(listOf("d1"), assertIs<Outcome.Success<List<AdminDevice>>>(repo.devices()).value.map { it.id })
        assertEquals(listOf("l1"), assertIs<Outcome.Success<List<AdminLibrary>>>(repo.libraries()).value.map { it.id })
        assertEquals(listOf("t1"), assertIs<Outcome.Success<List<ScheduledTask>>>(repo.scheduledTasks()).value.map { it.id })
        assertEquals(listOf("b.log", "a.log"), assertIs<Outcome.Success<List<ServerLogFile>>>(repo.logFiles()).value.map { it.name })
        assertEquals("wolf", assertIs<Outcome.Success<SystemInformation>>(repo.systemInfo()).value.serverName)
        assertTrue(assertIs<Outcome.Success<SystemInformation>>(repo.systemInfo()).value.hasUpdateAvailable)
        assertEquals(listOf("users", "devices", "adminLibraries", "tasks", "logFiles", "systemInfo", "systemInfo"), dataSource.calls)
    }

    @Test
    fun `createUser trims the name and maps a blank password to none`() = runTest {
        val dataSource = FakeAdminDataSource()
        var capturedPassword: String? = "untouched"
        dataSource.onCreateUser = { _, password -> capturedPassword = password; adminUser("u-new") }

        val result = repository(dataSource).createUser("  bob  ", "   ")

        assertIs<Outcome.Success<AdminUser>>(result)
        assertEquals(listOf("createUser(bob)"), dataSource.calls)
        assertNull(capturedPassword)
    }

    @Test
    fun `updateUser forwards the edit with a trimmed name`() = runTest {
        val dataSource = FakeAdminDataSource()

        repository(dataSource).updateUser("u1", AdminUserEdit(name = "  renamed  ", isDisabled = true))

        assertEquals(
            listOf("updateUser(u1, AdminUserEdit(name=renamed, isAdministrator=null, isDisabled=true, isHidden=null, password=null))"),
            dataSource.calls,
        )
    }

    @Test
    fun `delete, rename and task actions forward to the data source`() = runTest {
        val dataSource = FakeAdminDataSource()
        val repo = repository(dataSource)

        repo.deleteUser("u9")
        repo.renameDevice("d1", "  Living room  ")
        repo.deleteDevice("d1")
        repo.startTask("t1")
        repo.stopTask("t1")
        repo.logContent("a.log")

        assertEquals(
            listOf("deleteUser(u9)", "renameDevice(d1, Living room)", "deleteDevice(d1)", "startTask(t1)", "stopTask(t1)", "logContent(a.log)"),
            dataSource.calls,
        )
    }

    @Test
    fun `permission denied is reported, not thrown`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            users = { throw InvalidStatusException(403) }
        }

        assertEquals(Outcome.Failure(LoadError.Forbidden), repository(dataSource).users())
    }

    @Test
    fun `an unreachable server is reported, not thrown`() = runTest {
        val dataSource = FakeAdminDataSource().apply {
            tasks = { throw TimeoutException("timed out", null) }
        }

        assertEquals(Outcome.Failure(LoadError.ServerUnreachable), repository(dataSource).scheduledTasks())
    }

    private suspend fun signedIn(): AuthRepository =
        AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)).apply { restoreSession() }
}
