package com.focal.android.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * Manages mDNS / DNS-SD (Network Service Discovery) auto-discovery.
 * Advertises Focal over the local Wi-Fi network using the standard `_focal._tcp` service type,
 * allowing desktop clients, OBS plugins, and browsers to discover the phone automatically
 * without requiring the user to type in an IP address.
 */
object FocalDiscoveryManager {

    private const val TAG = "FocalDiscovery"
    const val SERVICE_TYPE = "_focal._tcp."

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    var isRegistered: Boolean = false
        private set

    var registeredServiceName: String? = null
        private set

    fun registerService(context: Context, port: Int = 8080, pin: String = PairingManager.currentPin) {
        if (isRegistered) {
            unregisterService()
        }

        try {
            val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager

            val cleanModel = Build.MODEL.replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            val desiredName = "Focal-$cleanModel"

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = desiredName
                serviceType = SERVICE_TYPE
                this.port = port

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAttribute("pin", pin)
                    setAttribute("path", "/stream.h264")
                    setAttribute("mjpeg", "/stream.mjpg")
                    setAttribute("port", port.toString())
                    setAttribute("model", Build.MODEL)
                    setAttribute("version", "1.0")
                }
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(registeredInfo: NsdServiceInfo) {
                    registeredServiceName = registeredInfo.serviceName
                    isRegistered = true
                    Log.i(TAG, "mDNS auto-discovery service registered: ${registeredInfo.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "mDNS auto-discovery registration failed: error=$errorCode")
                    isRegistered = false
                    registeredServiceName = null
                }

                override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                    Log.i(TAG, "mDNS auto-discovery service unregistered")
                    isRegistered = false
                    registeredServiceName = null
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS auto-discovery unregistration failed: error=$errorCode")
                    isRegistered = false
                    registeredServiceName = null
                }
            }

            registrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start mDNS auto-discovery service: ${e.message}")
            isRegistered = false
            registeredServiceName = null
        }
    }

    fun unregisterService() {
        val manager = nsdManager
        val listener = registrationListener
        if (manager != null && listener != null && isRegistered) {
            try {
                manager.unregisterService(listener)
            } catch (e: Throwable) {
                Log.w(TAG, "Exception during mDNS unregistration: ${e.message}")
            }
        }
        registrationListener = null
        nsdManager = null
        isRegistered = false
        registeredServiceName = null
    }
}
