package com.jwoglom.controlx2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Android and Samsung entry points which help the foreground pump connection remain available
 * while the activity is not visible. These settings reduce OS-initiated suspension; they do not
 * replace the connected-device foreground service or its persistent notification.
 */
object BackgroundConnectionAccess {
    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun isBatteryOptimizationExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryOptimizationExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    /** Samsung's documented entry point for Settings > Never sleeping apps. */
    fun samsungNeverSleepingAppsIntent(): Intent = Intent().apply {
        action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY"
        setPackage("com.samsung.android.lool")
        putExtra("activity_type", 2)
    }

    fun isSamsungDevice(manufacturer: String = Build.MANUFACTURER): Boolean =
        manufacturer.equals("samsung", ignoreCase = true)

    fun canOpen(context: Context, intent: Intent): Boolean =
        intent.resolveActivity(context.packageManager) != null
}
