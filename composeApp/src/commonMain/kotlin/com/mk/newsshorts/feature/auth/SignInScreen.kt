package com.mk.newsshorts.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mk.newsshorts.auth.AuthFailure
import com.mk.newsshorts.data.local.isPlausibleEmail
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.presentation.ui.components.OverlayTopBar

/**
 * Sign in is one screen the reader can always back out of — "continue as
 * guest" is not a lesser option here, it is the same button style as the
 * others, because everything past this screen already works without an
 * account.
 *
 * There is no password field, deliberately: a password proves the reader can
 * invent one, not that the address is theirs. A link sent to the address and
 * followed back proves exactly the thing that matters, and leaves nothing to
 * store or leak.
 */
@Composable
fun SignInScreen(
    uiState: AuthUiState,
    onEvent: (AuthUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = appStrings()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayTopBar(title = strings.signIn, onBack = { onEvent(AuthUiEvent.Closed) })
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    // The order matters: a link waiting to be claimed is the
                    // only state the reader cannot leave by waiting, so it wins
                    // over the "we sent you mail" screen behind it.
                    uiState.hasUnclaimedLink -> UnclaimedLinkPrompt(
                        isLoading = uiState.authInProgress,
                        errorFailure = uiState.authError,
                        onEvent = onEvent,
                    )
                    uiState.pendingSignInEmail != null -> CheckInboxPrompt(
                        email = uiState.pendingSignInEmail,
                        errorFailure = uiState.authError,
                        onEvent = onEvent,
                    )
                    else -> SignInOptions(
                        isLoading = uiState.authInProgress,
                        errorFailure = uiState.authError,
                        onEvent = onEvent,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onEvent(AuthUiEvent.Closed) }) {
                    Text(
                        text = strings.continueAsGuest,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SignInOptions(
    isLoading: Boolean,
    errorFailure: AuthFailure?,
    onEvent: (AuthUiEvent) -> Unit,
) {
    val strings = appStrings()
    var email: String by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(16.dp))
    CircleIcon(icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) })
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = strings.signInSubtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(28.dp))

    OutlinedButton(
        onClick = { onEvent(AuthUiEvent.SignInWithGoogle) },
        enabled = !isLoading,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text = strings.continueWithGoogle, fontWeight = FontWeight.SemiBold)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = strings.orDivider,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }

    EmailField(
        value = email,
        onValueChange = {
            email = it
            onEvent(AuthUiEvent.DismissAuthError)
        },
        label = strings.emailLabel,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = strings.signInLinkExplainer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))

    FailureText(errorFailure)

    PrimaryButton(
        text = strings.sendSignInLinkButton,
        isLoading = isLoading,
        enabled = !isLoading && isPlausibleEmail(email),
        onClick = { onEvent(AuthUiEvent.SendSignInLink(email)) },
    )
}

@Composable
private fun CheckInboxPrompt(
    email: String,
    errorFailure: AuthFailure?,
    onEvent: (AuthUiEvent) -> Unit,
) {
    val strings = appStrings()

    Spacer(modifier = Modifier.height(16.dp))
    CircleIcon(icon = { Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) })
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = strings.checkInboxTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = strings.checkInboxBody(email),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(16.dp))

    FailureText(errorFailure)

    TextButton(onClick = { onEvent(AuthUiEvent.CancelPendingSignInLink) }) {
        Text(text = strings.useDifferentEmail, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Shown when a link is followed on a device that never asked for one — the
 * address is not carried in the link, so it has to be re-stated here.
 */
@Composable
private fun UnclaimedLinkPrompt(
    isLoading: Boolean,
    errorFailure: AuthFailure?,
    onEvent: (AuthUiEvent) -> Unit,
) {
    val strings = appStrings()
    var email: String by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(16.dp))
    CircleIcon(icon = { Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp)) })
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = strings.confirmLinkEmailTitle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = strings.confirmLinkEmailBody,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(20.dp))

    EmailField(
        value = email,
        onValueChange = {
            email = it
            onEvent(AuthUiEvent.DismissAuthError)
        },
        label = strings.emailLabel,
    )
    Spacer(modifier = Modifier.height(16.dp))

    FailureText(errorFailure)

    PrimaryButton(
        text = strings.confirmLinkEmailButton,
        isLoading = isLoading,
        enabled = !isLoading && isPlausibleEmail(email),
        onClick = { onEvent(AuthUiEvent.SupplyLinkEmail(email)) },
    )
}

@Composable
private fun CircleIcon(icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
        content = { icon() },
    )
}

@Composable
private fun EmailField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FailureText(failure: AuthFailure?) {
    val strings = appStrings()
    failure?.let {
        Text(
            text = strings.authFailure(it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}
