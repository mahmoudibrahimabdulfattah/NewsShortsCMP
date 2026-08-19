package com.mk.newsshorts.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mk.newsshorts.MainActivity
import com.mk.newsshorts.data.local.NotificationPreferenceKeys
import com.mk.newsshorts.data.local.SettingsStorage
import com.mk.newsshorts.navigation.ArticleDeepLinks
import com.mk.newsshorts.navigation.NotificationBus
import com.mk.newsshorts.presentation.mvi.InboxNotification
import com.mk.newsshorts.R
import org.koin.mp.KoinPlatformTools

/**
 * Receives breaking-news pushes.
 *
 * Messages are sent data-only so this always builds the notification itself —
 * a `notification` payload would be rendered by the system while the app is in
 * the background, which skips the channel and the tap target below.
 */
class NewsMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = message.data
        val title = payload["title"]?.takeUnless { it.isBlank() } ?: return
        val body = payload["body"].orEmpty()
        val articleUrl = payload["url"].orEmpty()
        // Servers that predate the deepLink key still deliver a usable tap.
        val deepLink = payload["deepLink"]?.takeUnless { it.isBlank() }
            ?: fallbackDeepLink(title, articleUrl)
        // A reminder carries no article, so the title is the only thing that
        // identifies it — without this every one of them would reuse id 0.
        val notificationId = (deepLink ?: title).hashCode()

        // Announced before either gate below, and deliberately so. The inbox
        // lists what a reader was sent, not what their phone chose to show —
        // the published list makes no distinction either, and a reader who has
        // muted a tier is exactly the one who opens the inbox to catch up.
        announce(title, body, deepLink)

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        if (!isAllowedByInAppSettings(payload["tier"])) return
        ensureChannel(this)

        // ACTION_VIEW carrying the deep link, but with an explicit component so
        // no other app can intercept the tap and no chooser appears. The app
        // then handles a notification and a shared link through one parser.
        val intent = Intent(
            Intent.ACTION_VIEW,
            deepLink?.let { Uri.parse(it) },
            this,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
        } catch (securityException: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here.
        }
    }

    override fun onNewToken(token: String) {
        // Delivery is by topic, so there is no per-device token to register;
        // re-subscribing keeps a rotated token attached to the same topics.
        NewsTopics.resubscribe(applicationContext)
    }

    /**
     * Hands the notification to whoever is listening inside the app.
     *
     * The backend publishes the same list, but through a static deploy that
     * lands minutes after the push — long enough for a reader who taps straight
     * in to look for what they were just shown and not find it. This closes that
     * gap; the published list then agrees with it.
     *
     * Reminders are left out: they carry no article, so an inbox row for one
     * would open nothing.
     *
     * Koin is reached through the global registry rather than injected, for the
     * same reason [isAllowedByInAppSettings] reads storage directly — the system
     * instantiates this class, so there is no constructor to inject into. A
     * process where Koin has not started yet is a process with no ViewModel
     * listening, so failing quietly is the whole of the handling needed.
     */
    private fun announce(title: String, body: String, deepLink: String?) {
        val link = deepLink ?: return
        runCatching {
            KoinPlatformTools.defaultContext().get().get<NotificationBus>().post(
                InboxNotification(
                    sentAt = System.currentTimeMillis(),
                    title = title,
                    body = body,
                    deepLink = link,
                )
            )
        }
    }

    /**
     * The in-app on/off switches, checked alongside the OS-level one at [39].
     *
     * This service is instantiated by the system, not by Koin, so it reads
     * `SettingsStorage` directly rather than taking it as a constructor
     * argument — there is nothing to inject it. The key names come from
     * [NotificationPreferenceKeys] so this and the Settings screen can never
     * name the same switch two different things.
     *
     * An unrecognised tier is allowed through: a server that starts sending a
     * new tier this build does not know about should not go silent for it.
     */
    private fun isAllowedByInAppSettings(tier: String?): Boolean {
        val settingsStorage = SettingsStorage(applicationContext)
        fun flag(key: String) =
            settingsStorage.getString(key, NotificationPreferenceKeys.DEFAULT_ENABLED) == "true"

        val masterEnabled = flag(NotificationPreferenceKeys.ENABLED)
        val tierKey = NotificationPreferenceKeys.keyForWireTier(tier.orEmpty())
        val tierEnabled = tierKey?.let { flag(it) }
        return NotificationPreferenceKeys.isAllowed(masterEnabled, tierEnabled)
    }

    /** Enough for the details screen to render a headline and a source link. */
    private fun fallbackDeepLink(title: String, articleUrl: String): String? {
        if (articleUrl.isBlank()) return null
        return "${ArticleDeepLinks.SCHEME}://${ArticleDeepLinks.HOST}" +
            "?url=${Uri.encode(articleUrl)}&title=${Uri.encode(title)}"
    }

    companion object {
        const val CHANNEL_ID: String = "breaking_news"

        /** Channels are only a concept from API 26; below that this is a no-op. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_breaking_news),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_breaking_news_description)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
