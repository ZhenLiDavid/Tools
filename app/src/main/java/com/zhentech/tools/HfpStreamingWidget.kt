package com.zhentech.tools

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews

class HfpStreamingWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds,
            state = AppStreamingState.store.state.value,
        )
    }

    companion object {
        internal const val ACTION_REQUEST_PERMISSION_AND_START =
            "com.zhentech.tools.action.REQUEST_HFP_PERMISSION_AND_START"

        internal fun updateAll(context: Context, state: StreamingState) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, HfpStreamingWidget::class.java)
            update(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetManager.getAppWidgetIds(provider),
                state = state,
            )
        }

        internal fun createRemoteViews(
            context: Context,
            state: StreamingState,
            hasBluetoothPermission: Boolean = context.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED,
        ): RemoteViews {
            val isStreaming = state == StreamingState.On
            val label = if (isStreaming) {
                R.string.hfp_widget_streaming_on
            } else {
                R.string.hfp_widget_streaming_off
            }
            val contentDescription = if (isStreaming) {
                R.string.hfp_widget_stop_streaming
            } else {
                R.string.hfp_widget_start_streaming
            }

            return RemoteViews(context.packageName, R.layout.hfp_streaming_widget).apply {
                setTextViewText(R.id.hfp_widget_label, context.getString(label))
                setContentDescription(
                    android.R.id.background,
                    context.getString(contentDescription),
                )
                setInt(
                    android.R.id.background,
                    "setBackgroundResource",
                    if (isStreaming) {
                        R.drawable.hfp_widget_background_on
                    } else {
                        R.drawable.hfp_widget_background_off
                    },
                )
                val contentColor = context.getColor(
                    if (isStreaming) {
                        R.color.hfp_widget_active_content
                    } else {
                        R.color.hfp_widget_inactive_content
                    },
                )
                setInt(R.id.hfp_widget_icon, "setColorFilter", contentColor)
                setTextColor(R.id.hfp_widget_label, contentColor)
                setOnClickPendingIntent(
                    android.R.id.background,
                    clickPendingIntent(
                        context = context,
                        action = HfpWidgetAction.resolve(state, hasBluetoothPermission),
                    ),
                )
            }
        }

        private fun update(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            state: StreamingState,
        ) {
            appWidgetIds.forEach { appWidgetId ->
                appWidgetManager.updateAppWidget(
                    appWidgetId,
                    createRemoteViews(context, state),
                )
            }
        }

        private fun clickPendingIntent(
            context: Context,
            action: HfpWidgetAction,
        ): PendingIntent = when (action) {
            HfpWidgetAction.Start -> HfpStreamingService.startPendingIntent(context)
            HfpWidgetAction.Stop -> HfpStreamingService.stopPendingIntent(context)
            HfpWidgetAction.RequestPermission -> PendingIntent.getActivity(
                context,
                REQUEST_PERMISSION_REQUEST_CODE,
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_REQUEST_PERMISSION_AND_START)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private const val REQUEST_PERMISSION_REQUEST_CODE = 3
    }
}

internal enum class HfpWidgetAction {
    Start,
    Stop,
    RequestPermission;

    companion object {
        fun resolve(
            state: StreamingState,
            hasBluetoothPermission: Boolean,
        ): HfpWidgetAction = when {
            state == StreamingState.On -> Stop
            hasBluetoothPermission -> Start
            else -> RequestPermission
        }
    }
}
