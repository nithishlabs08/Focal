package com.focal.android.tv.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
        if (_isScanning.value || nsdManager == null) return

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
                _isScanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
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

        try {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    finishResolve()
                }

                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    val hostAddress = resolvedInfo.host?.hostAddress
                    val port = resolvedInfo.port
                    val name = resolvedInfo.serviceName

                    if (!hostAddress.isNullOrBlank() && port > 0) {
                        val friendlyName = name.removePrefix("Focal-").replace("_", " ")
                        val camera = DiscoveredCamera(
                            id = name,
                            name = if (friendlyName.isNotBlank()) friendlyName else "Focal Camera",
                            host = hostAddress,
                            port = port
                        )
                        cameraMap[name] = camera
                        updateList()
                    }
                    finishResolve()
                }
            })
        } catch (_: Exception) {
            finishResolve()
        }
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
}
