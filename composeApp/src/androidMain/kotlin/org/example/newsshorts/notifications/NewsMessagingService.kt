package org.example.newsshorts.notifications

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
import org.example.newsshorts.MainActivity
import org.example.newsshorts.R

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

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        ensureChannel(this)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (articleUrl.isNotBlank()) {
                putExtra(EXTRA_ARTICLE_URL, articleUrl)
                // Distinct data per article, so a second push does not reuse
                // the first one's extras.
                setData(Uri.parse(articleUrl))
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            articleUrl.hashCode(),
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
            NotificationManagerCompat.from(this).notify(articleUrl.hashCode(), notification)
        } catch (securityException: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check above and here.
        }
    }

    override fun onNewToken(token: String) {
        // Delivery is by topic, so there is no per-device token to register;
        // re-subscribing keeps a rotated token attached to the same topics.
        NewsTopics.resubscribe(applicationContext)
    }

    companion object {
        const val EXTRA_ARTICLE_URL: String = "article_url"
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
