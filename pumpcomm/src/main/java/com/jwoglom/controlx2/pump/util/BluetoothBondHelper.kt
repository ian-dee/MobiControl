package com.jwoglom.controlx2.pump.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Reflection-based BluetoothDevice.removeBond. Android exposes the bond list
 * but no public API to drop a bond — the only path is the hidden `removeBond`
 * method, which the existing PumpCommHandler already calls inline.
 *
 * Returns true if no bond exists for [targetMac] after the call (either it was
 * already absent, or the unbond request was acknowledged and the OS dropped it
 * within the wait window). Android reports bond changes asynchronously, so this
 * polls for confirmation instead of assuming a successful reflection call means
 * the bond is gone.
 */
@SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is checked at runtime below for API 31+.
fun removeBondCompat(ctx: Context, targetMac: String): Boolean {
    if (targetMac.isBlank()) return true

    val adapter = BluetoothAdapter.getDefaultAdapter() ?: run {
        Timber.w("removeBondCompat: BluetoothAdapter null")
        return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        Timber.w("removeBondCompat: BLUETOOTH_CONNECT not granted")
        return false
    }

    val bondedDevice = adapter.bondedDevices
        ?.firstOrNull { it.address.equals(targetMac, ignoreCase = true) }
    if (bondedDevice == null) {
        Timber.i("removeBondCompat: $targetMac not currently bonded")
        return true
    }

    try {
        val removeBondMethod = bondedDevice.javaClass.getMethod("removeBond")
        val requested = removeBondMethod.invoke(bondedDevice) as? Boolean ?: false
        Timber.i("removeBondCompat: requested unbond for ${bondedDevice.name} ($targetMac), result=$requested")
    } catch (e: SecurityException) {
        Timber.w(e, "removeBondCompat: missing permission for $targetMac")
        return false
    } catch (e: Exception) {
        Timber.w(e, "removeBondCompat: reflection failure for $targetMac")
        return false
    }

    val deadlineNanos = System.nanoTime() + 5_000_000_000L
    do {
        val stillBonded = adapter.bondedDevices
            ?.any { it.address.equals(targetMac, ignoreCase = true) } == true
        if (!stillBonded) {
            Timber.i("removeBondCompat: Android confirmed $targetMac is no longer bonded")
            return true
        }
        Thread.sleep(100)
    } while (System.nanoTime() < deadlineNanos)

    Timber.w("removeBondCompat: $targetMac remains bonded after timeout")
    return false
}
