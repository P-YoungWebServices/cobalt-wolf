package com.pyoungws.cobaltwolf.shared.auth

import com.pyoungws.cobaltwolf.shared.model.Session

/** Persists the signed-in session. Platform implementations must store the access token securely. */
interface SessionStore {
    suspend fun load(): Session?
    suspend fun save(session: Session)
    suspend fun clear()
}
