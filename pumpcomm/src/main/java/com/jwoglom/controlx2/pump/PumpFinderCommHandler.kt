package com.jwoglom.controlx2.pump

import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import android.os.Message
import com.jwoglom.controlx2.shared.CommServiceCodes
import com.jwoglom.controlx2.shared.MessagePaths
import com.jwoglom.pumpx2.pump.bluetooth.PumpReadyState
import com.jwoglom.pumpx2.pump.bluetooth.TandemPumpFinder
import com.welie.blessed.BluetoothPeripheral
import timber.log.Timber

/**
 * Handler for pump discovery mode. Manages scanning for available Tandem pumps
 * and reporting discovered pumps back via callbacks.
 *
 * Extracted from CommService's inner class to enable independent testing
 * and future module extraction.
 */
class PumpFinderCommHandler(
    looper: Looper,
    private val callbacks: CommServiceCallbacks
) : Handler(looper) {

    private var pumpFinder: PumpFinder? = null
    private var pumpFinderActive = false
    private val discoveryRoster = PumpFinderDiscoveryRoster()

    private inner class PumpFinder(
        private val scanGeneration: Long,
    ) : TandemPumpFinder(callbacks.getApplicationContext(), null) {
        init {
            Timber.i("PumpFinder init")
        }

        override fun toString(): String {
            return "PumpFinder(pumpFinderActive=$pumpFinderActive,foundPumpCount=${discoveryRoster.entries().size})"
        }

        override fun onDiscoveredPump(
            peripheral: BluetoothPeripheral?,
            scanResult: ScanResult?,
            readyState: PumpReadyState
        ) {
            val observation = discoveryRoster.observe(
                observedGeneration = scanGeneration,
                rawName = peripheral?.name,
                rawAddress = peripheral?.address,
                readyState = readyState,
            ) ?: return
            val entry = observation.entry
            val displayName = entry.name ?: "NO NAME"
            val key = "$displayName=${entry.address}"
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_FINDER_PUMP_DISCOVERED,
                "${key};;${readyState.name}".toByteArray()
            )
            // The selectable summary contains only named pumps. An unnamed advertisement is still
            // emitted above as non-interactive progress and can later upgrade this same MAC row.
            if (observation.rosterChanged && entry.name != null) {
                callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_FINDER_FOUND_PUMPS,
                    discoveryRoster.namedPumpPayload().toByteArray()
                )
            }
        }

        override fun onBluetoothState(bluetoothEnabled: Boolean) {
            callbacks.sendWearCommMessage(
                MessagePaths.FROM_PUMP_PUMP_FINDER_BLUETOOTH_STATE,
                "${bluetoothEnabled}".toByteArray()
            )
        }
    }

    override fun handleMessage(msg: Message) {
        when (msg.what) {
            CommServiceCodes.INIT_PUMP_FINDER_COMM.ordinal -> {
                Timber.i("pumpFinderCommHandler: init_pump_comm")
                pumpFinder?.let { existing ->
                    discoveryRoster.stopGeneration()
                    try {
                        existing.stop()
                    } catch (e: SecurityException) {
                        Timber.w(e, "pumpFinderCommHandler: unable to stop previous finder")
                    }
                }
                pumpFinderActive = false
                val scanGeneration = discoveryRoster.startGeneration()
                try {
                    pumpFinder = PumpFinder(scanGeneration)
                } catch (e: SecurityException) {
                    Timber.e("pumpFinderCommHandler: SecurityException starting pump $e")
                    pumpFinder = null
                    return
                }
                while (true) {
                    try {
                        Timber.i("pumpFinderCommHandler: Starting scan...")
                        pumpFinder?.startScan() ?: return
                        pumpFinderActive = true
                        break
                    } catch (e: SecurityException) {
                        Timber.e("pumpFinderCommHandler: Waiting for BT permissions $e")
                        Thread.sleep(500)
                    }
                }
            }
            CommServiceCodes.STOP_PUMP_FINDER_COMM.ordinal -> {
                val finderToStop = pumpFinder
                discoveryRoster.stopGeneration()
                pumpFinder = null
                finderToStop?.stop()
                pumpFinderActive = false
            }
            CommServiceCodes.CHECK_PUMP_FINDER_FOUND_PUMPS.ordinal -> {
                if (pumpFinderActive) {
                    discoveryRoster.entries().forEach { entry ->
                        callbacks.sendWearCommMessage(
                            MessagePaths.FROM_PUMP_PUMP_FINDER_PUMP_DISCOVERED,
                            "${entry.name ?: "NO NAME"}=${entry.address};;${entry.readyState.name}".toByteArray(),
                        )
                    }
                    callbacks.sendWearCommMessage(MessagePaths.FROM_PUMP_PUMP_FINDER_FOUND_PUMPS,
                        discoveryRoster.namedPumpPayload().toByteArray()
                    )
                }
            }
        }
    }
}
