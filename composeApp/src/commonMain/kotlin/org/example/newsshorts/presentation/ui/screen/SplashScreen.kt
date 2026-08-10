package org.example.newsshorts.presentation.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.example.newsshorts.presentation.localization.appStrings

private const val ANIMATION_DURATION_MS: Int = 400
private const val TEXT_ANIMATION_DURATION_MS: Int = 300

@Composable
fun SplashScreen(
    logoPainter: Painter,
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            val mainAnimation = async {
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = ANIMATION_DURATION_MS,
                        easing = EaseOutBack
                    )
                )
            }
            awaitAll(mainAnimation)
        }
        onSplashComplete()
    }
    val scale: Float = 0.5f + (animationProgress.value * 0.5f)
    val alpha: Float = animationProgress.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1B2A),
                        Color(0xFF1B263B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = logoPainter,
                contentDescription = "News Shorts Logo",
                modifier = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .alpha(alpha)
            )
            Text(
                text = appStrings().appName,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = 0.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .alpha(alpha)
            )
            Text(
                text = appStrings().appTagline,
                style = TextStyle(
                    fontSize = 16.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFFB0B8C1),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .alpha(alpha)
            )
        }
    }
}
