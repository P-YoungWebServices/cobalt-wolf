package com.pyoungws.bluewolfplayer.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pyoungws.bluewolfplayer.MainActivity
import com.pyoungws.bluewolfplayer.R
import com.pyoungws.bluewolfplayer.shared.admin.ActiveSession
import com.pyoungws.bluewolfplayer.shared.admin.AdminRepository
import com.pyoungws.bluewolfplayer.shared.auth.AuthRepository
import com.pyoungws.bluewolfplayer.shared.auth.SessionState
import com.pyoungws.bluewolfplayer.shared.model.LoadError
import com.pyoungws.bluewolfplayer.shared.model.Outcome
import org.koin.core.context.GlobalContext

/** Home-screen widget listing the server's active Jellyfin sessions. */
class SessionsWidget : GlanceAppWidget() {

    private sealed interface WidgetState {
        data object SignedOut : WidgetState
        data object Failed : WidgetState
        data class Ready(val sessions: List<ActiveSession>) : WidgetState
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState()
        provideContent {
            GlanceTheme {
                Content(context, state)
            }
        }
    }

    private suspend fun loadState(): WidgetState {
        val koin = GlobalContext.get()
        val auth = koin.get<AuthRepository>()
        // The widget can update in a cold process where the app's startup flow never ran.
        if (auth.sessionState.value is SessionState.Unknown) auth.restoreSession()
        if (auth.sessionState.value !is SessionState.SignedIn) return WidgetState.SignedOut
        return when (val outcome = koin.get<AdminRepository>().sessions()) {
            is Outcome.Success -> WidgetState.Ready(outcome.value)
            is Outcome.Failure -> when (outcome.error) {
                LoadError.SessionExpired -> WidgetState.SignedOut
                else -> WidgetState.Failed
            }
        }
    }

    @Composable
    private fun Content(context: Context, state: WidgetState) {
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java))
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(openApp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    context.getString(R.string.widget_sessions_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.defaultWeight())
                Button(
                    text = context.getString(R.string.widget_sessions_refresh),
                    onClick = actionRunCallback<RefreshSessionsAction>(),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            when (state) {
                WidgetState.SignedOut -> Message(context.getString(R.string.widget_sessions_signed_out))
                WidgetState.Failed -> Message(context.getString(R.string.widget_sessions_error))
                is WidgetState.Ready -> SessionList(context, state.sessions)
            }
        }
    }

    @Composable
    private fun SessionList(context: Context, sessions: List<ActiveSession>) {
        if (sessions.isEmpty()) {
            Message(context.getString(R.string.widget_sessions_empty))
            return
        }
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(sessions.map(ActiveSession::toSessionRow)) { row ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        row.title ?: context.getString(R.string.widget_sessions_idle),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
                    )
                    val status = when {
                        row.paused -> context.getString(R.string.widget_sessions_paused)
                        else -> row.progressPercent?.let { "$it%" }
                    }
                    Text(
                        listOfNotNull(row.subtitle, status).joinToString(" · "),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Message(text: String) {
        Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
        }
    }
}

/** Triggers a full widget refresh (and data reload) from the refresh button. */
class RefreshSessionsAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SessionsWidget().update(context, glanceId)
    }
}

class SessionsWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SessionsWidget()
}
