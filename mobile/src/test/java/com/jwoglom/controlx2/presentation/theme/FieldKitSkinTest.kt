package com.jwoglom.controlx2.presentation.theme

import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSkin
import org.junit.Assert.*
import org.junit.Test

class FieldKitSkinTest {
    @Test fun `restored module keeps stable selection identity`() {
        assertEquals("field-kit", FieldKitSkin.id)
        assertEquals(AppSkin.FIELD_KIT, AppSkin.fromPersistedValue("field-kit"))
        assertEquals(AppSkin.CURRENT, AppSkin.fromPersistedValue("bauhaus"))
        assertEquals(listOf("POR", "Instrument", "Field Kit"), AppSkin.entries.map { it.displayName })
    }

    @Test fun `field kit palette is independent with zero elevation`() {
        val kit = FieldKitSkin.tokens()
        assertTrue(kit.isFieldKit)
        assertTrue(kit.usesRefinedChrome)
        assertFalse(instrumentSkinTokens().isFieldKit)
        assertNotEquals(instrumentSkinTokens().canvas, kit.canvas)
        assertNotEquals(FieldKitSkin.colors.error, kit.accent)
        assertEquals(0f, kit.panelElevation.value)
    }
}
