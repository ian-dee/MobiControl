package com.jwoglom.controlx2.presentation.screens.sections

import com.jwoglom.controlx2.shared.enums.UserMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionsDeveloperPreviewModeTest {
    @Test
    fun enablesExerciseFromStandardMode() {
        assertEquals(
            UserMode.EXERCISE,
            developerPreviewModeAfterToggle(UserMode.NONE, UserMode.EXERCISE, checked = true),
        )
    }

    @Test
    fun disablesActiveExerciseMode() {
        assertEquals(
            UserMode.NONE,
            developerPreviewModeAfterToggle(UserMode.EXERCISE, UserMode.EXERCISE, checked = false),
        )
    }

    @Test
    fun enablesSleepFromStandardMode() {
        assertEquals(
            UserMode.SLEEP,
            developerPreviewModeAfterToggle(UserMode.NONE, UserMode.SLEEP, checked = true),
        )
    }

    @Test
    fun rejectsEnablingSleepWhileExerciseIsActive() {
        assertNull(
            developerPreviewModeAfterToggle(UserMode.EXERCISE, UserMode.SLEEP, checked = true),
        )
    }
}
