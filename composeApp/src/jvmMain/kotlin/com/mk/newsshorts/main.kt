package com.mk.newsshorts

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mk.newsshorts.di.initializeKoin
import com.mk.newsshorts.di.platformModule
import java.awt.Desktop
import java.net.URI

fun main() {
    initializeKoin(platformModules = listOf(platformModule))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "News Shorts",
            icon = painterResource("app_icon.png"),
            state = rememberWindowState(width = 420.dp, height = 900.dp)
        ) {
            App(
                onOpenUrl = { url, _ -> openUrlDesktop(url) },
                onShareContent = { title, url, _ -> copyToClipboardDesktop("$title\n\n$url") },
                onShowToast = { /* Desktop notification could be added */ }
            )
        }
    }
}

private fun openUrlDesktop(url: String) {
    try {
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun copyToClipboardDesktop(text: String) {
    try {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val selection = java.awt.datatransfer.StringSelection(text)
        clipboard.setContents(selection, selection)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
