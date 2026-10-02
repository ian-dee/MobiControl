package com.jwoglom.controlx2.presentation.screens.sections

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jwoglom.controlx2.presentation.ProfileReadProgress
import com.jwoglom.pumpx2.pump.messages.builders.IDPManager
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class ProfileReadPhase {
    CHECKING_PUMP,
    READING_PROFILE_SETTINGS,
    READING_TIME_SEGMENTS,
    COMPLETE,
    EMPTY,
}

internal data class ProfileReadStatus(
    val phase: ProfileReadPhase,
    val title: String,
    val detail: String,
    val profilesRead: Int = 0,
    val totalProfiles: Int? = null,
    val segmentsRead: Int = 0,
    val totalSegments: Int? = null,
    val complete: Boolean = false,
)

/**
 * Converts PumpX2's next required profile messages into a user-visible read milestone.
 * Completion is only reported after IDPManager has received every profile setting and segment.
 */
internal fun profileReadStatus(
    manager: IDPManager?,
    progress: ProfileReadProgress = ProfileReadProgress(),
): ProfileReadStatus {
    val current = manager ?: IDPManager()

    if (current.isComplete) {
        val profiles = current.profiles.orEmpty()
        if (profiles.isEmpty()) {
            return ProfileReadStatus(
                phase = ProfileReadPhase.EMPTY,
                title = "Pump reports no profiles",
                detail = "The connected pump does not have a Personal Profile.",
                totalProfiles = 0,
                totalSegments = 0,
                complete = true,
            )
        }

        val totalSegments = profiles.sumOf { it.segments.size }
        // Pump profile slot 0 is the active profile; IDPManager preserves pump slot order.
        val activeName = profiles.firstOrNull()?.idpSettingsResponse?.name
        val activeDetail = activeName?.let { " • Active: $it" }.orEmpty()
        return ProfileReadStatus(
            phase = ProfileReadPhase.COMPLETE,
            title = "Profiles loaded from pump",
            detail = "${profiles.size} ${profileWord(profiles.size)} • " +
                "$totalSegments ${segmentWord(totalSegments)}$activeDetail",
            profilesRead = profiles.size,
            totalProfiles = profiles.size,
            segmentsRead = totalSegments,
            totalSegments = totalSegments,
            complete = true,
        )
    }

    val totalProfiles = progress.totalProfiles
    if (totalProfiles == null) {
        return ProfileReadStatus(
            phase = ProfileReadPhase.CHECKING_PUMP,
            title = "Checking pump for profiles",
            detail = "Requesting the profile list from the connected pump.",
        )
    }

    if (progress.profilesRead < totalProfiles) {
        return ProfileReadStatus(
            phase = ProfileReadPhase.READING_PROFILE_SETTINGS,
            title = "Reading pump profiles",
            detail = "Profile details: ${progress.profilesRead} of $totalProfiles received.",
            profilesRead = progress.profilesRead,
            totalProfiles = totalProfiles,
        )
    }

    return ProfileReadStatus(
        phase = ProfileReadPhase.READING_TIME_SEGMENTS,
        title = "Reading pump profiles",
        detail = "Time segments: ${progress.segmentsRead} of ${progress.totalSegments} received across " +
            "$totalProfiles ${profileWord(totalProfiles)}.",
        profilesRead = progress.profilesRead,
        totalProfiles = totalProfiles,
        segmentsRead = progress.segmentsRead,
        totalSegments = progress.totalSegments,
    )
}

private fun profileWord(count: Int) = if (count == 1) "profile" else "profiles"

private fun segmentWord(count: Int) = if (count == 1) "time segment" else "time segments"

@Composable
internal fun ProfileReadStatusIndicator(
    status: ProfileReadStatus,
    errorMessage: String?,
    showingCachedProfiles: Boolean = false,
    cachedVerifiedAtEpochMillis: Long? = null,
    onRetry: () -> Unit,
) {
    val hasError = errorMessage != null
    val headline = when {
        hasError && showingCachedProfiles -> "Profile refresh stopped"
        hasError -> "Profile read stopped"
        showingCachedProfiles -> "Refreshing profiles from pump"
        else -> status.title
    }
    val cacheDetail = cachedVerifiedAtEpochMillis?.let {
        when {
            showingCachedProfiles ->
                "Showing the pump-verified copy from ${formatProfileCacheTime(it)}. " +
                    "Changes unlock when the refresh finishes."
            status.complete -> "Verified directly with the pump at ${formatProfileCacheTime(it)}."
            else -> null
        }
    }
    val supportingText = listOfNotNull(errorMessage ?: status.detail, cacheDetail)
        .joinToString("\n")

    ListItem(
        headlineContent = {
            Text(headline, fontWeight = FontWeight.SemiBold)
        },
        supportingContent = { Text(supportingText) },
        leadingContent = {
            when {
                hasError -> Icon(
                    Icons.Filled.Error,
                    contentDescription = "Profile read error",
                    tint = MaterialTheme.colorScheme.error,
                )
                status.complete -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Profile read complete",
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                )
            }
        },
        trailingContent = {
            if (hasError || status.complete) {
                TextButton(onClick = onRetry) {
                    Text(if (hasError) "Retry" else "Read Again")
                }
            }
        },
    )
    HorizontalDivider()
}

private val PROFILE_CACHE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())

internal fun formatProfileCacheTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(PROFILE_CACHE_TIME_FORMATTER)
