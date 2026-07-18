package com.zhentech.tools

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HfpStreamingControllerTest {
    @Test
    fun unavailableDeviceKeepsStreamingOff() {
        val gateway = FakeGateway(deviceAvailable = false)
        val store = StreamingStateStore()
        val controller = HfpStreamingController(gateway, store)

        controller.start(1, Intent())

        assertFalse(gateway.startCalled)
        assertEquals(StreamingState.Off, controller.state.value)
    }

    @Test
    fun startThenStopDelegatesToGatewayAndReturnsOff() {
        val gateway = FakeGateway(deviceAvailable = true)
        val store = StreamingStateStore()
        val controller = HfpStreamingController(gateway, store)

        controller.start(1, Intent())
        store.markStarted()
        controller.stop()

        assertTrue(gateway.startCalled)
        assertTrue(gateway.stopCalled)
        assertEquals(StreamingState.Off, controller.state.value)
    }

    private class FakeGateway(
        private val deviceAvailable: Boolean,
    ) : HfpStreamingGateway {
        var startCalled = false
        var stopCalled = false

        override fun hasAvailableHfpDevice(): Boolean = deviceAvailable

        override fun start(resultCode: Int, projectionData: Intent) {
            startCalled = true
        }

        override fun stop() {
            stopCalled = true
        }
    }
}
