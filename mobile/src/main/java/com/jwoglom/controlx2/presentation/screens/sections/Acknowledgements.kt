package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.theme.ControlX2Theme
import com.jwoglom.controlx2.presentation.theme.PorMenuDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuGroupEnd
import com.jwoglom.controlx2.presentation.theme.PorMenuItem
import com.jwoglom.controlx2.presentation.theme.PorMenuPage
import com.jwoglom.controlx2.presentation.theme.PorMenuPageDivider
import com.jwoglom.controlx2.presentation.theme.PorMenuPageHeader
import com.jwoglom.controlx2.presentation.theme.PorMenuNotice
import com.jwoglom.controlx2.presentation.theme.SkinIcon
import com.jwoglom.controlx2.util.AppVersionInfo

internal const val CONTROLX2_REPOSITORY = "https://github.com/jwoglom/controlX2"
internal const val PUMPX2_REPOSITORY = "https://github.com/jwoglom/pumpX2"

internal const val PUMPX2_MIT_LICENSE = """MIT License

Copyright (c) 2022 James Woglom

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

@Composable
fun Acknowledgements(
    innerPadding: PaddingValues = PaddingValues(),
    navigateBack: () -> Unit,
) {
    val pumpX2Version = AppVersionInfo(LocalContext.current).pumpX2

    PorMenuPage(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        PorMenuItem(
            headlineContent = { Text("Back") },
            leadingContent = {
                SkinIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            },
            trailingContent = {},
            modifier = Modifier.clickable(onClick = navigateBack),
        )
        PorMenuPageHeader("Acknowledgements")
        PorMenuPageDivider()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("acknowledgements-page"),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                PorMenuNotice {
                    Text(
                        "MobiControl is a local research fork based on ControlX2 and uses " +
                            "the PumpX2 communication library.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                AcknowledgementSection(
                    title = "ControlX2",
                    body = "Original Android phone and Wear OS application by James Woglom.",
                    repository = CONTROLX2_REPOSITORY,
                )
                PorMenuDivider()
            }

            item {
                AcknowledgementSection(
                    title = "PumpX2",
                    body = "Pump communication library by James Woglom. Bundled version: $pumpX2Version.",
                    repository = PUMPX2_REPOSITORY,
                )
                PorMenuDivider()
            }

            item {
                AcknowledgementSection(
                    title = "PumpX2 license",
                    body = PUMPX2_MIT_LICENSE,
                )
                PorMenuDivider()
            }

            item {
                AcknowledgementSection(
                    title = "Non-affiliation",
                    body = "MobiControl is not affiliated with or supported by Tandem Diabetes Care or Dexcom.",
                )
                PorMenuGroupEnd()
            }
        }
    }
}

@Composable
private fun AcknowledgementSection(
    title: String,
    body: String,
    repository: String? = null,
) {
    PorMenuItem(
        headlineContent = {
            Text(title, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Text(body)
                if (repository != null) {
                    Text(
                        repository,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        trailingContent = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun AcknowledgementsPreview() {
    ControlX2Theme {
        Acknowledgements(navigateBack = {})
    }
}
