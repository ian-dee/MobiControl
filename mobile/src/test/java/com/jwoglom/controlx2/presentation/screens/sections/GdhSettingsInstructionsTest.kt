package com.jwoglom.controlx2.presentation.screens.sections

import org.junit.Assert.assertTrue
import org.junit.Test

class GdhSettingsInstructionsTest {
    @Test
    fun setupNamesTheExistingGdhReceiverAndDuplicateSourcePrecaution() {
        val instructions = gdhSetupInstructions().joinToString(" ")

        assertTrue(instructions.contains("Sources"))
        assertTrue(instructions.contains("Local applications"))
        assertTrue(instructions.contains("Juggluco"))
        assertTrue(instructions.contains("Webserver off"))
        assertTrue(instructions.contains("Notification Reader"))
        assertTrue(instructions.contains("MobiControl"))
    }
}
