package com.pyoungws.cobaltwolf.shared.library

import com.pyoungws.cobaltwolf.shared.auth.AuthRepository
import com.pyoungws.cobaltwolf.shared.auth.FakeAuthDataSource
import com.pyoungws.cobaltwolf.shared.auth.InMemorySessionStore
import com.pyoungws.cobaltwolf.shared.model.LoadError
import com.pyoungws.cobaltwolf.shared.model.Outcome
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionGuardTest {
    @Test
    fun `a stored session is restored when the guard runs before app start-up did`() = runTest {
        val guard = SessionGuard(AuthRepository(FakeAuthDataSource(), InMemorySessionStore(SESSION)))

        assertEquals(Outcome.Success(SESSION), guard.run { it })
    }

    @Test
    fun `without a stored session the call fails as expired`() = runTest {
        val guard = SessionGuard(AuthRepository(FakeAuthDataSource(), InMemorySessionStore()))

        assertEquals(Outcome.Failure(LoadError.SessionExpired), guard.run { it })
    }
}
