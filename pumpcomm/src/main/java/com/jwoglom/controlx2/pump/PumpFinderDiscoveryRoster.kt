package com.jwoglom.controlx2.pump

import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import java.util.Locale

internal data class PumpFinderDiscoveryEntry(
    val address: String,
    val name: String?,
    val readyState: PumpReadyState,
)

internal data class PumpFinderDiscoveryObservation(
    val entry: PumpFinderDiscoveryEntry,
    val rosterChanged: Boolean,
)

/**
 * Current-scan PumpFinder results, keyed only by normalized Bluetooth address.
 *
 * A generation token makes callbacks from a stopped/replaced finder harmless. Names are allowed
 * to arrive later than the first advertisement, but an absent name never erases a known one.
 */
internal class PumpFinderDiscoveryRoster {
    private val entriesByAddress = linkedMapOf<String, PumpFinderDiscoveryEntry>()
    private var generation = 0L

    fun startGeneration(): Long {
        generation += 1L
        entriesByAddress.clear()
        return generation
    }

    fun stopGeneration() {
        generation += 1L
        entriesByAddress.clear()
    }

    fun observe(
        observedGeneration: Long,
        rawName: String?,
        rawAddress: String?,
        readyState: PumpReadyState,
    ): PumpFinderDiscoveryObservation? {
        if (observedGeneration != generation) return null
        val address = normalizeBluetoothAddress(rawAddress) ?: return null
        val name = normalizePumpName(rawName)
        val previous = entriesByAddress[address]
        val updated = PumpFinderDiscoveryEntry(
            address = address,
            name = name ?: previous?.name,
            readyState = readyState,
        )
        entriesByAddress[address] = updated
        return PumpFinderDiscoveryObservation(
            entry = updated,
            rosterChanged = previous == null || previous.name != updated.name,
        )
    }

    fun entries(): List<PumpFinderDiscoveryEntry> = entriesByAddress.values.toList()

    fun namedPumpPayload(): String = entriesByAddress.values
        .mapNotNull { entry -> entry.name?.let { name -> "$name=${entry.address}" } }
        .joinToString(";")

    companion object {
        private val bluetoothAddressPattern =
            Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

        fun normalizeBluetoothAddress(rawAddress: String?): String? {
            val normalized = rawAddress?.trim()?.uppercase(Locale.US).orEmpty()
            return normalized.takeIf { bluetoothAddressPattern.matches(it) }
        }

        fun normalizePumpName(rawName: String?): String? {
            val normalized = rawName?.trim().orEmpty()
            return normalized.takeUnless {
                it.isEmpty() || it.equals("NO NAME", ignoreCase = true)
            }
        }
    }
}
