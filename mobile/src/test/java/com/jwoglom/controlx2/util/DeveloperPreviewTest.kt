package com.jwoglom.controlx2.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperPreviewTest {
    @Test
    fun recognizesAndroidStudioEmulator() {
        assertTrue(
            isEmulatorDevice(
                fingerprint = "google/sdk_gphone64_arm64/emu64a:17/ABC123:userdebug/dev-keys",
                model = "sdk_gphone64_arm64",
                manufacturer = "Google",
                product = "sdk_gphone64_arm64",
                brand = "google",
                device = "emu64a",
            )
        )
    }

    @Test
    fun rejectsPhysicalSamsungDevice() {
        assertFalse(
            isEmulatorDevice(
                fingerprint = "samsung/q8qxxx/q8q:16/ABC123:user/release-keys",
                model = "SM-F766U1",
                manufacturer = "samsung",
                product = "q8qxxx",
                brand = "samsung",
                device = "q8q",
            )
        )
    }
}
