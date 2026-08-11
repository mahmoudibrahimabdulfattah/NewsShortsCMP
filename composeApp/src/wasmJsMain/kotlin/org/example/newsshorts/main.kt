package org.example.newsshorts

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.example.newsshorts.di.initializeKoin
import org.example.newsshorts.di.platformModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeKoin(platformModules = listOf(platformModule))
    val rootElement = document.getElementById("root") ?: return
    ComposeViewport(rootElement) {
        App(
            onOpenUrl = { url -> openUrlWeb(url) },
            onShareContent = { title, url, _ -> shareContentWeb(title, url) },
            onShowToast = { message -> showToastWeb(message) }
        )
    }
}

private fun openUrlWeb(url: String) {
    window.open(url, "_blank")
}

private fun shareContentWeb(title: String, url: String) {
    copyToClipboardWeb("$title\n\nRead more: $url")
}

private fun copyToClipboardWeb(text: String) {
    window.navigator.clipboard.writeText(text)
}

private fun showToastWeb(message: String) {
    window.alert(message)
}
