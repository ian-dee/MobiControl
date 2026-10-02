package com.jwoglom.controlx2.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Native Switch owns checked/enabled state, callbacks, animation, size and accessibility. */
@Composable
internal fun SkinSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!MobiControlTheme.tokens.isFieldKit) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Color(0xFF465541), checkedBorderColor = Color(0xFF293B2A),
            checkedThumbColor = Color(0xFFE9E5D2), uncheckedTrackColor = Color(0xFFC2C8B9),
            uncheckedBorderColor = Color(0xFF7E8876), uncheckedThumbColor = Color(0xFF465343),
        ),
        thumbContent = {
            Canvas(Modifier.size(16.dp)) {
                for (i in -1..1) {
                    val x = center.x + i * 3.dp.toPx()
                    drawLine(if (checked) Color(0xFF717963) else Color(0xFF9DA890),
                        Offset(x, center.y - 3.dp.toPx()), Offset(x, center.y + 3.dp.toPx()), 0.8.dp.toPx())
                }
            }
        },
    )
}
