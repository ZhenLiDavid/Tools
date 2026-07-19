package com.zhentech.tools

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectHfpRoutingInstrumentedTest {
    @Test
    fun streamingUsesOnlyDirectBluetoothRoutingPermissionsAndServiceType() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES,
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertFalse(Manifest.permission.RECORD_AUDIO in permissions)
        assertFalse(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION in permissions)
        assertFalse(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK in permissions)

        val streamingService = packageInfo.services.orEmpty().single {
            it.name == HfpStreamingService::class.java.name
        }
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            streamingService.foregroundServiceType,
        )
        assertEquals(0, streamingService.flags and ServiceInfo.FLAG_STOP_WITH_TASK)
    }
}
