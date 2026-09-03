package com.mk.newsshorts.core.model.time

private fun jsDateNow(): Double = js("Date.now()")

actual fun currentTimeMillis(): Long = jsDateNow().toLong()
