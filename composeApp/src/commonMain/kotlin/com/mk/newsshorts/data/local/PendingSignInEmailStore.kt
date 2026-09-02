package com.mk.newsshorts.data.local

/**
 * The address a sign-in link was last sent to.
 *
 * Firebase will not complete a link sign-in without being told which address
 * the link was issued for — the link alone is not enough, deliberately, so that
 * a link intercepted in transit cannot be redeemed by someone who does not also
 * know whose it is. That means the address has to survive the trip out to the
 * mail app and back, which is all this stores.
 *
 * It is cleared as soon as sign-in finishes, and it is not secret: it is the
 * reader's own address, already sitting in their inbox.
 */
interface PendingSignInEmailPersistence {
    fun save(email: String)
    fun load(): String?
    fun clear()
}

class PendingSignInEmailStore(
    private val settingsStorage: SettingsStorage
) : PendingSignInEmailPersistence {
    override fun save(email: String) {
        settingsStorage.putString(KEY_PENDING_EMAIL, email.trim())
    }

    /** Null when the link is opened on a device that never asked for one. */
    override fun load(): String? = settingsStorage.getString(KEY_PENDING_EMAIL, "").ifBlank { null }

    override fun clear() {
        settingsStorage.putString(KEY_PENDING_EMAIL, "")
    }

    private companion object {
        const val KEY_PENDING_EMAIL = "pending_sign_in_email"
    }
}

/**
 * Whether an address is worth sending a link to at all.
 *
 * Deliberately loose: the only authority on whether an address exists is
 * whether the link arrives, and rejecting valid-but-unusual addresses locally
 * would be a bug the reader cannot work around. This exists to keep the send
 * button from firing on an obviously empty or unfinished field.
 */
fun isPlausibleEmail(candidate: String): Boolean {
    val trimmed = candidate.trim()
    if (trimmed.length < MIN_EMAIL_LENGTH || trimmed.any { it.isWhitespace() }) return false
    val atIndex = trimmed.indexOf('@')
    // Exactly one '@', with something either side, and a dotted domain that
    // does not start or end on the dot.
    if (atIndex <= 0 || atIndex != trimmed.lastIndexOf('@')) return false
    val domain = trimmed.substring(atIndex + 1)
    return domain.length >= MIN_DOMAIN_LENGTH &&
        '.' in domain.drop(1).dropLast(1)
}

private const val MIN_EMAIL_LENGTH = 6
private const val MIN_DOMAIN_LENGTH = 3
