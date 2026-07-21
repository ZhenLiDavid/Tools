package com.zhentech.tools

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HfpStreamingWidgetInstrumentedTest {
    @Test
    fun widgetProviderIsDeclaredForTheHomeScreen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, HfpStreamingWidget::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(receiverInfo.exported)
        assertEquals(
            R.xml.hfp_streaming_widget_info,
            receiverInfo.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER),
        )
    }

    @Test
    fun widgetRendersDistinctAccessibleOffAndOnStates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        lateinit var offView: View
        lateinit var onView: View

        instrumentation.runOnMainSync {
            offView = inflateWidget(context, StreamingState.Off)
            onView = inflateWidget(context, StreamingState.On)
        }

        val offLabel = offView.findViewById<TextView>(R.id.hfp_widget_label)
        val onLabel = onView.findViewById<TextView>(R.id.hfp_widget_label)
        val offBackground = offView.findViewById<View>(android.R.id.background)
        val onBackground = onView.findViewById<View>(android.R.id.background)

        assertEquals(context.getString(R.string.hfp_widget_streaming_off), offLabel.text)
        assertEquals(context.getString(R.string.hfp_widget_streaming_on), onLabel.text)
        assertEquals(
            context.getString(R.string.hfp_widget_start_streaming),
            offBackground.contentDescription,
        )
        assertEquals(
            context.getString(R.string.hfp_widget_stop_streaming),
            onBackground.contentDescription,
        )
        assertNotEquals(
            offLabel.currentTextColor,
            onLabel.currentTextColor,
        )
    }

    private fun inflateWidget(context: Context, state: StreamingState): View =
        HfpStreamingWidget.createRemoteViews(
            context = context,
            state = state,
            hasBluetoothPermission = true,
        ).apply(context, FrameLayout(context))
}
