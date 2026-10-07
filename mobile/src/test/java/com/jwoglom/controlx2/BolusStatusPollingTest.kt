package com.jwoglom.controlx2

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.CurrentBolusStatusResponse
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BolusStatusPollingTest {
    @After
    fun tearDown() {
        BolusStatusPolling.stop()
    }

    @Test
    fun terminalStatusDoesNotRequireBolusIdToClear() {
        assertFalse(status(CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING, 42)
            .isTerminalBolusStatus())
        assertFalse(status(CurrentBolusStatusResponse.CurrentBolusStatus.DELIVERING, 42)
            .isTerminalBolusStatus())
        assertTrue(status(
            CurrentBolusStatusResponse.CurrentBolusStatus.ALREADY_DELIVERED_OR_INVALID,
            42,
        ).isTerminalBolusStatus())
        assertTrue(status(CurrentBolusStatusResponse.CurrentBolusStatus.REQUESTING, 0)
            .isTerminalBolusStatus())
    }

    @Test
    fun stopCancelsTheProcessWideTimerImmediately() {
        val mainHandler = Handler(Looper.getMainLooper())
        var requests = 0
        BolusStatusPolling.start(
            requestStatus = { requests++ },
            handler = mainHandler,
            initialDelayMillis = 0L,
            intervalMillis = 1_000L,
        )

        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, requests)
        assertTrue(BolusStatusPolling.isRunning())

        assertTrue(BolusStatusPolling.stop())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertEquals(1, requests)
        assertFalse(BolusStatusPolling.isRunning())
    }

    private fun status(
        status: CurrentBolusStatusResponse.CurrentBolusStatus,
        bolusId: Int,
    ) = CurrentBolusStatusResponse(
        status.id,
        bolusId,
        100L,
        500L,
        8,
        8,
    )
}
