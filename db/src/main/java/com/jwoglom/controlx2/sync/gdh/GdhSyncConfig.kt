package com.jwoglom.controlx2.sync.gdh

import android.content.SharedPreferences
import androidx.core.content.edit

/** Passive, read-only GlucoDataHandler export configuration. */
data class GdhSyncConfig(
    val enabled: Boolean = false,
    val sendPumpIob: Boolean = true,
) {
    companion object {
        private const val PREF_ENABLED = "gdh_enabled"
        private const val PREF_SEND_PUMP_IOB = "gdh_send_pump_iob"

        fun load(prefs: SharedPreferences): GdhSyncConfig = GdhSyncConfig(
            enabled = prefs.getBoolean(PREF_ENABLED, false),
            sendPumpIob = prefs.getBoolean(PREF_SEND_PUMP_IOB, true),
        )

        fun save(prefs: SharedPreferences, config: GdhSyncConfig) {
            prefs.edit {
                putBoolean(PREF_ENABLED, config.enabled)
                putBoolean(PREF_SEND_PUMP_IOB, config.sendPumpIob)
            }
        }
    }
}
