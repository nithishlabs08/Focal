package com.focal.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focal.android.tv.client.TvAudioPlayer
import com.focal.android.tv.client.TvClientState
import com.focal.android.tv.client.TvStreamClient
import com.focal.android.tv.client.TvVideoDecoder
import com.focal.android.tv.discovery.TvDiscoveryManager
import com.focal.android.tv.model.DiscoveredCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TvClientTest {

    @Test
    fun discoveredCamera_urlFormatting() {
        val camera = DiscoveredCamera(
            id = "test_cam",
            name = "Pixel 8 Pro",
            host = "192.168.1.150",
            port = 8080
        )

        assertEquals("http://192.168.1.150:8080/stream.h264", camera.streamUrl)
        assertEquals("http://192.168.1.150:8080/stream.mjpg", camera.mjpegUrl)
    }

    @Test
    fun tvDiscoveryManager_addManualCamera() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = TvDiscoveryManager(context)

        manager.addManualCamera(name = "Living Room Phone", host = "192.168.1.100", port = 8080)
        val list = manager.discoveredCameras.value

        assertEquals(1, list.size)
        assertEquals("Living Room Phone", list[0].name)
        assertEquals("192.168.1.100", list[0].host)
        assertEquals(8080, list[0].port)
    }

    @Test
    fun tvAudioPlayer_muteToggle() {
        val player = TvAudioPlayer()
        assertFalse(player.isMuted)

        player.toggleMute()
        assertTrue(player.isMuted)

        player.toggleMute()
        assertFalse(player.isMuted)

        player.start()
        player.playPcm(ByteArray(100))
        player.stop()
    }

    @Test
    fun tvStreamClient_initialState() {
        val decoder = TvVideoDecoder()
        val audio = TvAudioPlayer()
        val client = TvStreamClient(decoder, audio)

        assertEquals(TvClientState.IDLE, client.connectionState.value)
        assertEquals(0f, client.currentFps.value, 0.01f)
        assertEquals(0f, client.currentBitrateMbps.value, 0.01f)

        client.disconnect()
        assertEquals(TvClientState.IDLE, client.connectionState.value)
    }
}
