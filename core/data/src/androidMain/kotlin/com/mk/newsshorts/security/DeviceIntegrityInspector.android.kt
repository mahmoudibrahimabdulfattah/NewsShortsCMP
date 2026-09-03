package com.mk.newsshorts.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.provider.Settings
import com.mk.newsshorts.core.domain.security.DeviceIntegrityInspector
import com.mk.newsshorts.core.model.security.DeviceIntegrity
import com.mk.newsshorts.core.model.security.IntegrityPolicy
import java.io.File
import java.security.MessageDigest

/**
 * Android checks for [DeviceIntegrityInspector].
 *
 * Deliberately cheap and side-effect free: filesystem probes, a build flag, and
 * one signature comparison. No process spawning (`which su` shows up in system
 * logs and is trivially hooked), and no scanning of installed packages, which
 * on current Play policy would mean asking for QUERY_ALL_PACKAGES — a permission
 * this app would never be granted for this reason.
 *
 * Every result is a hint. Root can be hidden from all of it; a reader with a
 * custom ROM can trip it while doing nothing wrong. That is why the response
 * lives in [IntegrityPolicy] on the server rather than being hardcoded here.
 */
class AndroidDeviceIntegrityInspector(
    private val context: Context,
    private val expectedSigningSha256: String,
    private val isDebug: Boolean,
) : DeviceIntegrityInspector {

    override fun inspect(): DeviceIntegrity = DeviceIntegrity(
        isRooted = hasRootArtifacts() || hasTestKeys(),
        isDebuggerAttached = isDebuggerAttached(),
        isTampered = isResignedBuild(),
        isEmulator = isEmulator(),
        isDeveloperOptionsEnabled = isDeveloperOptionsEnabled(),
    )

    /**
     * Build properties an emulator image does not bother hiding. A tool built to
     * evade this would evade it; the target is the ordinary case of a release
     * APK pulled off a device and run in an emulator to poke at it.
     */
    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true) ||
            Build.MANUFACTURER.orEmpty().contains("Genymotion", ignoreCase = true) ||
            (Build.BRAND.orEmpty().startsWith("generic") && Build.DEVICE.orEmpty().startsWith("generic")) ||
            hardware in EMULATOR_HARDWARE ||
            product in EMULATOR_PRODUCTS
    }

    /**
     * Developer options being on is a weak signal — plenty of people turn them
     * on once for a screen recorder or a USB transfer and never turn them off.
     * It is reported so the policy can decide; it is not treated as an attack
     * on its own.
     */
    private fun isDeveloperOptionsEnabled(): Boolean = runCatching {
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) != 0
    }.getOrDefault(false)

    /**
     * The su binary and the Magisk working directories. Paths, not packages:
     * a hidden-root setup will pass this, and that is accepted — the target is
     * the ordinary rooted device, not a hardened one.
     */
    private fun hasRootArtifacts(): Boolean = ROOT_PATHS.any { path ->
        runCatching { File(path).exists() }.getOrDefault(false)
    }

    /** A build signed with the public AOSP test keys is not a stock ROM. */
    private fun hasTestKeys(): Boolean = Build.TAGS?.contains("test-keys") == true

    /**
     * Only meaningful in a release build: a debug build is debuggable and
     * usually has a debugger attached, which is the point of it.
     */
    private fun isDebuggerAttached(): Boolean {
        if (isDebug) return false
        val debuggableFlag =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return debuggableFlag || Debug.isDebuggerConnected()
    }

    /**
     * Compares the certificate that signed the running app against the one this
     * build was released with. A repackaged APK — ads injected, feed pointed
     * elsewhere — cannot carry the original signature.
     *
     * Disabled while [expectedSigningSha256] is empty, which it is until there
     * is a release keystore. It must be the Play App Signing certificate, not
     * the upload one: Play re-signs every build it serves.
     */
    private fun isResignedBuild(): Boolean {
        val expected = expectedSigningSha256.filterNot { it == ':' || it == ' ' }
        if (expected.isEmpty()) return false
        val actual = signingCertificateSha256() ?: return false
        return !actual.equals(expected, ignoreCase = true)
    }

    private fun signingCertificateSha256(): String? = runCatching {
        val packageManager = context.packageManager
        val certificate: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            val signing = info.signingInfo ?: return null
            // Rotated keys report history; the current signer is what matters.
            val signatures = if (signing.hasMultipleSigners()) {
                signing.apkContentsSigners
            } else {
                signing.signingCertificateHistory
            }
            signatures?.firstOrNull()?.toByteArray() ?: return null
        } else {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES,
            )
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray() ?: return null
        }
        MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { byte -> "%02X".format(byte) }
    }.getOrNull()

    private companion object {
        val ROOT_PATHS = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/data/adb/magisk",
            "/data/adb/modules",
            "/sbin/.magisk",
        )

        val EMULATOR_HARDWARE = setOf("goldfish", "ranchu", "vbox86", "android_x86")
        val EMULATOR_PRODUCTS = setOf("sdk", "sdk_x86", "sdk_google", "google_sdk", "vbox86p")
    }
}
