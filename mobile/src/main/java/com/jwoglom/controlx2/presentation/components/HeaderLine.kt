package com.jwoglom.controlx2.presentation.components

import com.jwoglom.controlx2.presentation.theme.fieldkit.FieldKitMaterial
import com.jwoglom.controlx2.presentation.theme.fieldkit.fieldKitMaterial

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.MobiControlTheme

@Composable
fun HeaderLine(
    text: String
) {
    val tokens = MobiControlTheme.tokens
    if (!tokens.usesTechnicalChrome) {
        Line(
            text,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(all = 20.dp),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.chassis)
            .then(if (tokens.isFieldKit) Modifier.fieldKitMaterial(FieldKitMaterial.CHASSIS) else Modifier)
            .border(width = tokens.keylineWidth, color = tokens.keyline)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(tokens.accent, tokens.controlShape)
                .border(tokens.keylineWidth, tokens.keyline, tokens.controlShape),
        )
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "SYSTEM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
