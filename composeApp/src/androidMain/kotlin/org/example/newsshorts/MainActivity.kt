package org.example.newsshorts

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.example.newsshorts.di.initializeKoin
import org.example.newsshorts.di.platformModule
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
                onOpenUrl = { url -> openUrl(url) },
                onShareContent = { title, url -> shareContent(title, url) },
                onShowToast = { message -> showToast(message) }
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (exception: Exception) {
            showToast("Unable to open link")
        }
    }

    private fun shareContent(title: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
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
