package com.mk.newsshorts.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.mk.newsshorts.core.ui.resources.Res
import com.mk.newsshorts.core.ui.resources.logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun appLogoPainter(): Painter = painterResource(Res.drawable.logo)
