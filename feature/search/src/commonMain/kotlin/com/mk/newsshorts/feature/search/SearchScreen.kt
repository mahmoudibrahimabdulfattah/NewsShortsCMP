package com.mk.newsshorts.feature.search

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.presentation.localization.AppStrings
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.ui.components.SavedArticleCard

/**
 * Search over the corpus the backend publishes for the reader's news language.
 *
 * Four states, and which one shows is decided in the ViewModel rather than
 * inferred from an empty list here — "nothing typed yet" and "nothing found"
 * look identical from the list alone, and they are not the same screen.
 */
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()
    val focusRequester = remember { FocusRequester() }
    // The reader opened search to type something; landing on the field with the
    // keyboard already up saves them a tap every single time.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(
                query = uiState.query,
                strings = strings,
                focusRequester = focusRequester,
                onQueryChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
                onSubmit = { onEvent(SearchUiEvent.Submitted(uiState.query)) },
                onClear = { onEvent(SearchUiEvent.QueryCleared) },
                onBack = { onEvent(SearchUiEvent.Closed) },
            )
            when {
                // Only when there is nothing to show underneath it: replacing a
                // result list with a spinner on every keystroke makes the whole
                // screen flicker while someone types.
                uiState.isSearching && uiState.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                uiState.hasFailed -> {
                    SearchMessage(
                        icon = Icons.Filled.WifiOff,
                        title = strings.searchFailedTitle,
                        body = strings.searchFailedBody,
                        actionLabel = strings.tryAgain,
                        onAction = { onEvent(SearchUiEvent.Submitted(uiState.query)) },
                    )
                }
                uiState.results.isNotEmpty() -> {
                    SearchResults(uiState = uiState, strings = strings, onEvent = onEvent)
                }
                uiState.hasNoResults -> {
                    SearchMessage(
                        icon = Icons.Filled.SearchOff,
                        title = strings.searchNoResultsTitle,
                        body = strings.searchNoResultsBody(uiState.query.trim()),
                    )
                }
                uiState.recentSearches.isNotEmpty() -> {
                    RecentSearches(
                        queries = uiState.recentSearches,
                        strings = strings,
                        onSelect = { onEvent(SearchUiEvent.Submitted(it)) },
                        onRemove = { onEvent(SearchUiEvent.RecentSearchRemoved(it)) },
                        onClearAll = { onEvent(SearchUiEvent.RecentSearchesCleared) },
                    )
                }
                else -> {
                    SearchMessage(
                        icon = Icons.Filled.Search,
                        title = strings.searchEmptyTitle,
                        body = strings.searchEmptyBody,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    strings: AppStrings,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                // Auto-mirrored so the arrow points the right way in Arabic.
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.back,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = strings.searchHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.clearSearchQuery,
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            shape = MaterialTheme.shapes.small,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                // The container is the field's outline; a second underline
                // under a rounded box reads as a rendering mistake.
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    strings: AppStrings,
    onEvent: (SearchUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The app draws behind the system navigation bar, so the last result
        // needs the inset added or it sits under it on three-button navigation.
        contentPadding = WindowInsets.navigationBars
            .add(WindowInsets(left = 16.dp, top = 12.dp, right = 16.dp, bottom = 12.dp))
            .asPaddingValues(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = strings.searchResultsCount(uiState.results.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        items(uiState.results, key = { it.articleUrl.value }) { article ->
            SavedArticleCard(
                article = article,
                onClick = {
                    onEvent(SearchUiEvent.ResultOpened(article))
                },
                // A result is not a bookmark — there is nothing here to delete.
                onRemove = null,
            )
        }
    }
}

@Composable
private fun RecentSearches(
    queries: List<String>,
    strings: AppStrings,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = strings.recentSearches,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings.clearRecentSearches,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(queries, key = { it }) { query ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(query) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = strings.removeRecentSearch,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onRemove(query) }
                            .padding(4.dp)
                            .size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * The empty, no-results and unavailable states. One composable for all three:
 * they differ in wording and icon, and giving each its own layout is how three
 * screens that should feel like one stop matching.
 */
@Composable
private fun SearchMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable(onClick = onAction)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
