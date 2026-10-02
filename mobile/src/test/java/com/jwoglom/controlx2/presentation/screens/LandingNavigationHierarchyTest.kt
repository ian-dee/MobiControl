package com.jwoglom.controlx2.presentation.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LandingNavigationHierarchyTest {
    @Test
    fun `dashboard is the root destination`() {
        assertNull(landingBackDestination(LandingSection.DASHBOARD))
    }

    @Test
    fun `primary tabs return to dashboard`() {
        listOf(
            LandingSection.CARB_CAPTURE,
            LandingSection.NOTIFICATIONS,
            LandingSection.ACTIONS,
            LandingSection.SETTINGS,
        ).forEach { section ->
            assertEquals(LandingSection.DASHBOARD, landingBackDestination(section))
        }
    }

    @Test
    fun `action detail screens return to actions`() {
        listOf(
            LandingSection.CGM_ACTIONS,
            LandingSection.CARTRIDGE_ACTIONS,
            LandingSection.PROFILE_ACTIONS,
            LandingSection.QUICK_BOLUS_SETTINGS_ACTIONS,
            LandingSection.CONTROLIQ_SETTINGS_ACTIONS,
            LandingSection.SAFETY_LIMITS_ACTIONS,
        ).forEach { section ->
            assertEquals(LandingSection.ACTIONS, landingBackDestination(section))
        }
    }

    @Test
    fun `settings detail screens return to settings`() {
        listOf(
            LandingSection.SOUND_SETTINGS_ACTIONS,
            LandingSection.ACKNOWLEDGEMENTS,
            LandingSection.PAIRED_PUMP,
            LandingSection.PUMP_HISTORY,
            LandingSection.GRAPH_DISPLAY,
            LandingSection.DEBUG,
            LandingSection.NIGHTSCOUT_SETTINGS,
            LandingSection.XDRIP_SETTINGS,
            LandingSection.GDH_SETTINGS,
        ).forEach { section ->
            assertEquals(LandingSection.SETTINGS, landingBackDestination(section))
        }
    }

    @Test
    fun `feature flags returns to debug before settings`() {
        assertEquals(
            LandingSection.DEBUG,
            landingBackDestination(LandingSection.FEATURE_FLAGS),
        )
    }

    @Test
    fun `every landing section is covered by the hierarchy`() {
        LandingSection.entries.forEach { section ->
            if (section == LandingSection.DASHBOARD) {
                assertNull(landingBackDestination(section))
            } else {
                checkNotNull(landingBackDestination(section)) {
                    "Missing back destination for $section"
                }
            }
        }
    }
}
