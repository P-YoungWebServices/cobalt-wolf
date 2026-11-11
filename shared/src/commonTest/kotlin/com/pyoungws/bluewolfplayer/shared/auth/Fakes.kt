package com.pyoungws.bluewolfplayer.shared.auth

import com.pyoungws.bluewolfplayer.shared.model.PublicUser
import com.pyoungws.bluewolfplayer.shared.model.Session
import com.pyoungws.bluewolfplayer.shared.model.UserProfile

const val SERVER = "https://jellyfin.example.com"
const val SECRET = "qc-secret"
const val CODE = "123456"

val ADMIN = UserProfile(id = "user-1", name = "alice", isAdministrator = true)
val VIEWER = UserProfile(id = "user-2", name = "bob", isAdministrator = false)

class InMemorySessionStore(var stored: Session? = null) : SessionStore {
    override suspend fun load(): Session? = stored
    override suspend fun save(session: Session) {
        stored = session
    }
    override suspend fun clear() {
        stored = null
    }
}


class FakeAuthDataSource : JellyfinAuthDataSource {
    val calls = mutableListOf<String>()
    var pollCount = 0
        private set

    var onGetPublicUsers: () -> List<PublicUser> = { emptyList() }
    var onAuthenticateByName: (username: String, password: String) -> AuthenticatedUser =
        { _, _ -> AuthenticatedUser("token-password", ADMIN) }
    var onQuickConnectEnabled: () -> Boolean = { true }
    var onInitiate: () -> QuickConnectTicket = { QuickConnectTicket(CODE, SECRET, authenticated = false) }

    /** Called with the 1-based poll number. Approves on the second poll by default. */
    var onPoll: (poll: Int) -> QuickConnectTicket = { QuickConnectTicket(CODE, SECRET, authenticated = it >= 2) }
    var onAuthenticateWithQuickConnect: (secret: String) -> AuthenticatedUser =
        { AuthenticatedUser("token-quick-connect", VIEWER) }
    var onGetCurrentUser: (accessToken: String) -> UserProfile = { ADMIN }
    var onGetServerName: () -> String? = { "Test Server" }
    var onLogout: suspend (accessToken: String) -> Unit = {}

    override suspend fun getPublicUsers(serverAddress: String): List<PublicUser> {
        calls += "getPublicUsers"
        return onGetPublicUsers()
    }

    override suspend fun authenticateByName(serverAddress: String, username: String, password: String): AuthenticatedUser {
        calls += "authenticateByName($serverAddress, $username)"
        return onAuthenticateByName(username, password)
    }

    override suspend fun isQuickConnectEnabled(serverAddress: String): Boolean {
        calls += "isQuickConnectEnabled"
        return onQuickConnectEnabled()
    }

    override suspend fun initiateQuickConnect(serverAddress: String): QuickConnectTicket {
        calls += "initiateQuickConnect"
        return onInitiate()
    }

    override suspend fun getQuickConnectState(serverAddress: String, secret: String): QuickConnectTicket {
        calls += "getQuickConnectState($secret)"
        return onPoll(++pollCount)
    }

    override suspend fun authenticateWithQuickConnect(serverAddress: String, secret: String): AuthenticatedUser {
        calls += "authenticateWithQuickConnect($secret)"
        return onAuthenticateWithQuickConnect(secret)
    }

    override suspend fun getCurrentUser(serverAddress: String, accessToken: String): UserProfile {
        calls += "getCurrentUser($accessToken)"
        return onGetCurrentUser(accessToken)
    }

    override suspend fun getServerName(serverAddress: String): String? {
        calls += "getServerName($serverAddress)"
        return onGetServerName()
    }

    override suspend fun logout(serverAddress: String, accessToken: String) {
        calls += "logout($serverAddress, $accessToken)"
        onLogout(accessToken)
    }
}
