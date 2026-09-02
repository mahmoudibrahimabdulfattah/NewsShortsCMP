package com.mk.newsshorts.core.domain.security

import com.mk.newsshorts.core.model.security.DeviceIntegrity

/** Platform-specific; only Android implements real checks today. */
interface DeviceIntegrityInspector {
    fun inspect(): DeviceIntegrity
}

/** Used on targets with nothing meaningful to check. */
object PermissiveDeviceIntegrityInspector : DeviceIntegrityInspector {
    override fun inspect(): DeviceIntegrity = DeviceIntegrity()
}
