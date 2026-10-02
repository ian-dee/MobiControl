package com.jwoglom.controlx2.presentation.theme

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitGlyph

/** The source control still owns its exact semantics, tint and hit target. */
@Composable
internal fun SkinIcon(imageVector: ImageVector, contentDescription: String?,
    modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val glyph = if (MobiControlTheme.tokens.isFieldKit) {
        fieldKitGlyph(if (contentDescription == "Dashboard") "Dashboard" else imageVector.name.substringAfterLast('.')) ?: imageVector
    } else imageVector
    Icon(glyph, contentDescription, modifier, tint)
}

@Composable
internal fun SkinIcon(painter: Painter, contentDescription: String?,
    modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    if (MobiControlTheme.tokens.isFieldKit && contentDescription == "Paired pump") {
        Icon(requireNotNull(fieldKitGlyph("Pump")), contentDescription, modifier,
            androidx.compose.material3.MaterialTheme.colorScheme.primary)
    } else Icon(painter, contentDescription, modifier, tint)
}
