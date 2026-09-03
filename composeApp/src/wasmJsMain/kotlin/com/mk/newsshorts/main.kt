package com.mk.newsshorts

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import com.mk.newsshorts.di.initializeKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initializeKoin(platformModules = emptyList())
    val rootElement = document.getElementById("root") ?: return
    ComposeViewport(rootElement) {
        App(
            onOpenUrl = { url, _ -> openUrlWeb(url) },
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
