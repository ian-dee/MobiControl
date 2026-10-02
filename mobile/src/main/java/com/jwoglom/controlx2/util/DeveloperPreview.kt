package com.jwoglom.controlx2.util

import android.os.Build
import com.jwoglom.controlx2.BuildConfig

internal fun isEmulatorDevice(
    fingerprint: String = Build.FINGERPRINT,
    model: String = Build.MODEL,
    manufacturer: String = Build.MANUFACTURER,
    product: String = Build.PRODUCT,
    brand: String = Build.BRAND,
    device: String = Build.DEVICE,
): Boolean {
    return fingerprint.startsWith("generic", ignoreCase = true) ||
        fingerprint.startsWith("unknown", ignoreCase = true) ||
        model.contains("google_sdk", ignoreCase = true) ||
        model.contains("Emulator", ignoreCase = true) ||
        model.contains("Android SDK built for", ignoreCase = true) ||
        manufacturer.contains("Genymotion", ignoreCase = true) ||
        (brand.startsWith("generic", ignoreCase = true) &&
            device.startsWith("generic", ignoreCase = true)) ||
        product.contains("sdk", ignoreCase = true) ||
        product.contains("emulator", ignoreCase = true) ||
        product.contains("simulator", ignoreCase = true)
}

fun developerPreviewAvailable(): Boolean {
    return BuildConfig.DEBUG && isEmulatorDevice()
}
