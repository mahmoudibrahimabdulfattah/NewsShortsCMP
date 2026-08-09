package org.example.newsshorts.data.local

private fun jsDateNow(): Double = js("Date.now()")

actual fun currentTimeMillis(): Long = jsDateNow().toLong()
