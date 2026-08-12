package com.mk.newsshorts.security

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * This policy decides whether a reader is shown a warning, blocked entirely, or
 * left alone — driven by a string in a config file that is edited by hand. The
 * cases that matter are the ones where a mistake in that string turns into a
 * locked-out install base.
 */
class DeviceIntegrityTest {

    private val clean = DeviceIntegrity()
    private val rooted = DeviceIntegrity(isRooted = true)
    private val tampered = DeviceIntegrity(isTampered = true)
    private val emulator = DeviceIntegrity(isEmulator = true)
    private val developerPhone = DeviceIntegrity(isDeveloperOptionsEnabled = true)

    /** Defaults to a release build; the environment policy is out of the way. */
    private fun notice(
        integrity: DeviceIntegrity,
        policy: IntegrityPolicy,
        environmentPolicy: IntegrityPolicy = IntegrityPolicy.ALLOW,
        warningAlreadySeen: Boolean = false,
        enforce: Boolean = true,
    ): SecurityNotice = securityNoticeFor(
        integrity = integrity,
        policy = policy,
        environmentPolicy = environmentPolicy,
        warningAlreadySeen = warningAlreadySeen,
        enforce = enforce,
    )

    @Test
    fun `a clean device is never shown anything`() {
        IntegrityPolicy.entries.forEach { policy ->
            assertEquals(
                SecurityNotice.NONE,
                notice(clean, policy),
                "policy $policy fired on a clean device",
            )
        }
    }

    @Test
    fun `allow detects without interrupting`() {
        assertEquals(
            SecurityNotice.NONE,
            notice(rooted, IntegrityPolicy.ALLOW),
        )
    }

    @Test
    fun `warn shows once and then stays quiet`() {
        assertEquals(
            SecurityNotice.WARNING,
            notice(rooted, IntegrityPolicy.WARN),
        )
        assertEquals(
            SecurityNotice.NONE,
            notice(rooted, IntegrityPolicy.WARN, warningAlreadySeen = true),
        )
    }

    @Test
    fun `block ignores the acknowledgement`() {
        // Dismissing the softer warning must not carry over into an exemption
        // from the hard one when the policy is later raised.
        assertEquals(
            SecurityNotice.BLOCKED,
            notice(rooted, IntegrityPolicy.BLOCK, warningAlreadySeen = true),
        )
    }

    @Test
    fun `a repackaged build is treated like a rooted one`() {
        assertEquals(
            SecurityNotice.BLOCKED,
            notice(tampered, IntegrityPolicy.BLOCK),
        )
    }

    @Test
    fun `a debug build enforces nothing`() {
        // The development build has to run on an emulator, under a debugger, on
        // a developer's own phone. Enforcing there would only block the work.
        listOf(rooted, tampered, emulator, developerPhone).forEach { integrity ->
            assertEquals(
                SecurityNotice.NONE,
                notice(
                    integrity,
                    policy = IntegrityPolicy.BLOCK,
                    environmentPolicy = IntegrityPolicy.BLOCK,
                    enforce = false,
                ),
                "a debug build acted on $integrity",
            )
        }
    }

    @Test
    fun `a release build blocks emulators by default`() {
        assertEquals(
            SecurityNotice.BLOCKED,
            notice(emulator, policy = IntegrityPolicy.WARN, environmentPolicy = IntegrityPolicy.BLOCK),
        )
    }

    @Test
    fun `developer options can be relaxed without touching the root policy`() {
        // The two knobs are independent: blocking rooted installs must not drag
        // every developer-options phone down with it.
        assertEquals(
            SecurityNotice.NONE,
            notice(developerPhone, policy = IntegrityPolicy.BLOCK, environmentPolicy = IntegrityPolicy.ALLOW),
        )
    }

    @Test
    fun `the strictest applicable policy wins`() {
        val both = DeviceIntegrity(isRooted = true, isEmulator = true)
        assertEquals(
            SecurityNotice.BLOCKED,
            notice(both, policy = IntegrityPolicy.WARN, environmentPolicy = IntegrityPolicy.BLOCK),
        )
    }

    @Test
    fun `the environment default is block, and it is the only one that is`() {
        assertEquals(IntegrityPolicy.BLOCK, IntegrityPolicy.fromWire(null, default = IntegrityPolicy.BLOCK))
        assertEquals(IntegrityPolicy.WARN, IntegrityPolicy.fromWire(null))
    }

    @Test
    fun `an unreadable policy falls back to warning, not blocking`() {
        // A typo in the repository variable would otherwise take the app away
        // from every affected install at once, with nothing in the app to say
        // why.
        listOf(null, "", "blok", "BLOCK ME", "true").forEach { value ->
            assertEquals(
                IntegrityPolicy.WARN,
                IntegrityPolicy.fromWire(value),
                "'$value' resolved to something other than warn",
            )
        }
    }

    @Test
    fun `the wire values are matched exactly, whatever their case`() {
        assertEquals(IntegrityPolicy.ALLOW, IntegrityPolicy.fromWire("allow"))
        assertEquals(IntegrityPolicy.BLOCK, IntegrityPolicy.fromWire("BLOCK"))
        assertEquals(IntegrityPolicy.WARN, IntegrityPolicy.fromWire(" warn "))
    }
}
