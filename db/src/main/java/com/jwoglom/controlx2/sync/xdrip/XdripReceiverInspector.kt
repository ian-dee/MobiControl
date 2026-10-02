package com.jwoglom.controlx2.sync.xdrip

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

data class XdripReceiverStatus(
    val installed: Boolean,
    val packageVersion: String?,
)

/** Read-only package/receiver inspection. It never starts xDrip or sends a broadcast. */
class XdripReceiverInspector(private val context: Context) {
    fun inspect(): XdripReceiverStatus {
        val packageManager = context.packageManager
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    XdripBroadcastSender.XDRIP_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(XdripBroadcastSender.XDRIP_PACKAGE, 0)
            }
        }.getOrNull()

        return XdripReceiverStatus(
            installed = packageInfo != null,
            packageVersion = packageInfo?.versionName,
        )
    }

    fun hasReceiver(action: String): Boolean = receiverCount(action) > 0

    private fun receiverCount(action: String): Int {
        val packageManager = context.packageManager
        val intent = Intent(action).setPackage(XdripBroadcastSender.XDRIP_PACKAGE)
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryBroadcastReceivers(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0),
                ).size
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryBroadcastReceivers(intent, 0).size
            }
        }.getOrElse { error ->
            Timber.w(error, "Unable to inspect xDrip receiver action=%s", action)
            0
        }
    }
}
