package com.mk.newsshorts.core.model.security

/**
 * What the app could tell about the device it is running on.
 *
 * These are signals, not proof. Every check here runs inside the process it is
 * trying to vouch for, so anyone with the ability to modify the app can also
 * modify the answer — that is the ceiling of on-device attestation, and OWASP
 * MASVS-RESILIENCE says as much: these controls raise cost, they do not stop a
 * determined attacker.
 *
 * They are still worth having. Most tampering is not determined; it is a
 * repackaged APK with ads injected, running on a device where the root is what
 * made the repackaging convenient.
 */
data class DeviceIntegrity(
    val isRooted: Boolean = false,
    val isDebuggerAttached: Boolean = false,
    /** The signing certificate is not the one this build was released with. */
    val isTampered: Boolean = false,
    /** An emulator rather than a phone. */
    val isEmulator: Boolean = false,
    /** Developer options are switched on in system settings. */
    val isDeveloperOptionsEnabled: Boolean = false,
) {
    /** Signals about the app or the OS being modified. */
    val isCompromised: Boolean
        get() = isRooted || isDebuggerAttached || isTampered

    /**
     * Signals about where the app is running. Separate from [isCompromised]
     * because they say something much weaker: an emulator or a developer-mode
     * phone is a normal thing for a normal person to have, and the response to
     * it is a different decision.
     */
    val isDeveloperEnvironment: Boolean
        get() = isEmulator || isDeveloperOptionsEnabled
}

/**
 * What to do about a compromised device. Served by the backend so it can be
 * changed without an app release — the right response depends on who is
 * actually installing the app, which is not knowable before launch.
 */
enum class IntegrityPolicy(val wireValue: String) {
    /** Detect and report, change nothing for the reader. */
    ALLOW("allow"),

    /** Tell the reader once, then let them continue. */
    WARN("warn"),

    /** Refuse to run. */
    BLOCK("block");

    companion object {
        /**
         * An unreadable value falls back to [default] — never to something
         * stricter than what the caller asked for. A typo in a repository
         * variable should not lock an install base out with nothing to say why.
         */
        fun fromWire(value: String?, default: IntegrityPolicy = WARN): IntegrityPolicy =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) } ?: default
    }
}

/** The reader-facing consequence of [integrity] under the active policies. */
enum class SecurityNotice { NONE, WARNING, BLOCKED }

/**
 * Which family of signals fired, so the message can say the true thing.
 *
 * Telling someone their phone is rooted when all they did was leave developer
 * options on is both wrong and insulting, and it is the kind of wrongness that
 * ends up in a one-star review.
 */
enum class SecurityReason { INTEGRITY, ENVIRONMENT }

fun securityReasonFor(integrity: DeviceIntegrity): SecurityReason =
    if (integrity.isCompromised) SecurityReason.INTEGRITY else SecurityReason.ENVIRONMENT

/**
 * Decides what the reader sees.
 *
 * [enforce] is false for a debug build, and it short-circuits everything below.
 * A development build has to run on an emulator, under a debugger, on a phone
 * with developer options open — that is what it is for. Only the shipped build
 * enforces anything, which also means these rules cannot get in the way of the
 * work that produces the shipped build.
 *
 * The two policies are separate because the signals are not comparable. Root or
 * a mismatched signature says something about the app; an emulator says
 * something about the room it is running in.
 */
fun securityNoticeFor(
    integrity: DeviceIntegrity,
    policy: IntegrityPolicy,
    environmentPolicy: IntegrityPolicy,
    warningAlreadySeen: Boolean,
    enforce: Boolean,
): SecurityNotice {
    if (!enforce) return SecurityNotice.NONE

    val outcomes = listOf(
        integrity.isCompromised to policy,
        integrity.isDeveloperEnvironment to environmentPolicy,
    ).mapNotNull { (triggered, active) -> if (triggered) active else null }

    return when {
        outcomes.isEmpty() -> SecurityNotice.NONE
        // The strictest applicable policy wins: one signal saying "block" is
        // not cancelled by another saying "warn".
        outcomes.any { it == IntegrityPolicy.BLOCK } -> SecurityNotice.BLOCKED
        outcomes.none { it == IntegrityPolicy.WARN } -> SecurityNotice.NONE
        warningAlreadySeen -> SecurityNotice.NONE
        else -> SecurityNotice.WARNING
    }
}
