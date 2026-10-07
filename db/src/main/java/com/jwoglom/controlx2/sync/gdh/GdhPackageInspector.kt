package com.jwoglom.controlx2.sync.gdh

import android.content.Context
import android.content.pm.PackageManager

data class GdhInstalledPackage(
    val packageName: String,
    val versionName: String?,
)

data class GdhPackageStatus(
    val installedPackages: List<GdhInstalledPackage>,
) {
    val installed: Boolean get() = installedPackages.isNotEmpty()
}

/** Read-only discovery for the normal and optional second GlucoDataHandler installations. */
class GdhPackageInspector(private val context: Context) {
    fun inspect(): GdhPackageStatus = GdhPackageStatus(
        GdhBroadcastSender.TARGET_PACKAGES.mapNotNull(::installedPackage),
    )

    private fun installedPackage(packageName: String): GdhInstalledPackage? = try {
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
        GdhInstalledPackage(packageName, packageInfo.versionName)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
