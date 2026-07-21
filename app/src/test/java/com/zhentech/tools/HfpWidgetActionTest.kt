package com.zhentech.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class HfpWidgetActionTest {
    @Test
    fun offWithPermissionStartsStreaming() {
        assertEquals(
            HfpWidgetAction.Start,
            HfpWidgetAction.resolve(
                state = StreamingState.Off,
                hasBluetoothPermission = true,
            ),
        )
    }

    @Test
    fun offWithoutPermissionRequestsPermission() {
        assertEquals(
            HfpWidgetAction.RequestPermission,
            HfpWidgetAction.resolve(
                state = StreamingState.Off,
                hasBluetoothPermission = false,
            ),
        )
    }

    @Test
    fun onAlwaysStopsEvenWhenBluetoothPermissionWasRevoked() {
        assertEquals(
            HfpWidgetAction.Stop,
            HfpWidgetAction.resolve(
                state = StreamingState.On,
                hasBluetoothPermission = false,
            ),
        )
    }
}
