package com.focal.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focal.android.server.CameraStreamBroadcaster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Focal", appName)
  }

  @Test
  fun `frame broadcaster produces valid jpeg`() {
    val frame = CameraStreamBroadcaster.getLatestFrame()
    assertNotNull(frame)
    assertTrue(frame.isNotEmpty())
    assertEquals(0xFF.toByte(), frame[0])
    assertEquals(0xD8.toByte(), frame[1])
  }
}
