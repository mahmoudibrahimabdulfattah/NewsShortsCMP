package com.mk.newsshorts.feature.appgate

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mk.newsshorts.presentation.localization.appStrings
import com.mk.newsshorts.security.SecurityReason

/**
 * Shown once when the device looks rooted or the app looks repackaged, under
 * the "warn" policy.
 *
 * Deliberately not alarming and deliberately dismissible. A rooted phone is a
 * choice its owner made, usually knowingly; the honest thing is to say what
 * that costs here and then get out of the way. Wording it as an accusation
 * would only train readers to tap past security messages.
 */
@Composable
fun SecurityWarningDialog(reason: SecurityReason, onDismiss: () -> Unit) {
    val strings = appStrings()
    val isEnvironment = reason == SecurityReason.ENVIRONMENT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEnvironment) {
                    strings.environmentWarningTitle
                } else {
                    strings.securityWarningTitle
                }
            )
        },
        text = {
            Text(
                text = if (isEnvironment) {
                    strings.environmentWarningMessage
                } else {
                    strings.securityWarningMessage
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                // Carrying on past a security warning is the risky choice here,
                // so it takes the error colour rather than a literal that only
                // resembled one.
                Text(text = strings.continueAnyway, color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
