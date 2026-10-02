package com.jwoglom.controlx2.presentation.carb

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

internal const val CHATGPT_ANDROID_PACKAGE = "com.openai.chatgpt"

internal fun buildChatGptCarbCapturePrompt(note: String): String = buildString {
    append(
        "Analyze this meal photo. What foods and portions do you see, and how many grams of " +
            "carbohydrates are likely in the full meal? Return the estimated total carbs, a " +
            "plausible low-to-high range, confidence, and the assumptions behind the estimate.",
    )
    note.trim().take(MAX_NOTE_LENGTH).takeIf { it.isNotBlank() }?.let {
        append("\n\nUser note: ")
        append(it)
    }
    append(
        "\n\nThis is an estimate for user review only. Do not initiate a bolus or any other " +
            "medical action.",
    )
}

internal fun buildChatGptCarbCaptureIntent(imageUri: Uri, note: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        setPackage(CHATGPT_ANDROID_PACKAGE)
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TEXT, buildChatGptCarbCapturePrompt(note))
        clipData = ClipData.newRawUri("Meal photo", imageUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

@Suppress("DEPRECATION")
internal fun isChatGptInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(CHATGPT_ANDROID_PACKAGE, 0)
}.isSuccess

internal fun openCarbCaptureInChatGpt(
    context: Context,
    imageFile: File,
    note: String,
): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, context.packageName, imageFile)
    context.startActivity(buildChatGptCarbCaptureIntent(uri, note))
}.isSuccess

internal fun handleChatGptCarbCaptureHandoff(
    openChatGpt: () -> Boolean,
    onOpened: () -> Unit,
    onFailure: () -> Unit,
) {
    if (openChatGpt()) onOpened() else onFailure()
}
