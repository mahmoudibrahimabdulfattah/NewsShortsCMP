package com.mk.newsshorts

import androidx.compose.ui.window.ComposeUIViewController
import com.mk.newsshorts.di.initializeKoin
import com.mk.newsshorts.di.platformModule
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

private var isKoinInitialized: Boolean = false

fun MainViewController() = ComposeUIViewController(
    configure = {
        initializeKoinOnce()
    }
) {
    App(
        onOpenUrl = { url, _ -> openUrlIOS(url) },
        onShareContent = { title, url, _ -> shareContentIOS(title, url) },
        onShowToast = { /* iOS doesn't have toast, could show alert */ }
    )
}

private fun initializeKoinOnce() {
    if (!isKoinInitialized) {
        initializeKoin(platformModules = listOf(platformModule))
        isKoinInitialized = true
    }
}

private fun openUrlIOS(urlString: String) {
    val url: NSURL = NSURL.URLWithString(urlString) ?: return
    val application: UIApplication = UIApplication.sharedApplication
    if (application.canOpenURL(url)) {
        application.openURL(url, emptyMap<Any?, Any?>()) { success ->
            if (!success) {
                println("Failed to open URL: $urlString")
            }
        }
    } else {
        println("Cannot open URL: $urlString")
    }
}

private fun shareContentIOS(title: String, url: String) {
    val items: List<Any> = listOf("$title\n\nRead more: $url")
    val activityVC: UIActivityViewController = UIActivityViewController(
        activityItems = items,
        applicationActivities = null
    )
    val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    rootVC.presentViewController(activityVC, animated = true, completion = null)
}
