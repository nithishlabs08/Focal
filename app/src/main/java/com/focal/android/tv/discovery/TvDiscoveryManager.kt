package com.focal.android.tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.focal.android.tv.model.DiscoveredCamera
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Discovers active Focal camera streamers on the local LAN using Android NSD (mDNS / DNS-SD).
 * Targets the '_focal._tcp.' service registered by Focal mobile streamers.
 */
class TvDiscoveryManager(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _discoveredCameras = MutableStateFlow<List<DiscoveredCamera>>(emptyList())
    val discoveredCameras: StateFlow<List<DiscoveredCamera>> = _discoveredCameras.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveryError = MutableStateFlow<String?>(null)
    val discoveryError: StateFlow<String?> = _discoveryError.asStateFlow()

    private val cameraMap = ConcurrentHashMap<String, DiscoveredCamera>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // Sequential resolve queue to prevent NsdManager "FAILURE_ALREADY_ACTIVE" crashes
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private var isResolving = false

    companion object {
        private const val TAG = "TvDiscoveryManager"
        const val SERVICE_TYPE = "_focal._tcp."
    }

    fun startDiscovery() {
        if (_isScanning.value || nsdManager == null) {
            if (nsdManager == null) {
                _discoveryError.value = "Network discovery is not available on this device"
            }
            return
        }
        _discoveryError.value = null

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("focal")) {
                    enqueueResolve(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName
                cameraMap.remove(serviceName)
                updateList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: error=$errorCode")
                _isScanning.value = false
                _discoveryError.value = "Could not scan for cameras (error $errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery stop failed: error=$errorCode")
                _isScanning.value = false
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        if (!_isScanning.value || nsdManager == null) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        } finally {
            discoveryListener = null
            _isScanning.value = false
            resolveQueue.clear()
            isResolving = false
        }
    }

    fun addManualCamera(name: String, host: String, port: Int = 8080) {
        val id = "manual_${host}_$port"
        val camera = DiscoveredCamera(id = id, name = name, host = host, port = port)
        cameraMap[id] = camera
        updateList()
    }

    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.add(serviceInfo)
        processNextResolve()
    }

    private fun processNextResolve() {
        synchronized(this) {
            if (isResolving || resolveQueue.isEmpty() || nsdManager == null) return
            isResolving = true
        }

        val info = resolveQueue.poll() ?: run {
            isResolving = false
            return
        }

        val manager = nsdManager ?: run {
            finishResolve()
            return
        }

        val listener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    finishResolve()
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val hostAddress = resolvedInfo.firstHostAddress()
                    val port = resolvedInfo.port
                    val name = resolvedInfo.serviceName

                    if (!hostAddress.isNullOrBlank() && port > 0) {
                        val tlsPort = readTlsPort(resolvedInfo)
                        val friendlyName = name.removePrefix("Focal-").replace("_", " ")
                        val camera = DiscoveredCamera(
                            id = name,
                            name = if (friendlyName.isNotBlank()) friendlyName else "Focal Camera",
                            host = hostAddress,
                            port = port,
                            tlsPort = tlsPort
                        )
                        cameraMap[name] = camera
                        updateList()
                    }
                    finishResolve()
                }
            }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.resolveService(
                    info,
                    NsdManager.PROTOCOL_DNS_SD,
                    { runnable -> mainHandler.post(runnable) },
                    listener
                )
            } else {
                @Suppress("DEPRECATION")
                manager.resolveService(info, listener)
            }
        } catch (_: Exception) {
            finishResolve()
        }
    }

    private fun NsdServiceInfo.firstHostAddress(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return hostAddresses.firstOrNull()?.hostAddress
        }
        @Suppress("DEPRECATION")
        return host?.hostAddress
    }

    private fun finishResolve() {
        mainHandler.post {
            isResolving = false
            processNextResolve()
        }
    }

    private fun updateList() {
        _discoveredCameras.value = cameraMap.values.toList().sortedBy { it.name }
    }

    private fun readTlsPort(resolvedInfo: NsdServiceInfo): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val raw = resolvedInfo.attributes["tls_port"] ?: return null
        return raw.toString(Charsets.UTF_8).toIntOrNull()
    }
}
