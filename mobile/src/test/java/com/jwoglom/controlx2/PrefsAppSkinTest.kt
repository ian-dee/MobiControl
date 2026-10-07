package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jwoglom.controlx2.presentation.theme.AppSkin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsAppSkinTest {
    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.prefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        prefs.prefs().edit().clear().commit()
    }

    @Test
    fun `missing skin defaults to current`() {
        assertEquals(AppSkin.CURRENT, prefs.appSkin())
    }

    @Test
    fun `instrument skin survives a new Prefs instance`() {
        prefs.setAppSkin(AppSkin.INSTRUMENT)

        assertEquals(AppSkin.INSTRUMENT, Prefs(context).appSkin())
    }

    @Test
    fun `field kit skin survives a new Prefs instance`() {
        prefs.setAppSkin(AppSkin.FIELD_KIT)
        assertEquals(AppSkin.FIELD_KIT, Prefs(context).appSkin())
    }

    @Test
    fun `removed skins safely migrate to POR`() {
        listOf("bauhaus").forEach { removedSkin ->
            prefs.prefs().edit().putString("app-skin-v1", removedSkin).commit()
            assertEquals(AppSkin.CURRENT, prefs.appSkin())
        }
    }

    @Test
    fun `unknown persisted skin safely falls back to current`() {
        prefs.prefs().edit().putString("app-skin-v1", "future-skin").commit()

        assertEquals(AppSkin.CURRENT, prefs.appSkin())
    }
}
