package com.jwoglom.controlx2.shared

/**
 * Build-time gates for integrations that are intentionally kept in source but are not currently
 * part of the supported app surface.
 *
 * This is deliberately not a user- or developer-editable preference. Re-enabling an integration
 * requires a reviewed source change and a new build.
 */
object IntegrationGates {
    const val WATCH_INTEGRATION_ENABLED = false
}
