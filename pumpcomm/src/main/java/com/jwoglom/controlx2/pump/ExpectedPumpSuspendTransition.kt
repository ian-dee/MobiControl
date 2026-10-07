package com.jwoglom.controlx2.pump

/**
 * Tracks the short, pump-initiated communications interruption which follows an accepted Mobi
 * suspend command. The pump can emit status-change events and terminate BLE during this window;
 * neither is evidence that the suspend failed.
 */
internal class ExpectedPumpSuspendTransition(
    private val nowNanos: () -> Long = System::nanoTime,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    @Volatile
    private var activeUntilNanos: Long = 0L

    fun begin() {
        activeUntilNanos = nowNanos() + timeoutMs * NANOS_PER_MILLISECOND
    }

    fun onResponse(status: Int) {
        if (status == 0) begin() else clear()
    }

    fun isActive(): Boolean = activeUntilNanos > nowNanos()

    fun clear() {
        activeUntilNanos = 0L
    }

    companion object {
        private const val NANOS_PER_MILLISECOND = 1_000_000L
        private const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
