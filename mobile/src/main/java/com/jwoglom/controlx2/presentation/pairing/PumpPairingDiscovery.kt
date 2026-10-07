package com.jwoglom.controlx2.presentation.pairing

import com.jwoglom.controlx2.shared.util.determinePumpModel
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.messages.models.KnownDeviceModel
import java.util.Locale

internal const val MOBI_PAIRING_READINESS_WINDOW_MILLIS = 120_000L

data class PumpPairingCandidate(
    val address: String,
    val name: String?,
    val readyState: PumpReadyState,
    val readyObservedAtElapsedRealtimeMillis: Long?,
    val lastSeenAtElapsedRealtimeMillis: Long,
) {
    val model: KnownDeviceModel?
        get() = name?.let(::determinePumpModel)

    fun isFreshMobiPairingMode(nowElapsedRealtimeMillis: Long): Boolean {
        val observedAt = readyObservedAtElapsedRealtimeMillis ?: return false
        val age = nowElapsedRealtimeMillis - observedAt
        return model == KnownDeviceModel.MOBI &&
            readyState == PumpReadyState.PICKED_UP_WITH_TAP &&
            age in 0..MOBI_PAIRING_READINESS_WINDOW_MILLIS
    }

    /**
     * The finder can see a Mobi on its charger before the advertisement has a usable name. Treat
     * that unnamed charger state as an instruction-stage signal only; it is never selectable.
     */
    fun indicatesMobiOnCharger(): Boolean =
        readyState == PumpReadyState.CHARGING_OR_PICKED_UP &&
            (name == null || model == KnownDeviceModel.MOBI)

    fun isSelectable(nowElapsedRealtimeMillis: Long): Boolean = when (model) {
        KnownDeviceModel.MOBI -> isFreshMobiPairingMode(nowElapsedRealtimeMillis)
        KnownDeviceModel.TSLIM_X2 -> true
        else -> false
    }
}

/**
 * UI-side state for one live finder attempt. No candidate or readiness is persisted across reset.
 */
internal class PumpPairingDiscoveryState {
    private val candidatesByAddress = linkedMapOf<String, PumpPairingCandidate>()

    fun reset() {
        candidatesByAddress.clear()
    }

    fun candidates(): List<PumpPairingCandidate> = candidatesByAddress.values.toList()

    fun hasSelectableCandidate(nowElapsedRealtimeMillis: Long): Boolean =
        candidatesByAddress.values.any { it.isSelectable(nowElapsedRealtimeMillis) }

    fun candidate(address: String?): PumpPairingCandidate? {
        val normalized = normalizeBluetoothAddress(address) ?: return null
        return candidatesByAddress[normalized]
    }

    fun select(address: String?, nowElapsedRealtimeMillis: Long): PumpPairingCandidate? =
        candidate(address)?.takeIf { it.isSelectable(nowElapsedRealtimeMillis) }

    fun observeFoundPumpsPayload(
        payload: String,
        nowElapsedRealtimeMillis: Long,
    ): List<PumpPairingCandidate> {
        if (payload.isBlank()) return candidates()
        payload.split(';').forEach { item ->
            val separator = item.lastIndexOf('=')
            if (separator <= 0 || separator == item.lastIndex) return@forEach
            observe(
                rawName = item.substring(0, separator),
                rawAddress = item.substring(separator + 1),
                readyState = null,
                nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
            )
        }
        return candidates()
    }

    fun observeDiscoveredPayload(
        payload: String,
        nowElapsedRealtimeMillis: Long,
    ): PumpPairingCandidate? {
        val stateSeparator = payload.indexOf(";;")
        if (stateSeparator <= 0 || stateSeparator + 2 >= payload.length) return null
        val identity = payload.substring(0, stateSeparator)
        val identitySeparator = identity.lastIndexOf('=')
        if (identitySeparator <= 0 || identitySeparator == identity.lastIndex) return null
        val readyState = runCatching {
            PumpReadyState.valueOf(payload.substring(stateSeparator + 2).trim())
        }.getOrNull() ?: return null
        return observe(
            rawName = identity.substring(0, identitySeparator),
            rawAddress = identity.substring(identitySeparator + 1),
            readyState = readyState,
            nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
        )
    }

    private fun observe(
        rawName: String?,
        rawAddress: String?,
        readyState: PumpReadyState?,
        nowElapsedRealtimeMillis: Long,
    ): PumpPairingCandidate? {
        val address = normalizeBluetoothAddress(rawAddress) ?: return null
        val name = normalizePumpName(rawName)
        val previous = candidatesByAddress[address]
        val updatedReadyState = readyState ?: previous?.readyState ?: PumpReadyState.UNKNOWN
        val readyObservedAt = when {
            updatedReadyState != PumpReadyState.PICKED_UP_WITH_TAP -> null
            previous?.readyState == PumpReadyState.PICKED_UP_WITH_TAP ->
                previous.readyObservedAtElapsedRealtimeMillis ?: nowElapsedRealtimeMillis
            else -> nowElapsedRealtimeMillis
        }
        val updated = PumpPairingCandidate(
            address = address,
            name = name ?: previous?.name,
            readyState = updatedReadyState,
            readyObservedAtElapsedRealtimeMillis = readyObservedAt,
            lastSeenAtElapsedRealtimeMillis = nowElapsedRealtimeMillis,
        )
        candidatesByAddress[address] = updated
        return updated
    }

    companion object {
        private val bluetoothAddressPattern =
            Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")

        fun normalizeBluetoothAddress(rawAddress: String?): String? {
            val normalized = rawAddress?.trim()?.uppercase(Locale.US).orEmpty()
            return normalized.takeIf { bluetoothAddressPattern.matches(it) }
        }

        private fun normalizePumpName(rawName: String?): String? {
            val normalized = rawName?.trim().orEmpty()
            return normalized.takeUnless {
                it.isEmpty() || it.equals("NO NAME", ignoreCase = true)
            }
        }
    }
}
