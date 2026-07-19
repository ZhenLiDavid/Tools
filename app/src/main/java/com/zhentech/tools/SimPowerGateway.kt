package com.zhentech.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager

internal data class SimPowerResult(
    val success: Boolean,
    val backendStatus: WorkSimBackendStatus,
)

internal interface SimPowerGateway {
    val defaultStatus: WorkSimBackendStatus

    fun availableSims(): List<SimDescriptor>

    fun setPower(slotIndex: Int, enabled: Boolean): SimPowerResult
}

internal fun createSimPowerGateway(context: Context): SimPowerGateway =
    if (isRunningOnEmulator()) {
        EmulatedSimPowerGateway(context)
    } else {
        AdbWifiSimPowerGateway(context)
    }

internal class EmulatedSimPowerGateway(context: Context) : SimPowerGateway {
    private val store = WorkSimStore(context)

    override val defaultStatus = WorkSimBackendStatus.Simulated

    override fun availableSims(): List<SimDescriptor> = listOf(
        SimDescriptor(slotIndex = 0, label = "Personal SIM", isEmbedded = false),
        SimDescriptor(slotIndex = 1, label = "Work eSIM", isEmbedded = true),
    )

    override fun setPower(slotIndex: Int, enabled: Boolean): SimPowerResult {
        if (slotIndex !in availableSims().map(SimDescriptor::slotIndex)) {
            return SimPowerResult(false, WorkSimBackendStatus.Failed)
        }
        store.writeLastPowerState(enabled)
        return SimPowerResult(true, WorkSimBackendStatus.Simulated)
    }
}

internal class AdbWifiSimPowerGateway(context: Context) : SimPowerGateway {
    private val appContext = context.applicationContext
    private val adbClient = AdbWifiClient(appContext)

    override val defaultStatus = WorkSimBackendStatus.Ready

    override fun availableSims(): List<SimDescriptor> {
        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return genericSlots()
        }

        val subscriptionManager = appContext.getSystemService(SubscriptionManager::class.java)
        return try {
            val subscriptionInfos = if (Build.VERSION.SDK_INT >= 34) {
                subscriptionManager.allSubscriptionInfoList
            } else {
                subscriptionManager.completeActiveSubscriptionInfoList
            }
            subscriptionInfos
                .orEmpty()
                .asSequence()
                .filter { it.simSlotIndex >= 0 }
                .map { info ->
                    SimDescriptor(
                        slotIndex = info.simSlotIndex,
                        label = info.displayName?.toString()?.takeIf(String::isNotBlank)
                            ?: "SIM ${info.simSlotIndex + 1}",
                        isEmbedded = info.isEmbedded,
                    )
                }
                .distinctBy(SimDescriptor::slotIndex)
                .sortedBy(SimDescriptor::slotIndex)
                .toList()
                .ifEmpty(::genericSlots)
        } catch (_: SecurityException) {
            genericSlots()
        }
    }

    override fun setPower(slotIndex: Int, enabled: Boolean): SimPowerResult {
        val transactionCode = simPowerTransactionCode(Build.VERSION.SDK_INT)
            ?: return SimPowerResult(false, WorkSimBackendStatus.Failed)
        val powerState = if (enabled) 1 else 0
        val command = "service call phone $transactionCode i32 $slotIndex i32 $powerState"
        val response = adbClient.executeShell(command)
        if (response.isFailure) {
            return SimPowerResult(false, WorkSimBackendStatus.AdbUnavailable)
        }

        val output = response.getOrThrow()
        return if (!isSuccessfulServiceCallOutput(output)) {
            SimPowerResult(false, WorkSimBackendStatus.Failed)
        } else {
            SimPowerResult(true, WorkSimBackendStatus.Ready)
        }
    }

    private fun genericSlots(): List<SimDescriptor> = listOf(
        SimDescriptor(slotIndex = 0, label = "SIM 1", isEmbedded = false),
        SimDescriptor(slotIndex = 1, label = "SIM 2", isEmbedded = true),
    )
}

internal fun isSuccessfulServiceCallOutput(output: String): Boolean {
    val failed = listOf("Exception", "SecurityException", "Permission Denial", "error:")
        .any { marker -> output.contains(marker, ignoreCase = true) }
    return !failed && output.contains("Result: Parcel", ignoreCase = true)
}

internal fun simPowerTransactionCode(sdkInt: Int): Int? = when (sdkInt) {
    31, 32 -> 187
    33 -> 182
    34 -> 186
    35, 36, 37 -> 185
    else -> null
}

internal fun isRunningOnEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)
