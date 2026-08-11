package com.mk.newsshorts.data.local

import kotlin.js.Date

actual fun currentTimeMillis(): Long = Date.now().toLong()

