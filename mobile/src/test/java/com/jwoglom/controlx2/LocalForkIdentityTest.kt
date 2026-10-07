package com.jwoglom.controlx2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalForkIdentityTest {

    @Test
    fun `phone build retains the MobiControl local identity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("local.controlx21", context.packageName)
        assertEquals("MobiControl01BD", context.getString(R.string.app_name))
    }
}
