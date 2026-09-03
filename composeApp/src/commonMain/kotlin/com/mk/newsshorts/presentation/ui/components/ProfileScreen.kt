package com.mk.newsshorts.presentation.ui.components

import com.mk.newsshorts.feature.saved.SavedArticlesUiEvent
import com.mk.newsshorts.presentation.viewmodel.AppShellUiEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mk.newsshorts.core.model.auth.AuthUser
import com.mk.newsshorts.config.BuildConfig
import com.mk.newsshorts.core.model.NewsArticle
import com.mk.newsshorts.feature.saved.SavedArticlesUiState
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.core.model.article.ArticleOpenOrigin
import com.mk.newsshorts.feature.feed.FeedUiEvent
import com.mk.newsshorts.navigation.Overlay

/**
 * Identity, a saved-articles preview, the entry into Settings, and app info.
 *
 * Everything that used to live here in full — language pickers, the whole
 * saved-articles list — now has its own screen (`SettingsScreen`,
 * `SavedArticlesScreen`), reached through [Overlay.Settings] and
 * [Overlay.SavedArticles]. What is left is a summary, not a settings dump.
 */
@Composable
fun ProfileScreen(
    authUser: AuthUser?,
    savedArticlesUiState: SavedArticlesUiState,
    onShellEvent: (AppShellUiEvent) -> Unit,
    onSavedEvent: (SavedArticlesUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            // Clearance for the bottom bar belongs in contentPadding, not in a
            // fixed modifier: as padding it cropped the list, so the last card
            // could never be scrolled clear of the bar. The system inset is
            // added rather than assumed — it is zero on gesture navigation and
            // a real bar's worth on three-button.
            contentPadding = WindowInsets.navigationBars
                .add(WindowInsets(top = 16.dp, bottom = 16.dp + BottomNavBarHeight))
                .asPaddingValues()
        ) {
            item {
                ProfileHeader(
                    strings = strings,
                    authUser = authUser,
                    onSignInClick = { onShellEvent(AppShellUiEvent.OpenOverlay(Overlay.SignIn)) },
                )
            }
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SavedArticlesPreviewSection(
                    savedArticles = savedArticlesUiState.articles,
                    onArticleClick = { article ->
                        onShellEvent(AppShellUiEvent.OpenArticleDetails(article, ArticleOpenOrigin.SAVED))
                    },
                    onRemoveArticle = { article -> onSavedEvent(SavedArticlesUiEvent.Remove(article)) },
                    onSeeAll = { onShellEvent(AppShellUiEvent.OpenOverlay(Overlay.SavedArticles)) },
                    strings = strings
                )
            }
            item {
                Spacer(modifier = Modifier.height(28.dp))
                SettingsEntryRow(
                    strings = strings,
                    onClick = { onShellEvent(AppShellUiEvent.OpenOverlay(Overlay.Settings)) },
                )
            }
            item {
                Spacer(modifier = Modifier.height(28.dp))
                AppInfoSection(strings = strings, onShellEvent = onShellEvent)
            }
        }
    }
}

/**
 * A guest identity by default; the signed-in account's photo, name and email
 * once one exists. Deliberately not a gate either way — everything below this
 * screen already works without an account, so a guest sees the same layout
 * with a "Sign in" call to action in place of a subtitle.
 */
@Composable
private fun ProfileHeader(
    strings: AppStrings,
    authUser: AuthUser?,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    // Both ends from the azure family. This used to run into
                    // tertiary, which is now crimson and reserved for urgency —
                    // an avatar placeholder is not that.
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary,
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val photoUrl = authUser?.photoUrl
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = strings.profile,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = strings.profile,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = authUser?.displayName ?: authUser?.email ?: strings.guestLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (authUser != null) {
            Text(
                text = authUser.email ?: strings.personalizeExperience,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        } else {
            Text(
                text = strings.signIn,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onSignInClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** At most two, with a way to the rest — a preview, not the whole list. */
@Composable
private fun SavedArticlesPreviewSection(
    savedArticles: List<NewsArticle>,
    onArticleClick: (NewsArticle) -> Unit,
    onRemoveArticle: (NewsArticle) -> Unit,
    onSeeAll: () -> Unit,
    strings: AppStrings,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Bookmark,
            title = strings.savedArticles,
            subtitle = if (savedArticles.isEmpty()) {
                strings.noSavedArticles
            } else {
                strings.savedArticlesCount(savedArticles.size)
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (savedArticles.isEmpty()) {
            EmptySavedArticlesCard(
                strings = strings,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                savedArticles.take(SAVED_PREVIEW_COUNT).forEach { article ->
                    SavedArticleCard(
                        article = article,
                        onClick = { onArticleClick(article) },
                        onRemove = { onRemoveArticle(article) }
                    )
                }
                if (savedArticles.size > SAVED_PREVIEW_COUNT) {
                    SeeAllRow(label = strings.seeAll, onClick = onSeeAll)
                }
            }
        }
    }
}

@Composable
private fun SeeAllRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun SettingsEntryRow(
    strings: AppStrings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Built like `SectionHeader` rather than as a card, so its icon sits on the
    // same 20dp rail as Saved Articles and About. As a card it was inset twice
    // — 16dp for the card, 16dp again inside it — which put the icon 12dp
    // further in than its neighbours and read as an accident. The chevron is
    // what says this row is tappable; the card was never carrying that.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = strings.settingsSubtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AppInfoSection(
    strings: AppStrings,
    onShellEvent: (AppShellUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            icon = Icons.Filled.Info,
            title = strings.about,
            subtitle = strings.aboutDescription
        )
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Labelled, not named twice: `appName` is "News Shorts" in
                // English, so using it as the label put the same words on
                // both sides of the row.
                InfoRow(label = strings.appNameLabel, value = "News Shorts")
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = strings.appVersion, value = BuildConfig.VERSION_NAME)
                Spacer(modifier = Modifier.height(12.dp))
                InfoRow(label = strings.platform, value = "Compose Multiplatform")
                Spacer(modifier = Modifier.height(12.dp))
                // NewsAPI was replaced by the RSS + Gemini backend; the credit
                // had been telling readers the wrong thing since then.
                InfoRow(label = strings.poweredBy, value = "RSS + Gemini")
                Spacer(modifier = Modifier.height(16.dp))
                // Reachable without an account, on purpose: the policy's whole
                // first point is that guests are tracked by nothing, and a
                // reader deciding whether to sign in is exactly who needs it.
                Text(
                    text = strings.privacyPolicy,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShellEvent(AppShellUiEvent.OpenPrivacyPolicy) }
                        .padding(vertical = 4.dp),
                )
                Text(
                    text = strings.openSourceLicenses,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShellEvent(AppShellUiEvent.OpenOverlay(Overlay.Licenses)) }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/** How many bookmarks Profile shows before "See all" takes over. */
private const val SAVED_PREVIEW_COUNT: Int = 2
