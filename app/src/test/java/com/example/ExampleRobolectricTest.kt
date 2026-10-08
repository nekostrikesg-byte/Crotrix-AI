package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.actions.DeviceActionHandler
import org.json.JSONObject
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
    assertEquals("Arushi", appName)
  }

  @Test
  fun `test device action handler url opening validation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val handler = DeviceActionHandler(context, onLog = {})
    
    val result = handler.executeAction("openUrl", JSONObject().apply {
      put("url", "https://google.com")
    })
    
    assertNotNull(result)
    assertEquals("success", result.optString("status"))
  }

  @Test
  fun `test device action handler empty app returns error`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val handler = DeviceActionHandler(context, onLog = {})
    
    val result = handler.executeAction("openApp", JSONObject().apply {
      put("appName", "")
    })
    
    assertEquals("error", result.optString("status"))
  }
}
