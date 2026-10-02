package com.jwoglom.controlx2.util

import android.os.Handler
import android.os.Looper
import com.jwoglom.controlx2.pump.PumpHistoryLogSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

const val HistoryLogStatusIntervalMs = 60_000L

/**
 * Lightweight periodic worker that triggers a sequence-only HistoryLogStatus request while the
 * pump is connected. The fetcher downloads records only when the pump reports a missing range.
 */
class HistoryLogSyncWorker(
    private val intervalMs: Long = HistoryLogStatusIntervalMs,
    private val requestSync: () -> Unit
) : PumpHistoryLogSyncWorker {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncRunnable: Runnable? = null
    private var isRunning = false

    override fun start() {
        if (isRunning) {
            Timber.v("HistoryLogSyncWorker already running")
            return
        }
        isRunning = true
        scheduleNextSync()
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        syncRunnable?.let { handler.removeCallbacks(it) }
        syncRunnable = null
    }

    override fun triggerImmediateSync() {
        scope.launch {
            try {
                requestSync()
            } catch (t: Throwable) {
                Timber.w(t, "HistoryLogSyncWorker immediate sync failed")
            }
        }
    }

    private fun scheduleNextSync() {
        if (!isRunning) return
        val runnable = Runnable {
            scope.launch {
                try {
                    requestSync()
                } catch (t: Throwable) {
                    Timber.w(t, "HistoryLogSyncWorker periodic sync failed")
                } finally {
                    scheduleNextSync()
                }
            }
        }
        syncRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }
}
