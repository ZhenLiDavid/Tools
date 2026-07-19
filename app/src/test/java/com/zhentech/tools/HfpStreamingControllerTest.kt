package com.zhentech.tools

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

        controller.start()

        assertFalse(gateway.startCalled)
        assertEquals(StreamingState.Off, controller.state.value)
    }

    @Test
    fun startThenStopDelegatesToGatewayAndReturnsOff() {
        val gateway = FakeGateway(deviceAvailable = true)
        val store = StreamingStateStore()
        val controller = HfpStreamingController(gateway, store)

        controller.start()
        store.markStarted()
        controller.stop()

        assertTrue(gateway.startCalled)
        assertTrue(gateway.stopCalled)
        assertEquals(StreamingState.Off, controller.state.value)
    }

    @Test
    fun repeatedStartRequestsOnlyOpenOneDirectRoute() {
        val gateway = FakeGateway(deviceAvailable = true)
        val controller = HfpStreamingController(gateway, StreamingStateStore())

        controller.start()
        controller.start()

        assertEquals(1, gateway.startCount)
    }

    private class FakeGateway(
        private val deviceAvailable: Boolean,
    ) : HfpStreamingGateway {
        var startCount = 0
        var stopCalled = false

        override fun hasAvailableHfpDevice(): Boolean = deviceAvailable

        override fun start() {
            startCount += 1
        }

        override fun stop() {
            stopCalled = true
        }

        val startCalled: Boolean
            get() = startCount > 0
    }
}
