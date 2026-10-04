package com.focal.android.transport

interface TransportClientListener {
    fun onClientConnected(clientId: String, address: String)
    fun onClientAuthenticated(clientId: String)
    fun onClientDisconnected(clientId: String)
    fun onAuthChallengeFailed(clientId: String)
}

interface StreamTransport {
    val name: String
    val port: Int
    val isRunning: Boolean
    val activeClientsCount: Int

    fun start()
    fun stop()
    fun broadcastPacket(packet: StreamPacket)
    fun setClientListener(listener: TransportClientListener?)
}
