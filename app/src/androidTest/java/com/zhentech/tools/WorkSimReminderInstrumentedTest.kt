package com.zhentech.tools

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkSimReminderInstrumentedTest {
    @Test
    fun simSettingsFallbackResolvesOnTheDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertTrue(SimSettingsNavigator.open(context))
    }

    @Test
    fun reminderNeedsNoNetworkAndTransitionActivityStaysPrivate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES,
        )

        assertFalse(Manifest.permission.INTERNET in packageInfo.requestedPermissions.orEmpty())
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in packageInfo.requestedPermissions.orEmpty())
        val transitionActivity = packageInfo.activities.orEmpty().single {
            it.name == WorkSimTransitionActivity::class.java.name
        }
        assertFalse(transitionActivity.exported)
    }
}
