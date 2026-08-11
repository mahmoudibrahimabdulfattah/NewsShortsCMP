package com.mk.newsshorts

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
import com.mk.newsshorts.di.initializeKoin
import com.mk.newsshorts.di.platformModule
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.mk.newsshorts.navigation.ArticleDeepLinks
import com.mk.newsshorts.navigation.DeepLinkBus
import com.mk.newsshorts.notifications.NewsMessagingService
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.android.inject

class NewsShortsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeKoin(platformModules = listOf(platformModule)) {
            androidContext(this@NewsShortsApplication)
        }
    }
}

class MainActivity : ComponentActivity() {

    // The Activity hands links to the bus and never touches the ViewModel.
    private val deepLinkBus: DeepLinkBus by inject()

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
                onShareContent = { title, url, chooserTitle -> shareContent(title, url, chooserTitle) },
                onShowToast = { message -> showToast(message) }
            )
        }
        // Only on a fresh start: after process death the system replays the
        // original intent, which would reopen an article the reader dismissed.
        if (savedInstanceState == null) consumeDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    /**
     * A tapped notification and a `newsshorts://` link arrive the same way, so
     * one parser covers both — and `adb am start` exercises the real path.
     */
    private fun consumeDeepLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        // Clearing it stops the same link firing again when the app resumes.
        intent.data = null
        val link = ArticleDeepLinks.parse(data) ?: return
        deepLinkBus.post(link)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Opens the publisher's page in a Custom Tab, so the reader stays inside
     * the app and one back press returns to the article.
     */
    private fun openUrl(url: String) {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(TOOLBAR_COLOR)
                        .build()
                )
                .build()
                .launchUrl(this, uri)
        } catch (exception: Exception) {
            // No Custom Tabs-capable browser installed.
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (fallbackFailure: Exception) {
                // A device with no browser at all. Uses the platform resource
                // rather than AppStrings because the failure is discovered here,
                // outside composition — so it follows the system language.
                showToast(getString(R.string.unable_to_open_link))
            }
        }
    }

    private fun shareContent(title: String, url: String, chooserTitle: String) {
        val shareIntent: Intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            // No prose around the link: any wording here would be a hardcoded
            // language, and the landing page already explains itself.
            putExtra(Intent.EXTRA_TEXT, "$title\n\n$url")
        }
        startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** Matches the app's dark surface so the tab does not look borrowed. */
        const val TOOLBAR_COLOR: Int = 0xFF1A1A2E.toInt()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
