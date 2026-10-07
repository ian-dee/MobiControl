package com.jwoglom.controlx2.presentation.theme

import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitMaterial
import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitMaterial
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitFrame
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitFrameInk
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitFrameMuted
import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitSans

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Divider
import com.jwoglom.controlx2.presentation.theme.SkinIcon as Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jwoglom.controlx2.presentation.components.HeaderLine

// Shared native hierarchy. Skins supply presentation; native controls own every action.
@Composable
private fun isInstrumentMenu() = MobiControlTheme.tokens.skin == AppSkin.INSTRUMENT

@Composable
private fun menuGroupColor() = if (MobiControlTheme.tokens.isFieldKit) FieldKitFrame else if (isInstrumentMenu()) InstrumentGroupBackground
    else MaterialTheme.colorScheme.surfaceVariant

@Composable
private fun menuGroupInk() = if (MobiControlTheme.tokens.isFieldKit) FieldKitFrameInk else if (isInstrumentMenu()) InstrumentGroupContent
    else MaterialTheme.colorScheme.onSurface

@Composable
private fun menuGroupMuted() = if (MobiControlTheme.tokens.isFieldKit) FieldKitFrameMuted else if (isInstrumentMenu()) InstrumentGroupMuted
    else MaterialTheme.colorScheme.onSurfaceVariant

// The Instrument row is inset 8 dp from a 12 dp parent: its matching radius is 4 dp.
@Composable
private fun menuCornerRadius() = if (MobiControlTheme.tokens.isFieldKit) 14.dp else if (isInstrumentMenu()) 12.dp else 20.dp

@Composable
private fun menuRowRadius() = if (MobiControlTheme.tokens.isFieldKit) 6.dp else if (isInstrumentMenu()) 4.dp else 12.dp

/** Menu typography is scoped here; Dashboard retains its original type sizes and composition. */
@Composable
internal fun PorMenuPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (!MobiControlTheme.tokens.usesRefinedChrome) {
        Column(modifier = modifier, content = content)
        return
    }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    MaterialTheme(
        colorScheme = colors,
        typography = type.copy(
            bodyLarge = type.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = type.bodyMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
            bodySmall = type.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            titleMedium = type.titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        ),
    ) {
        Surface(modifier = modifier, color = colors.background) {
            Column(Modifier.then(if (MobiControlTheme.tokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.CHASSIS, edge = false) else Modifier), content = content)
        }
    }
}

/** Each native lazy item paints a piece of the same enclosing group, without nesting lists. */
@Composable
internal fun PorMenuItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    if (!MobiControlTheme.tokens.usesRefinedChrome) {
        ListItem(headlineContent, modifier, supportingContent = supportingContent,
            leadingContent = leadingContent, trailingContent = trailingContent)
        return
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        .background(menuGroupColor()).padding(horizontal = 8.dp)) {
        ListItem(
            headlineContent = {
                ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)) {
                    headlineContent()
                }
            },
            supportingContent = supportingContent,
            leadingContent = leadingContent?.let { slot -> {
                Box(Modifier.size(30.dp).background(MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(if (MobiControlTheme.tokens.isFieldKit) 5.dp else if (isInstrumentMenu()) 4.dp else 9.dp)).then(
                        if (MobiControlTheme.tokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.CHASSIS, radius = 5f) else Modifier), contentAlignment = Alignment.Center) { slot() }
            } },
            trailingContent = trailingContent ?: {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    modifier = Modifier.size(18.dp))
            },
            modifier = Modifier.clip(RoundedCornerShape(menuRowRadius())).then(
                if (MobiControlTheme.tokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.ENAMEL, radius = 6f) else Modifier).then(modifier),
            colors = ListItemDefaults.colors(
                containerColor = if (MobiControlTheme.tokens.isFieldKit) Color.Transparent else MaterialTheme.colorScheme.surface,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                leadingIconColor = MaterialTheme.colorScheme.primary,
                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
internal fun PorMenuDivider() {
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        Spacer(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(menuGroupColor()).height(5.dp))
    } else Divider()
}

@Composable
internal fun PorMenuPageDivider() {
    if (MobiControlTheme.tokens.usesRefinedChrome) Spacer(Modifier.height(4.dp)) else Divider()
}

@Composable
internal fun PorMenuGroupEnd() {
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        Spacer(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .background(menuGroupColor(),
                RoundedCornerShape(bottomStart = menuCornerRadius(), bottomEnd = menuCornerRadius())).height(16.dp))
    }
}

@Composable
internal fun PorMenuPageHeader(title: String) {
    if (!MobiControlTheme.tokens.usesRefinedChrome) {
        HeaderLine(title)
        return
    }
    Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, letterSpacing = (-0.6).sp),
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
        fontFamily = if (MobiControlTheme.tokens.isFieldKit) FieldKitSans else if (isInstrumentMenu()) FontFamily.SansSerif else null)
}

/** Always expanded. The heading and its children form one quiet outer card. */
@Composable
internal fun PorMenuCategoryHeader(title: String, description: String, firstGroup: Boolean = false) {
    if (!firstGroup) PorMenuGroupEnd()
    val icon = when (title) {
        "Notifications" -> Icons.Filled.Notifications
        "Display" -> Icons.Filled.Palette
        "Carb Capture" -> Icons.Filled.CameraAlt
        "Data Connections" -> Icons.Filled.CloudSync
        "Therapy Controls" -> Icons.Filled.Tune
        else -> Icons.Filled.Settings
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        shape = RoundedCornerShape(topStart = menuCornerRadius(), topEnd = menuCornerRadius()),
        color = menuGroupColor(),
        contentColor = menuGroupInk(),
    ) {
        val instrument = isInstrumentMenu()
        val fieldKit = MobiControlTheme.tokens.isFieldKit
        Row(Modifier.then(if (fieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.GRAPHITE, radius = 14f) else Modifier).then(if (instrument) Modifier.drawBehind {
            drawRect(InstrumentOrange, size = Size(3.dp.toPx(), size.height))
        } else Modifier).padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(
                if (instrument || fieldKit) Color.Transparent else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(if (instrument) 4.dp else 9.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null,
                    tint = if (fieldKit) MobiControlTheme.tokens.indicatorYellow else if (instrument) InstrumentOrange else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = if (fieldKit) MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FieldKitSans, fontSize = 17.sp, letterSpacing = 0.5.sp,
                ) else if (instrument) MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                ) else MaterialTheme.typography.titleMedium,
                    fontWeight = if (instrument) FontWeight.Medium else FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = menuGroupMuted())
            }
        }
    }
}

@Composable
internal fun PorMenuNotice(content: @Composable () -> Unit) {
    if (!MobiControlTheme.tokens.usesRefinedChrome) {
        content()
        return
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(menuCornerRadius()), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Notification content, ordering, swipe state and callbacks remain in the original component. */
@Composable
internal fun PorNotificationSurface(kind: String = "Notification", urgent: Boolean = false,
    content: @Composable () -> Unit) {
    if (!MobiControlTheme.tokens.usesRefinedChrome) {
        content()
        return
    }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(menuCornerRadius()),
        color = if (urgent) MaterialTheme.colorScheme.errorContainer else menuGroupColor()) {
        Column(Modifier.then(if (MobiControlTheme.tokens.isFieldKit) Modifier.fieldKitMaterial(
            if (urgent) FieldKitMaterial.CONTROL else FieldKitMaterial.GRAPHITE, radius = 14f) else Modifier)) {
            Text(kind, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = if (MobiControlTheme.tokens.isFieldKit) FieldKitFrameInk else if (urgent) MaterialTheme.colorScheme.onErrorContainer else menuGroupMuted())
            Surface(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), shape = RoundedCornerShape(if (MobiControlTheme.tokens.isFieldKit) 6.dp else if (isInstrumentMenu()) 4.dp else 13.dp),
                color = MaterialTheme.colorScheme.surface) { content() }
        }
    }
}

@Composable
internal fun Modifier.porDeliveryBanner(): Modifier =
    if (MobiControlTheme.tokens.usesRefinedChrome) {
        padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(if (MobiControlTheme.tokens.isFieldKit) 14.dp else if (isInstrumentMenu()) 8.dp else 16.dp))
            .then(if (MobiControlTheme.tokens.isFieldKit) Modifier.border(1.dp,
                MobiControlTheme.tokens.mutedKeyline, RoundedCornerShape(14.dp)) else Modifier)
    } else this

@Composable
internal fun porDeliveryBannerColor(original: Color, suspended: Boolean): Color {
    if (!MobiControlTheme.tokens.usesRefinedChrome) return original
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (suspended) {
        if (dark) Color(0xFF253E36) else Color(0xFFEBF6EF)
    } else MaterialTheme.colorScheme.errorContainer
}
