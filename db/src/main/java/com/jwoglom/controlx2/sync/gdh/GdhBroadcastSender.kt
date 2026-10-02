package com.jwoglom.controlx2.sync.gdh

import android.content.Context
import android.content.Intent
import timber.log.Timber

interface GdhBroadcaster {
    /** True means Android accepted at least one targeted broadcast, not that GDH displayed it. */
    fun send(payload: GdhPayload): Boolean
}

/** Sends GDH's existing Juggluco-compatible `glucodata.Minute` broadcast. */
class GdhBroadcastSender(
    private val installedPackagesProvider: () -> List<String>,
    private val sendBroadcastFn: (packageName: String, payload: GdhPayload) -> Boolean,
) : GdhBroadcaster {
    constructor(context: Context) : this(
        installedPackagesProvider = {
            GdhPackageInspector(context).inspect().installedPackages.map { it.packageName }
        },
        sendBroadcastFn = { packageName, payload ->
            val intent = Intent(ACTION_GLUCODATA_MINUTE).apply {
                `package` = packageName
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra(EXTRA_MGDL, payload.mgdl)
                putExtra(EXTRA_TIME, payload.readingAt.toEpochMilli())
                putExtra(EXTRA_RATE, payload.trendRateMgdlPerMinute)
                putExtra(EXTRA_SERIAL, payload.sensorId)
                putExtra(EXTRA_SOURCE_PACKAGE, payload.sourcePackage)
                payload.sensorStartedAt?.let {
                    putExtra(EXTRA_SENSOR_START_TIME, it.toEpochMilli())
                }
                payload.iobUnits?.let { putExtra(EXTRA_IOB, it) }
                payload.iobObservedAt?.let {
                    putExtra(EXTRA_IOB_COB_TIME, it.toEpochMilli())
                }
            }
            context.sendBroadcast(intent)
            true
        },
    )

    override fun send(payload: GdhPayload): Boolean {
        val targets = installedPackagesProvider().distinct()
        if (targets.isEmpty()) {
            Timber.w("GDH broadcast skipped: GlucoDataHandler is not installed")
            return false
        }

        var emitted = false
        targets.forEach { packageName ->
            val targetEmitted = runCatching {
                sendBroadcastFn(packageName, payload)
            }.getOrElse { error ->
                Timber.e(error, "GDH broadcast failed package=%s", packageName)
                false
            }
            if (targetEmitted) {
                emitted = true
                Timber.i(
                    "Emitted GDH broadcast package=%s readingAt=%s hasIob=%s",
                    packageName,
                    payload.readingAt,
                    payload.iobUnits != null,
                )
            }
        }
        return emitted
    }

    companion object {
        const val ACTION_GLUCODATA_MINUTE = "glucodata.Minute"
        const val GDH_PACKAGE = "de.michelinside.glucodatahandler"
        const val GDH_SECOND_PACKAGE = "de.michelinside.glucodatahandler.second"
        val TARGET_PACKAGES = listOf(GDH_PACKAGE, GDH_SECOND_PACKAGE)

        const val EXTRA_SERIAL = "glucodata.Minute.SerialNumber"
        const val EXTRA_MGDL = "glucodata.Minute.mgdl"
        const val EXTRA_RATE = "glucodata.Minute.Rate"
        const val EXTRA_TIME = "glucodata.Minute.Time"
        const val EXTRA_IOB = "glucodata.Minute.IOB"
        const val EXTRA_IOB_COB_TIME = "gdh.IOB_COB_time"
        const val EXTRA_SENSOR_START_TIME = "gdh.sensor_start_time"
        const val EXTRA_SOURCE_PACKAGE = "gdh.source_package"
    }
}
