package com.jwoglom.controlx2.presentation.carb

import android.util.Base64
import com.jwoglom.controlx2.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class CarbCaptureEstimate(
    val description: String,
    val estimatedCarbsGrams: Int,
    val rangeLowGrams: Int,
    val rangeHighGrams: Int,
    val confidence: CarbCaptureConfidence,
    val assumptions: List<String>,
)

enum class CarbCaptureConfidence(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
}

fun interface CarbCaptureAnalyzer {
    suspend fun analyze(imageFile: File, note: String): CarbCaptureEstimate
}

class CarbCaptureConfigurationException(message: String) : IllegalStateException(message)

internal class RemoteCarbCaptureAnalyzer(
    private val endpoint: String = BuildConfig.CARB_CAPTURE_ENDPOINT,
    private val accessToken: String = BuildConfig.CARB_CAPTURE_ACCESS_TOKEN,
) : CarbCaptureAnalyzer {
    override suspend fun analyze(imageFile: File, note: String): CarbCaptureEstimate =
        withContext(Dispatchers.IO) {
            val target = endpoint.trim()
            if (target.isBlank()) {
                throw CarbCaptureConfigurationException(
                    "Image analysis is not configured for this build. Configure the Carb Capture " +
                        "server endpoint and rebuild the app.",
                )
            }
            if (!target.startsWith("https://")) {
                throw CarbCaptureConfigurationException(
                    "The Carb Capture server must use a secure HTTPS address.",
                )
            }
            require(imageFile.isFile && imageFile.length() > 0L) {
                "Capture a photo before requesting an estimate."
            }

            val request = JSONObject()
                .put(
                    "image_base64",
                    Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP),
                )
                .put("note", note.trim().take(MAX_NOTE_LENGTH))

            val connection = (URL(target).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 75_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (accessToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $accessToken")
                }
            }

            try {
                connection.outputStream.use { output ->
                    output.write(request.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    .orEmpty()
                if (status !in 200..299) {
                    val serverMessage = runCatching {
                        JSONObject(responseBody).optString("error").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    throw IllegalStateException(
                        serverMessage ?: "Image analysis could not be completed (HTTP $status).",
                    )
                }
                parseCarbCaptureEstimate(responseBody)
            } finally {
                connection.disconnect()
            }
        }
}

internal class DeveloperPreviewCarbCaptureAnalyzer : CarbCaptureAnalyzer {
    override suspend fun analyze(imageFile: File, note: String): CarbCaptureEstimate {
        delay(900)
        return CarbCaptureEstimate(
            description = if (note.isBlank()) {
                "A turkey sandwich, a small apple, and a handful of chips."
            } else {
                "A meal matching the note “${note.trim().take(80)}”: sandwich, fruit, and chips."
            },
            estimatedCarbsGrams = 58,
            rangeLowGrams = 46,
            rangeHighGrams = 72,
            confidence = CarbCaptureConfidence.MEDIUM,
            assumptions = listOf(
                "The sandwich uses two standard slices of bread.",
                "The visible apple and chips are single servings.",
            ),
        )
    }
}

internal fun parseCarbCaptureEstimate(responseBody: String): CarbCaptureEstimate {
    val json = JSONObject(responseBody)
    val estimate = json.getInt("estimated_carbs_grams")
    val low = json.getInt("range_low_grams")
    val high = json.getInt("range_high_grams")
    require(estimate >= 0 && low >= 0 && high >= low) {
        "The analysis server returned an invalid carbohydrate range."
    }
    val confidence = when (json.getString("confidence").lowercase()) {
        "low" -> CarbCaptureConfidence.LOW
        "medium" -> CarbCaptureConfidence.MEDIUM
        "high" -> CarbCaptureConfidence.HIGH
        else -> throw IllegalArgumentException("The analysis server returned invalid confidence.")
    }
    val assumptionsJson = json.optJSONArray("assumptions") ?: JSONArray()
    return CarbCaptureEstimate(
        description = json.getString("description").trim(),
        estimatedCarbsGrams = estimate,
        rangeLowGrams = low,
        rangeHighGrams = high,
        confidence = confidence,
        assumptions = buildList {
            repeat(assumptionsJson.length()) { index ->
                assumptionsJson.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        },
    )
}

internal const val MAX_NOTE_LENGTH = 500
