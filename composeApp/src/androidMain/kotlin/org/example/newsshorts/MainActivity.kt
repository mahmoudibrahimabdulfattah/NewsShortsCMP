package org.example.newsshorts

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import org.example.newsshorts.di.initializeKoin
import org.example.newsshorts.di.platformModule
import org.example.newsshorts.notifications.NewsMessagingService
import org.koin.android.ext.koin.androidContext

class NewsShortsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeKoin(platformModules = listOf(platformModule)) {
            androidContext(this@NewsShortsApplication)
        }
    }
}

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is fine — the app works without notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        NewsMessagingService.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        setContent {
            App(
                onOpenUrl = { url -> openUrl(url) },
                onShareContent = { title, url -> shareContent(title, url) },
                onShowToast = { message -> showToast(message) }
            )
        }
        openArticleFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openArticleFrom(intent)
    }

    /** A tapped push carries the article it was about. */
    private fun openArticleFrom(intent: Intent?) {
        val url = intent?.getStringExtra(NewsMessagingService.EXTRA_ARTICLE_URL) ?: return
        intent.removeExtra(NewsMessagingService.EXTRA_ARTICLE_URL)
        openUrl(url)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openUrl(url: String) {
        try {
            val intent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (exception: Exception) {
            showToast("Unable to open link")
        }
    }

    private fun shareContent(title: String, url: String) {
        val shareIntent: Intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n\nRead more: $url")
        }
        startActivity(Intent.createChooser(shareIntent, "Share Article"))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
