package com.jwoglom.controlx2.sync.xdrip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripExportLedgerTest {
    @Test
    fun identitySurvivesLedgerRecreation() {
        var storage: String? = null
        val first = PersistedXdripExportLedger(
            readValue = { storage },
            writeValue = { storage = it; true },
        )
        assertTrue(first.tryBeginExport("bolus:68:9001", 10_000))
        first.finishExport("bolus:68:9001", 10_000, emitted = true)

        val recreated = PersistedXdripExportLedger(
            readValue = { storage },
            writeValue = { storage = it; true },
        )
        assertFalse(recreated.tryBeginExport("bolus:68:9001", 11_000))
    }

    @Test
    fun oldIdentityExpires() {
        var storage: String? = null
        val ledger = PersistedXdripExportLedger(
            readValue = { storage },
            writeValue = { storage = it; true },
            retentionMillis = 1_000,
        )
        assertTrue(ledger.tryBeginExport("sgv:68:1234", 10_000))
        ledger.finishExport("sgv:68:1234", 10_000, emitted = true)

        assertTrue(ledger.tryBeginExport("sgv:68:1234", 11_001))
    }

    @Test
    fun ledgerKeepsOnlyNewestBoundedEntries() {
        var storage: String? = null
        val ledger = PersistedXdripExportLedger(
            readValue = { storage },
            writeValue = { storage = it; true },
            maximumEntries = 2,
        )
        assertTrue(ledger.tryBeginExport("one", 1_000))
        ledger.finishExport("one", 1_000, emitted = true)
        assertTrue(ledger.tryBeginExport("two", 2_000))
        ledger.finishExport("two", 2_000, emitted = true)
        assertTrue(ledger.tryBeginExport("three", 3_000))
        ledger.finishExport("three", 3_000, emitted = true)

        assertTrue(ledger.tryBeginExport("one", 3_000))
        assertFalse(ledger.tryBeginExport("two", 3_000))
        assertFalse(ledger.tryBeginExport("three", 3_000))
    }

    @Test
    fun inFlightIdentityIsClaimedOnce_andFailedEmissionCanRetry() {
        val ledger = InMemoryXdripExportLedger()

        assertTrue(ledger.tryBeginExport("sgv:68:1234", 10_000))
        assertFalse(ledger.tryBeginExport("sgv:68:1234", 10_000))
        ledger.finishExport("sgv:68:1234", 10_000, emitted = false)
        assertTrue(ledger.tryBeginExport("sgv:68:1234", 10_001))
    }
}
