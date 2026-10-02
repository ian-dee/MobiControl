package com.jwoglom.controlx2.sync.xdrip

import android.content.SharedPreferences

internal interface XdripExportLedger {
    /** Atomically claims an identity within this process. */
    fun tryBeginExport(identity: String, nowMillis: Long): Boolean

    /** Completes the claim, persisting it only when Android accepted the broadcast call. */
    fun finishExport(identity: String, nowMillis: Long, emitted: Boolean)
}

/**
 * Small durable ledger for receiver-emitted xDrip events.
 *
 * The pump may replay the same history after reconnect and the Android process may be recreated.
 * Keeping a bounded, time-pruned identity list prevents those ordinary events from creating a
 * second xDrip entry without turning the xDrip exporter into another clinical history database.
 */
internal class PersistedXdripExportLedger(
    private val readValue: () -> String?,
    private val writeValue: (String) -> Boolean,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS,
    private val maximumEntries: Int = DEFAULT_MAXIMUM_ENTRIES,
) : XdripExportLedger {
    constructor(prefs: SharedPreferences) : this(
        readValue = { prefs.getString(PREF_LEDGER, null) },
        writeValue = { prefs.edit().putString(PREF_LEDGER, it).commit() },
    )

    private val lock = Any()
    private val inFlight = mutableSetOf<String>()

    override fun tryBeginExport(identity: String, nowMillis: Long): Boolean = synchronized(lock) {
        if (identity in inFlight || readEntries(nowMillis).containsKey(identity)) {
            false
        } else {
            inFlight += identity
            true
        }
    }

    override fun finishExport(identity: String, nowMillis: Long, emitted: Boolean) {
        synchronized(lock) {
            inFlight -= identity
            if (!emitted) return
            val entries = readEntries(nowMillis)
            entries.remove(identity)
            entries[identity] = nowMillis
            while (entries.size > maximumEntries) {
                entries.remove(entries.entries.first().key)
            }
            writeValue(
                entries.entries.joinToString("\n") { (eventIdentity, emittedAt) ->
                    "$emittedAt|$eventIdentity"
                }
            )
        }
    }

    private fun readEntries(nowMillis: Long): LinkedHashMap<String, Long> {
        val oldestAllowed = nowMillis - retentionMillis
        val parsed = readValue()
            .orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('|')
                if (separator <= 0 || separator == line.lastIndex) return@mapNotNull null
                val emittedAt = line.substring(0, separator).toLongOrNull()
                    ?: return@mapNotNull null
                val identity = line.substring(separator + 1)
                if (emittedAt < oldestAllowed) null else identity to emittedAt
            }
            .sortedBy { it.second }
            .toList()

        return LinkedHashMap<String, Long>().apply {
            parsed.takeLast(maximumEntries).forEach { (identity, emittedAt) ->
                put(identity, emittedAt)
            }
        }
    }

    companion object {
        private const val PREF_LEDGER = "xdrip-export-ledger-v1"
        private const val DEFAULT_MAXIMUM_ENTRIES = 4_096
        private const val DEFAULT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

internal class InMemoryXdripExportLedger : XdripExportLedger {
    private val lock = Any()
    private val exported = mutableSetOf<String>()
    private val inFlight = mutableSetOf<String>()

    override fun tryBeginExport(identity: String, nowMillis: Long): Boolean = synchronized(lock) {
        if (identity in exported || identity in inFlight) false else inFlight.add(identity)
    }

    override fun finishExport(identity: String, nowMillis: Long, emitted: Boolean) {
        synchronized(lock) {
            inFlight -= identity
            if (emitted) exported += identity
        }
    }
}
