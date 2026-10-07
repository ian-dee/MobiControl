package com.jwoglom.controlx2.presentation

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSegmentResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.IDPSettingsResponse
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ProfileStatusResponse

/** The profile responses received during the current source-of-truth pump read. */
data class ProfileReadProgress(
    val totalProfiles: Int? = null,
    val profileNames: Map<Int, String> = emptyMap(),
    val expectedSegmentsByProfile: Map<Int, Int> = emptyMap(),
    val receivedSegments: Set<Pair<Int, Int>> = emptySet(),
) {
    val profilesRead: Int get() = profileNames.size
    val totalSegments: Int get() = expectedSegmentsByProfile.values.sum()
    val segmentsRead: Int get() = receivedSegments.size

    fun process(message: Message): ProfileReadProgress = when (message) {
        is ProfileStatusResponse -> ProfileReadProgress(totalProfiles = message.numberOfProfiles)
        is IDPSettingsResponse -> copy(
            profileNames = profileNames + (message.idpId to message.name),
            expectedSegmentsByProfile = expectedSegmentsByProfile +
                (message.idpId to message.numberOfProfileSegments),
        )
        is IDPSegmentResponse -> copy(
            receivedSegments = receivedSegments + (message.idpId to message.segmentIndex),
        )
        else -> this
    }
}
