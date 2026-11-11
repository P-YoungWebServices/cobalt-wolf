package com.pyoungws.bluewolfplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.auth.SignInError
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.server.ServerError

@Composable
fun ServerError.message(): String = when (this) {
    ServerError.InvalidAddress -> stringResource(R.string.error_server_invalid_address)
    ServerError.Unreachable -> stringResource(R.string.error_server_unreachable)
    ServerError.SecureConnectionFailed -> stringResource(R.string.error_server_secure_connection)
    ServerError.NotJellyfin -> stringResource(R.string.error_server_not_jellyfin)
    is ServerError.UnsupportedVersion -> stringResource(R.string.error_server_unsupported_version, version)
}

@Composable
fun SignInError.message(): String = when (this) {
    SignInError.MissingUsername -> stringResource(R.string.error_sign_in_missing_username)
    SignInError.InvalidCredentials -> stringResource(R.string.error_sign_in_invalid_credentials)
    SignInError.SessionExpired -> stringResource(R.string.error_sign_in_session_expired)
    SignInError.ServerUnreachable -> stringResource(R.string.error_sign_in_unreachable)
    SignInError.SecureConnectionFailed -> stringResource(R.string.error_sign_in_secure_connection)
    SignInError.QuickConnectDisabled -> stringResource(R.string.error_quick_connect_disabled)
    SignInError.QuickConnectExpired -> stringResource(R.string.error_quick_connect_expired)
    is SignInError.Unexpected -> stringResource(R.string.error_unexpected, message ?: stringResource(R.string.error_unknown))
}

@Composable
fun LoadError.message(): String = when (this) {
    LoadError.SessionExpired -> stringResource(R.string.error_session_expired)
    LoadError.ServerUnreachable -> stringResource(R.string.error_load_unreachable)
    LoadError.SecureConnectionFailed -> stringResource(R.string.error_load_secure_connection)
    LoadError.Forbidden -> stringResource(R.string.error_load_forbidden)
    LoadError.NotFound -> stringResource(R.string.error_load_not_found)
    is LoadError.Unexpected -> stringResource(R.string.error_unexpected, message ?: stringResource(R.string.error_unknown))
}
