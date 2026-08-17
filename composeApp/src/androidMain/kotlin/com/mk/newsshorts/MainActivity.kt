package com.mk.newsshorts

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.mk.newsshorts.navigation.SignInLinkBus
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
    private val signInLinkBus: SignInLinkBus by inject()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining is fine — the app works without notifications. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent both ways, and dark to begin with: the first thing drawn
        // is the branded splash, which is dark in either theme. Compose takes
        // it from here and re-runs this per screen — see SystemBarAppearance.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        NewsMessagingService.ensureChannel(this)
        // No longer requested here unconditionally: asking before a single
        // headline is on screen is where opt-in rates go to die. It now fires
        // from the app itself — when the reader turns notifications on in
        // Settings, or once after they've read a few stories.
        setContent {
            App(
                onOpenUrl = { url, isDark -> openUrl(url, isDark) },
                onShareContent = { title, url, chooserTitle -> shareContent(title, url, chooserTitle) },
                onShowToast = { message -> showToast(message) },
                onRequestNotificationPermission = { requestNotificationPermissionIfNeeded() }
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
     *
     * A followed sign-in link arrives here too, and is deliberately checked
     * second: only Firebase can say whether a link is one of its own, and
     * asking it about every article link would be a pointless round trip.
     */
    private fun consumeDeepLink(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        // Clearing it stops the same link firing again when the app resumes.
        intent.data = null
        val articleLink = ArticleDeepLinks.parse(data)
        if (articleLink != null) {
            deepLinkBus.post(articleLink)
            return
        }
        signInLinkBus.post(data)
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
     *
     * The tab is told which scheme to use rather than left on
     * `COLOR_SCHEME_SYSTEM`: the browser is a different app and would follow
     * the *phone's* dark-mode setting, which is the same mismatch the launch
     * window had — a light app handing off to a dark toolbar.
     *
     * @param isDark the reader's resolved Appearance, from `App`.
     */
    private fun openUrl(url: String, isDark: Boolean) {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setColorScheme(
                    if (isDark) CustomTabsIntent.COLOR_SCHEME_DARK
                    else CustomTabsIntent.COLOR_SCHEME_LIGHT
                )
                // Both are supplied, not just the active one: the tab keeps the
                // params per scheme, and a half-filled builder leaves the other
                // side on the browser's default grey.
                .setDefaultColorSchemeParams(schemeParams(LIGHT_SURFACE, LIGHT_BACKGROUND))
                .setColorSchemeParams(
                    CustomTabsIntent.COLOR_SCHEME_DARK,
                    schemeParams(DARK_SURFACE, DARK_BACKGROUND),
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

    /** Toolbar takes the app's surface, the bars below it the background. */
    private fun schemeParams(toolbar: Int, navigationBar: Int): CustomTabColorSchemeParams =
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbar)
            .setNavigationBarColor(navigationBar)
            .build()

    private companion object {
        // Mirrors NewsShortsTheme.kt. Kept as literals because the schemes are
        // Compose Colors in common code and this runs outside composition; any
        // drift shows up as a toolbar that does not match the screen it came
        // from, which is what the old single hardcoded value already looked
        // like once the palette moved off it.
        const val LIGHT_SURFACE: Int = 0xFFFFFFFF.toInt()
        const val LIGHT_BACKGROUND: Int = 0xFFF5F8FB.toInt()
        const val DARK_SURFACE: Int = 0xFF0F2234.toInt()
        const val DARK_BACKGROUND: Int = 0xFF081726.toInt()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
