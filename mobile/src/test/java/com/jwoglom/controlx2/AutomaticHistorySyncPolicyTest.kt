package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jwoglom.controlx2.util.HistoryLogRequestBatchSize
import com.jwoglom.controlx2.util.HistoryLogStatusIntervalMs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AutomaticHistorySyncPolicyTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun historySyncCannotBeDisabledByLegacyPreference() {
        prefs.prefs().edit().putBoolean("auto-fetch-history-logs", false).commit()

        assertTrue(Prefs(context).autoFetchHistoryLogs())
    }

    @Test
    fun cadenceAndBatchSizeMatchObservedMobiTraffic() {
        assertEquals(60_000L, HistoryLogStatusIntervalMs)
        assertEquals(30, HistoryLogRequestBatchSize)
    }
}
