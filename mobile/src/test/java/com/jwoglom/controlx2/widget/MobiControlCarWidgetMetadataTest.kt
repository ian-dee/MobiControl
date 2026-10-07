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
class MobiControlCarWidgetMetadataTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `car provider is a separately named responsive home widget`() {
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, MobiControlCarGlanceWidgetReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        val metadata = receiver.metaData.getInt("android.appwidget.provider")
        assertTrue(metadata != 0)

        context.resources.getXml(metadata).use { parser ->
            parser.moveToCarWidgetStartTag()
            assertEquals("0x1", parser.carWidgetAttribute("widgetCategory"))
            assertEquals("0x3", parser.carWidgetAttribute("resizeMode"))
            assertEquals("2", parser.carWidgetAttribute("targetCellWidth"))
            assertEquals("2", parser.carWidgetAttribute("targetCellHeight"))
        }
        assertEquals("MobiControl Car Glance", receiver.loadLabel(context.packageManager).toString())
    }
}

private const val carWidgetAndroidNamespace = "http://schemas.android.com/apk/res/android"

private fun org.xmlpull.v1.XmlPullParser.moveToCarWidgetStartTag() {
    while (eventType != org.xmlpull.v1.XmlPullParser.START_TAG &&
        eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
    ) {
        next()
    }
}

private fun org.xmlpull.v1.XmlPullParser.carWidgetAttribute(name: String): String? =
    getAttributeValue(carWidgetAndroidNamespace, name)
