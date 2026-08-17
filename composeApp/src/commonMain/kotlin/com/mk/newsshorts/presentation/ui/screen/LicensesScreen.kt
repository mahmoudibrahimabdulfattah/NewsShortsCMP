package com.mk.newsshorts.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.mvi.NewsUiEvent
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.SectionHeader

/**
 * Third-party notices.
 *
 * Not decoration and not a formality: the two bundled families are under the
 * SIL Open Font License, which requires the copyright notice to be distributed
 * with the font software. The files themselves ship inside the app, so this
 * screen is where that notice actually reaches a reader — `licenses/` in the
 * repository covers the source, not the install.
 *
 * The licence text is named rather than reproduced in full. Anything longer
 * belongs in a file, and the OFL asks for the notice and the licence to travel
 * with the fonts, which the repository copies do.
 */
@Composable
fun LicensesScreen(
    onEvent: (NewsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(
                title = strings.openSourceLicenses,
                onBack = { onEvent(NewsUiEvent.CloseOverlay) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Drawn behind the system navigation bar, like every other
                // scrolling screen here.
                contentPadding = WindowInsets.navigationBars
                    .add(WindowInsets(top = 8.dp, bottom = 24.dp))
                    .asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SectionHeader(
                        icon = Icons.Filled.TextFields,
                        title = strings.licenseFontsHeading,
                        subtitle = strings.openSourceLicensesSubtitle,
                    )
                }
                item {
                    LicenseCard(
                        notice = strings.licenseNoticePoppins,
                        license = strings.licenseName,
                    )
                }
                item {
                    LicenseCard(
                        notice = strings.licenseNoticeTajawal,
                        license = strings.licenseName,
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseCard(
    notice: String,
    license: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = license,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
