package com.nuvio.app.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Reorders DNS results to prefer IPv4 first. This helps avoid broken IPv6 routes
 * on some emulator and network setups.
 * Also provides dynamic fallback for unresolvable subdomains sharing the same root domain.
 */
class IPv4FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            delegate.lookup(hostname)
        } catch (e: Exception) {
            val fallback = DynamicHostFallback.getFallbackHost(hostname)
            if (!fallback.isNullOrBlank() && !fallback.equals(hostname, ignoreCase = true)) {
                delegate.lookup(fallback)
            } else {
                throw e
            }
        }
        return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
    }
}
