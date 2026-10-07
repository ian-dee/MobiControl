package com.jwoglom.controlx2.shared

enum class PumpHydrationPhase {
    DISCONNECTED,
    LIVE_STATE_LOADING,
    LIVE_STATE_READY,
    LIVE_STATE_PARTIAL,
}

data class PumpHydrationState(
    val phase: PumpHydrationPhase,
    val updatedAtEpochMillis: Long,
) {
    fun encode(): ByteArray =
        "1|${phase.name}|$updatedAtEpochMillis".toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): PumpHydrationState? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 3 || parts[0] != "1") return null
            return runCatching {
                PumpHydrationState(
                    phase = PumpHydrationPhase.valueOf(parts[1]),
                    updatedAtEpochMillis = parts[2].toLong(),
                )
            }.getOrNull()
        }
    }
}
