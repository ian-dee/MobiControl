package com.jwoglom.controlx2.widget

import com.jwoglom.controlx2.presentation.theme.AppSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetSkinPaletteTest {
    @Test
    fun `every app skin has a matching widget palette`() {
        AppSkin.entries.forEach { skin ->
            assertEquals(skin, widgetSkinPalette(skin).skin)
        }
    }

    @Test
    fun `three widget skins have distinct chrome`() {
        val palettes = AppSkin.entries.map(::widgetSkinPalette)

        assertEquals(3, palettes.size)
        assertEquals(3, palettes.map { it.backgroundDrawable }.distinct().size)
        assertEquals(3, palettes.map { it.surfaceColor }.distinct().size)
        assertNotEquals(
            widgetSkinPalette(AppSkin.INSTRUMENT).chart.gridColor,
            widgetSkinPalette(AppSkin.FIELD_KIT).chart.gridColor,
        )
    }
}
