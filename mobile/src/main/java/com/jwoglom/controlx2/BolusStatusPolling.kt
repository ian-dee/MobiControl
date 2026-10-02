package com.jwoglom.controlx2

import android.os.Handler
import android.os.Looper
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import timber.log.Timber

/** True once the pump no longer reports this bolus as requesting or delivering. */
internal fun CurrentBolusStatusResponse.isTerminalBolusStatus(): Boolean =
    bolusId == 0 ||
        status !in setOf(
            CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING,
            CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING,
        )

/**
 * Process-wide status poll lifecycle.
 *
 * Android may create a different BroadcastReceiver instance for every status broadcast. Keeping
 * the Handler on the receiver instance prevented a completion broadcast from cancelling the
 * timer that another instance created.
 */
internal object BolusStatusPolling {
    private var handler: Handler? = null
    private var runnable: Runnable? = null

    @Synchronized
    fun start(
        requestStatus: () -> Unit,
        handler: Handler = Handler(Looper.getMainLooper()),
        initialDelayMillis: Long = 500L,
        intervalMillis: Long = 1_000L,
        maxRequests: Int = 60,
    ) {
        require(initialDelayMillis >= 0L)
        require(intervalMillis > 0L)
        require(maxRequests > 0)
        stopLocked()

        this.handler = handler
        var requestCount = 0
        val pollingRunnable = object : Runnable {
            override fun run() {
                synchronized(this@BolusStatusPolling) {
                    if (runnable !== this) return
                    if (requestCount >= maxRequests) {
                        Timber.d(
                            "BolusNotificationBroadcastReceiver stopping status updates after %s requests",
                            maxRequests,
                        )
                        stopLocked()
                        return
                    }
                    requestCount++
                }

                Timber.d(
                    "BolusNotificationBroadcastReceiver requesting bolus status update (request %s)",
                    requestCount,
                )
                requestStatus()

                synchronized(this@BolusStatusPolling) {
                    if (runnable === this) {
                        handler.postDelayed(this, intervalMillis)
                    }
                }
            }
        }
        runnable = pollingRunnable
        handler.postDelayed(pollingRunnable, initialDelayMillis)
    }

    @Synchronized
    fun stop(): Boolean = stopLocked()

    @Synchronized
    fun isRunning(): Boolean = runnable != null

    private fun stopLocked(): Boolean {
        val activeRunnable = runnable ?: return false
        handler?.removeCallbacks(activeRunnable)
        runnable = null
        handler = null
        Timber.d("BolusNotificationBroadcastReceiver stopped status updates")
        return true
    }
}
