package com.mk.newsshorts.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.mk.newsshorts.presentation.ui.theme.UnreadMark
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.domain.model.PublishedTimestamp
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar
import com.mk.newsshorts.presentation.ui.components.formatPublishedTime

/**
 * What the reader was sent, whether or not their phone showed it.
 *
 * The list comes from the backend rather than from notifications this device
 * happened to receive — see `NotificationInboxClient` for why that difference
 * decides whether the screen is any use.
 *
 * The unread mark is passed in rather than derived here: opening this screen
 * does not clear a mark, so the set has to be the one the ViewModel holds,
 * changed only by opening a notification or by the action in the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationInboxScreen(
    uiState: InboxUiState,
    onEvent: (InboxUiEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = appStrings()
    // Local to this screen rather than hoisted: an undo is only offered while
    // the reader is still looking at the list they swiped in, and it dies with
    // the screen on purpose.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(
                title = strings.notificationInbox,
                onBack = onClose,
                action = {
                    // Offered only when it would do something. A control that is
                    // always there and usually inert teaches the reader to stop
                    // looking at it.
                    if (uiState.unreadIds.isNotEmpty()) {
                        TextButton(onClick = { onEvent(InboxUiEvent.MarkAllRead) }) {
                            Text(
                                text = strings.markAllNotificationsRead,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
            // Wraps the empty state too. A reader who opens an inbox that says
            // nothing is here is exactly the one who will pull to check.
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { onEvent(InboxUiEvent.Refresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (uiState.visibleNotifications.isEmpty()) {
                    // Scrollable even when it holds one card, or there is no
                    // gesture for the pull to attach to.
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item {
                            EmptyInbox(
                                title = strings.notificationInboxEmptyTitle,
                                body = strings.notificationInboxEmptyBody,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // The app draws behind the system navigation bar, so the
                        // last card needs the inset added or it sits under it on
                        // three-button navigation.
                        contentPadding = WindowInsets.navigationBars
                            .add(WindowInsets(left = 16.dp, top = 12.dp, right = 16.dp, bottom = 12.dp))
                            .asPaddingValues(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.visibleNotifications, key = { it.sentAt }) { notification ->
                            SwipeableNotificationRow(
                                notification = notification,
                                isUnread = notification.sentAt in uiState.unreadIds,
                                onOpen = {
                                    onEvent(InboxUiEvent.OpenNotification(notification.deepLink))
                                },
                                onDismiss = {
                                    onEvent(InboxUiEvent.DismissNotification(notification.articleUrl))
                                    scope.launch {
                                        // Only one at a time: a reader clearing
                                        // several in a row wants the last one
                                        // offered back, not a queue of them.
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = strings.notificationDismissed,
                                            actionLabel = strings.undo,
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onEvent(
                                                InboxUiEvent.RestoreNotification(
                                                    notification.articleUrl
                                                )
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp),
        ) { data ->
            // Colours named rather than defaulted. The theme defines the roles
            // it actually uses and leaves the `inverse*` family at Material's
            // baseline, so the undo action came out lavender — the one purple
            // in an app built from two hues.
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium,
            )
        }
    }
}

/**
 * A row that can be swiped away, in one direction.
 *
 * `EndToStart` and nothing else. Compose resolves the two directions against the
 * layout direction, which is what makes one constant right here: in Arabic the
 * end edge is on the left, so this is a swipe left-to-right, and in English it
 * is the right-to-left swipe every list on the platform uses. One rule, mirrored
 * for free.
 *
 * The other direction is left inert on purpose. A row that can be flung away
 * whichever way the thumb happens to move is a row that gets deleted by a
 * mis-aimed scroll, and there is nothing on the other side worth a second
 * action.
 *
 * The confirm lambda reports the swipe and then returns false, so the box
 * springs back and never rests in a dismissed position. The row still vanishes
 * — the list drops it the moment the state updates. Letting the box settle
 * instead left it stuck: the list is keyed by `sentAt`, so a row brought back
 * by undo restored the saved swipe state along with it and redrew itself
 * permanently swiped aside, showing the delete background and no content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationRow(
    notification: InboxNotification,
    isUnread: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
            false
        },
        // Half the row. A shorter threshold turns a hesitant scroll into a
        // deletion; a longer one makes the gesture feel like it is refusing.
        positionalThreshold = { distance -> distance * 0.5f },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        // The other way does not drag at all, rather than dragging and springing
        // back: a row that moves under the thumb is promising something, and
        // this one has nothing to give.
        enableDismissFromStartToEnd = false,
        backgroundContent = { DismissBackground(dismissState.dismissDirection) },
    ) {
        NotificationRow(notification = notification, isUnread = isUnread, onClick = onOpen)
    }
}

/**
 * What sits under a row being swiped.
 *
 * The icon follows the drag rather than sitting on both sides at once, so the
 * reader sees the action arriving from the edge their thumb is pulling towards.
 * Crimson because this is the one destructive thing on the screen — the same
 * reason `error` exists in the palette and the same restraint about using it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(direction: SwipeToDismissBoxValue) {
    // Nothing at all at rest. The row card is translucent, so a background left
    // painted underneath it tinted every row pink whether or not anyone was
    // swiping — the whole list looked like it was in an error state.
    //
    // Only the one direction that dismisses, for the same reason: the other way
    // does nothing, so it must not look like it is about to.
    if (direction != SwipeToDismissBoxValue.EndToStart) return

    Box(
        // The edge the row is being pulled towards, in either reading direction.
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = appStrings().deleteNotification,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun NotificationRow(
    notification: InboxNotification,
    isUnread: Boolean,
    onClick: () -> Unit,
) {
    val strings = appStrings()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = if (isUnread) 0.55f else 0.3f)
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // A dot rather than bold text: the row's own weight is already
            // carrying the headline, and the mark has to read the same in both
            // reading directions.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    // The same crimson as the badge on the bell, and the same
                    // in both themes: one mark should not read as two states.
                    .background(if (isUnread) UnreadMark else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notification.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notification.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    // The same formatter the cards use, so a date reads the
                    // same wherever it appears.
                    text = formatPublishedTime(
                        PublishedTimestamp(notification.sentAt),
                        strings.monthNames,
                        strings.recently,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun EmptyInbox(title: String, body: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Centred explicitly: the column centres the block, not the lines
            // inside it, so a wrapped sentence fell back to the reading
            // direction and sat under a centred heading looking misaligned.
            //
            // And leading tightened from the theme's. Arabic body text carries
            // 1.4x for long-form reading, which is right for an article and
            // wrong for two centred lines in a card: one sentence broke into
            // halves further apart than the sentence was from its own heading,
            // so it read as two unrelated fragments. Derived from the font size
            // rather than fixed, so it still follows the reader's text scale.
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.35f,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
