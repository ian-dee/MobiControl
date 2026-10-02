package com.jwoglom.controlx2.presentation.screens.sections.components

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.R
import com.jwoglom.controlx2.presentation.screens.sections.Settings
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.util.AppVersionInfo

@Composable
fun VersionInfo(
    context: Context,
    compact: Boolean = false,
) {
    val ver = AppVersionInfo(context)
    Text(buildAnnotatedString {
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 20.sp)) {
            append(context.getString(R.string.app_name))
            append(" ")
            append(ver.version)
        }
    }, Modifier.padding(start = if (compact) 0.dp else 16.dp, top = if (compact) 0.dp else 16.dp))
    Text(buildAnnotatedString {
        append("with PumpX2 ")
        append(ver.pumpX2)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Build: ")
        }
        append(ver.buildVersion)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("Build time: ")
        }
        append(ver.buildTime)
        append("\n")

        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
            append("PumpX2 build time: ")
        }
        append(ver.pumpX2BuildTime)
        append("\n")
    }, lineHeight = if (compact) 16.sp else 20.sp, fontSize = if (compact) 11.sp else 14.sp, modifier = Modifier.padding(start = if (compact) 0.dp else 16.dp))
}



@Preview(showBackground = true)
@Composable
internal fun VersionInfoDefaultPreview() {
    ControlX2Theme() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            VersionInfo(LocalContext.current)
        }
    }
}
