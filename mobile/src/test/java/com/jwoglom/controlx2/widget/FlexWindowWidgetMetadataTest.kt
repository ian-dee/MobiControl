package com.jwoglom.controlx2.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FlexWindowWidgetMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `compact cover provider declares samsung sub screen metadata`() {
        assertCoverProvider(
            provider = MobiControlCoverCompactWidgetProvider::class.java,
            expectedMinWidthDp = 150,
            expectedMinHeightDp = 133,
            expectedTargetWidth = "2",
        )
    }

    @Test
    fun `wide cover provider declares samsung sub screen metadata`() {
        assertCoverProvider(
            provider = MobiControlCoverWideWidgetProvider::class.java,
            expectedMinWidthDp = 312,
            expectedMinHeightDp = 133,
            expectedTargetWidth = "4",
        )
    }

    private fun assertCoverProvider(
        provider: Class<out MobiControlWidgetProvider>,
        expectedMinWidthDp: Int,
        expectedMinHeightDp: Int,
        expectedTargetWidth: String,
    ) {
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, provider),
            PackageManager.GET_META_DATA,
        )
        val androidMetadata = receiver.metaData.getInt("android.appwidget.provider")
        val samsungMetadata = receiver.metaData.getInt("com.samsung.android.appwidget.provider")
        assertTrue(androidMetadata != 0)
        assertTrue(samsungMetadata != 0)

        context.resources.getXml(androidMetadata).use { parser ->
            parser.moveToStartTag()
            assertEquals("0x2", parser.attribute(androidNamespace, "widgetCategory"))
            assertEquals(expectedMinWidthDp, parser.dimensionDp("minWidth"))
            assertEquals(expectedMinHeightDp, parser.dimensionDp("minHeight"))
            assertEquals(expectedTargetWidth, parser.attribute(androidNamespace, "targetCellWidth"))
            assertEquals("2", parser.attribute(androidNamespace, "targetCellHeight"))
        }
        context.resources.getXml(samsungMetadata).use { parser ->
            parser.moveToStartTag()
            assertEquals("samsung-appwidget-provider", parser.name)
            assertEquals("sub_screen", parser.attribute(null, "display"))
        }
    }
}

private const val androidNamespace = "http://schemas.android.com/apk/res/android"

private fun org.xmlpull.v1.XmlPullParser.moveToStartTag() {
    while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG &&
        eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
    ) {
        next()
    }
}

private fun org.xmlpull.v1.XmlPullParser.attribute(namespace: String?, name: String): String? =
    getAttributeValue(namespace, name)

private fun org.xmlpull.v1.XmlPullParser.dimensionDp(name: String): Int =
    requireNotNull(attribute(androidNamespace, name))
        .removeSuffix("dip")
        .removeSuffix("dp")
        .toFloat()
        .toInt()
