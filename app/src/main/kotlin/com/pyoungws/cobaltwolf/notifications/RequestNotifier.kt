package com.pyoungws.cobaltwolf.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pyoungws.cobaltwolf.MainActivity
import com.pyoungws.cobaltwolf.R
import com.pyoungws.cobaltwolf.shared.seerr.RequestEvent
import com.pyoungws.cobaltwolf.shared.seerr.RequestWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Posts a local notification for each [RequestEvent] the watcher reports: new pending requests
 * for admins, approvals and availability for the user's own requests. Events only arrive while
 * the process is alive; there is no push channel.
 */
class RequestNotifier(
    private val context: Context,
    private val watcher: RequestWatcher,
    private val scope: CoroutineScope,
) {
    fun start() {
        createChannel()
        watcher.start()
        scope.launch { watcher.events.collect(::post) }
    }

    private fun post(event: RequestEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = event.request.title ?: "#${event.request.tmdbId}"
        val (contentTitle, contentText) = when (event) {
            is RequestEvent.NewPending ->
                context.getString(R.string.notification_new_request) to
                    (event.request.requestedByName?.let {
                        context.getString(R.string.notification_new_request_by, it, title)
                    } ?: context.getString(R.string.notification_new_request_unknown, title))
            is RequestEvent.Approved ->
                context.getString(R.string.notification_request_approved) to
                    context.getString(R.string.notification_request_approved_body, title)
            is RequestEvent.Available ->
                context.getString(R.string.notification_request_available) to
                    context.getString(R.string.notification_request_available_body, title)
        }

        val intent = PendingIntent.getActivity(
            context,
            event.request.id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(event.request.id, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_requests),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notification_channel_requests_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "requests"
    }
}
