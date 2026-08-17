package com.mk.newsshorts.presentation.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.ui.theme.OnImagery

/** Height every button in the app shares. */
private val BUTTON_HEIGHT = 52.dp
private val ICON_SIZE = 20.dp
private val SPINNER_SIZE = 20.dp
private val SPINNER_STROKE = 2.dp

/**
 * Where a button gets its colours from.
 *
 * [Imagery] exists because a button over a photograph cannot use a themed
 * container — it needs to stay legible against whatever the picture happens to
 * be underneath it.
 */
enum class AppButtonTone { Primary, Secondary, Imagery }

/**
 * The one button in the app.
 *
 * Before this there were four of them at 14dp/50dp, 16dp/54dp, 14dp/52dp and
 * 16dp/52dp — the same control, drawn four slightly different ways, so tapping
 * between the feed, the details screen and sign-in meant three different
 * buttons for one action. Corner radius now comes from the shape scale and the
 * height is fixed here.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: AppButtonTone = AppButtonTone.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val container: Color = when (tone) {
        AppButtonTone.Primary -> colorScheme.primary
        AppButtonTone.Secondary -> colorScheme.secondaryContainer
        AppButtonTone.Imagery -> OnImagery.fillStrong
    }
    val content: Color = when (tone) {
        AppButtonTone.Primary -> colorScheme.onPrimary
        AppButtonTone.Secondary -> colorScheme.onSecondaryContainer
        AppButtonTone.Imagery -> OnImagery.content
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(BUTTON_HEIGHT),
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(SPINNER_SIZE),
                color = content,
                strokeWidth = SPINNER_STROKE,
            )
            return@Button
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(ICON_SIZE),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
