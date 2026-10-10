package com.focal.android.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket

/**
 * Routes FOCL TCP to the local Wi‑Fi/Ethernet interface when a VPN is the system default.
 * Without this, [Socket.connect] to a LAN IP often fails while VPN is active.
 */
object FocalLanNetwork {

    fun isPrivateLanHost(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val addr = InetAddress.getByName(trimmed)
            addr is Inet4Address && isPrivateLanAddress(addr)
        } catch (_: Exception) {
            false
        }
    }

    fun isPrivateLanAddress(addr: Inet4Address): Boolean {
        val bytes = addr.address
        if (bytes.size != 4) return false
        val b0 = bytes[0].toInt() and 0xff
        val b1 = bytes[1].toInt() and 0xff
        return when {
            b0 == 10 -> true
            b0 == 172 && b1 in 16..31 -> true
            b0 == 192 && b1 == 168 -> true
            b0 == 169 && b1 == 254 -> true
            else -> false
        }
    }

    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun hasLocalLanTransport(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) {
                return true
            }
        }
        return false
    }

    /** VPN is default route but Wi‑Fi/Ethernet is still up — LAN streaming often breaks until VPN is off or split-tunnel is enabled. */
    fun isVpnLikelyBlockingLan(context: Context): Boolean {
        return isVpnActive(context) && hasLocalLanTransport(context)
    }

    fun findLocalLanNetwork(context: Context): Network? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        var wifi: Network? = null
        var ethernet: Network? = null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifi = network
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ethernet = network
            }
        }
        return wifi ?: ethernet
    }

    fun connectTcp(
        context: Context?,
        host: String,
        port: Int,
        useTls: Boolean,
        timeoutMs: Int
    ): Socket {
        val bound = context?.let { ctx ->
            if (!isPrivateLanHost(host)) return@let null
            findLocalLanNetwork(ctx)
        }

        val socket = if (bound != null) {
            bound.socketFactory.createSocket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), timeoutMs)
            }
        } else {
            Socket().apply {
                tcpNoDelay = true
                connect(InetSocketAddress(host, port), timeoutMs)
            }
        }

        if (!useTls) return socket

        val ssl = FocalLanTls.clientSocketFactory().createSocket(
            socket,
            host,
            port,
            true
        ) as SSLSocket
        ssl.tcpNoDelay = true
        return ssl
    }
}
