package com.focal.android.util

import android.content.Context
import android.net.ConnectivityManager
import com.focal.android.transport.FocalLanNetwork
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    private val VPN_INTERFACE_MARKERS = listOf(
        "tun", "tap", "wg", "ppp", "vpn", "utun", "ipsec", "nordlynx", "tailscale"
    )

    fun isVpnInterfaceName(name: String): Boolean {
        val n = name.lowercase()
        return VPN_INTERFACE_MARKERS.any { n.startsWith(it) || n.contains(it) }
    }

    fun getLocalIpAddress(context: Context? = null): String {
        context?.let { ctx ->
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val lanNetwork = FocalLanNetwork.findLocalLanNetwork(ctx)
            if (cm != null && lanNetwork != null) {
                val props = cm.getLinkProperties(lanNetwork)
                props?.linkAddresses
                    ?.mapNotNull { la ->
                        val addr = la.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) addr else null
                    }
                    ?.firstOrNull { FocalLanNetwork.isPrivateLanAddress(it) }
                    ?.hostAddress
                    ?.let { if (it.isNotBlank()) return it }
            }
        }

        return firstIpv4FromInterfaces(excludeVpn = true)
    }

    fun getLocalIpv4Addresses(): Set<String> {
        val result = linkedSetOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                if (isVpnInterfaceName(iface.name)) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { result.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun isVpnLikelyBlockingLan(context: Context): Boolean =
        FocalLanNetwork.isVpnLikelyBlockingLan(context)

    private fun firstIpv4FromInterfaces(excludeVpn: Boolean): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                if (excludeVpn && isVpnInterfaceName(iface.name)) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && FocalLanNetwork.isPrivateLanAddress(addr)) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ""
    }
}
