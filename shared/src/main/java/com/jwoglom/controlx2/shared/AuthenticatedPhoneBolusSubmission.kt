package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.request.control.InitiateBolusRequest
import com.jwoglom.pumpx2.shared.Hex

enum class BolusBgRecordType {
    NONE,
    AUTOMATIC,
    MANUAL,
}

enum class PostBolusRecordPhase {
    STAGED,
    BG_RESPONSE_PENDING,
    CARB_RESPONSE_PENDING,
    BG_REJECTED,
    CARB_REJECTED,
}

enum class PostBolusRecordCommand {
    NONE,
    SEND_BG,
    SEND_CARBS,
}

data class PostBolusRecordTransition(
    val handled: Boolean,
    val persistedPlan: PostBolusRecordPlan?,
    val nextCommand: PostBolusRecordCommand,
)

/** Non-delivery history records bound to one authenticated bolus request. */
data class PostBolusRecordPlan(
    val bolusId: Int,
    val bgMgdl: Int?,
    val bgRecordType: BolusBgRecordType,
    val carbsGrams: Int,
    val phase: PostBolusRecordPhase = PostBolusRecordPhase.STAGED,
    val pumpTimeSecondsSinceReset: Long? = null,
) {
    init {
        require(bolusId > 0)
        require(bgMgdl == null || bgMgdl in 1..0xFFFF)
        require((bgMgdl == null) == (bgRecordType == BolusBgRecordType.NONE))
        require(carbsGrams >= 0)
        when (phase) {
            PostBolusRecordPhase.STAGED -> require(pumpTimeSecondsSinceReset == null)
            PostBolusRecordPhase.BG_RESPONSE_PENDING,
            PostBolusRecordPhase.BG_REJECTED,
            -> {
                require(bgMgdl != null && bgRecordType != BolusBgRecordType.NONE)
                require(pumpTimeSecondsSinceReset != null && pumpTimeSecondsSinceReset > 0)
            }
            PostBolusRecordPhase.CARB_RESPONSE_PENDING,
            PostBolusRecordPhase.CARB_REJECTED,
            -> {
                require(carbsGrams > 0)
                require(pumpTimeSecondsSinceReset != null && pumpTimeSecondsSinceReset > 0)
            }
        }
    }

    fun encode(): ByteArray = listOf(
        "2",
        bolusId,
        bgMgdl ?: -1,
        bgRecordType.name,
        carbsGrams,
        phase.name,
        pumpTimeSecondsSinceReset ?: -1,
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    fun onInitiationAccepted(pumpTime: Long): PostBolusRecordTransition {
        if (phase != PostBolusRecordPhase.STAGED || pumpTime <= 0) {
            return ignoredTransition()
        }
        return when {
            bgMgdl != null && bgRecordType != BolusBgRecordType.NONE -> transitionTo(
                phase = PostBolusRecordPhase.BG_RESPONSE_PENDING,
                pumpTime = pumpTime,
                command = PostBolusRecordCommand.SEND_BG,
            )
            carbsGrams > 0 -> transitionTo(
                phase = PostBolusRecordPhase.CARB_RESPONSE_PENDING,
                pumpTime = pumpTime,
                command = PostBolusRecordCommand.SEND_CARBS,
            )
            else -> PostBolusRecordTransition(
                handled = true,
                persistedPlan = null,
                nextCommand = PostBolusRecordCommand.NONE,
            )
        }
    }

    fun onBgResponse(success: Boolean): PostBolusRecordTransition {
        if (phase != PostBolusRecordPhase.BG_RESPONSE_PENDING) return ignoredTransition()
        if (!success) {
            return PostBolusRecordTransition(
                handled = true,
                persistedPlan = copy(phase = PostBolusRecordPhase.BG_REJECTED),
                nextCommand = PostBolusRecordCommand.NONE,
            )
        }
        return if (carbsGrams > 0) {
            PostBolusRecordTransition(
                handled = true,
                persistedPlan = copy(phase = PostBolusRecordPhase.CARB_RESPONSE_PENDING),
                nextCommand = PostBolusRecordCommand.SEND_CARBS,
            )
        } else {
            PostBolusRecordTransition(
                handled = true,
                persistedPlan = null,
                nextCommand = PostBolusRecordCommand.NONE,
            )
        }
    }

    fun onCarbResponse(success: Boolean): PostBolusRecordTransition {
        if (phase != PostBolusRecordPhase.CARB_RESPONSE_PENDING) return ignoredTransition()
        return if (success) {
            PostBolusRecordTransition(
                handled = true,
                persistedPlan = null,
                nextCommand = PostBolusRecordCommand.NONE,
            )
        } else {
            PostBolusRecordTransition(
                handled = true,
                persistedPlan = copy(phase = PostBolusRecordPhase.CARB_REJECTED),
                nextCommand = PostBolusRecordCommand.NONE,
            )
        }
    }

    private fun transitionTo(
        phase: PostBolusRecordPhase,
        pumpTime: Long,
        command: PostBolusRecordCommand,
    ) = PostBolusRecordTransition(
        handled = true,
        persistedPlan = copy(
            phase = phase,
            pumpTimeSecondsSinceReset = pumpTime,
        ),
        nextCommand = command,
    )

    private fun ignoredTransition() = PostBolusRecordTransition(
        handled = false,
        persistedPlan = this,
        nextCommand = PostBolusRecordCommand.NONE,
    )

    companion object {
        fun decode(bytes: ByteArray): PostBolusRecordPlan? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            return try {
                when {
                    parts.size == 5 && parts[0] == "1" -> PostBolusRecordPlan(
                        bolusId = parts[1].toInt(),
                        bgMgdl = parts[2].toInt().takeIf { it > 0 },
                        bgRecordType = BolusBgRecordType.valueOf(parts[3]),
                        carbsGrams = parts[4].toInt(),
                    )
                    parts.size == 7 && parts[0] == "2" -> PostBolusRecordPlan(
                        bolusId = parts[1].toInt(),
                        bgMgdl = parts[2].toInt().takeIf { it > 0 },
                        bgRecordType = BolusBgRecordType.valueOf(parts[3]),
                        carbsGrams = parts[4].toInt(),
                        phase = PostBolusRecordPhase.valueOf(parts[5]),
                        pumpTimeSecondsSinceReset = parts[6].toLong().takeIf { it > 0 },
                    )
                    else -> null
                }
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/**
 * The device-authenticated phone handoff. The insulin-delivery request remains covered by the
 * existing one-time HMAC before it reaches PumpX2; the attached plan can only add history records
 * after the pump has accepted that same bolus ID.
 */
data class AuthenticatedPhoneBolusSubmission(
    val request: InitiateBolusRequest,
    val postBolusRecords: PostBolusRecordPlan,
) {
    init {
        require(request.bolusID == postBolusRecords.bolusId)
    }

    fun encode(): ByteArray = listOf(
        "1",
        Hex.encodeHexString(PumpMessageSerializer.toBytes(request)),
        Hex.encodeHexString(postBolusRecords.encode()),
    ).joinToString("|").toByteArray(Charsets.UTF_8)

    companion object {
        fun decode(bytes: ByteArray): AuthenticatedPhoneBolusSubmission? {
            val parts = bytes.toString(Charsets.UTF_8).split('|')
            if (parts.size != 3 || parts[0] != "1") return null
            return try {
                val request = PumpMessageSerializer.fromBytes(
                    Hex.decodeHex(parts[1]),
                ) as? InitiateBolusRequest ?: return null
                val plan = PostBolusRecordPlan.decode(Hex.decodeHex(parts[2])) ?: return null
                AuthenticatedPhoneBolusSubmission(request, plan)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
