package com.mk.newsshorts.core.model.time

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date.now().toLong()
