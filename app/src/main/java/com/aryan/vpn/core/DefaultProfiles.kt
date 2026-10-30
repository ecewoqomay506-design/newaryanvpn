package com.aryan.vpn.core

/**
 * No server credentials are shipped in the application. Users add profiles by importing their own
 * VLESS links; this prevents UUIDs and other access secrets from ending up in source or APKs.
 */
object DefaultProfiles {
    fun load(): List<VlessProfile> = emptyList()
}
