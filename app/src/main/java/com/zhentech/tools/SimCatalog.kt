package com.zhentech.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager

internal class SimCatalog(context: Context) {
    private val appContext = context.applicationContext

    fun availableSims(): List<SimDescriptor> {
        if (isRunningOnEmulator()) return previewSims()
        if (appContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return genericSims()
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
                .ifEmpty(::genericSims)
        } catch (_: SecurityException) {
            genericSims()
        }
    }

    private fun previewSims(): List<SimDescriptor> = listOf(
        SimDescriptor(slotIndex = 0, label = "Personal SIM", isEmbedded = false),
        SimDescriptor(slotIndex = 1, label = "Work eSIM", isEmbedded = true),
    )

    private fun genericSims(): List<SimDescriptor> = listOf(
        SimDescriptor(slotIndex = 0, label = "SIM 1", isEmbedded = false),
        SimDescriptor(slotIndex = 1, label = "SIM 2", isEmbedded = true),
    )
}

internal fun isRunningOnEmulator(): Boolean =
    Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)
