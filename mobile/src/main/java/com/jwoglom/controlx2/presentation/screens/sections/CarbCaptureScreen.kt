package com.jwoglom.controlx2.presentation.screens.sections

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.jwoglom.controlx2.BuildConfig
import com.jwoglom.controlx2.presentation.carb.CarbCaptureAnalyzer
import com.jwoglom.controlx2.presentation.carb.CarbCaptureEstimate
import com.jwoglom.controlx2.presentation.carb.DeveloperPreviewCarbCaptureAnalyzer
import com.jwoglom.controlx2.presentation.carb.MAX_NOTE_LENGTH
import com.jwoglom.controlx2.presentation.carb.RemoteCarbCaptureAnalyzer
import com.jwoglom.controlx2.presentation.carb.handleChatGptCarbCaptureHandoff
import com.jwoglom.controlx2.presentation.carb.isChatGptInstalled
import com.jwoglom.controlx2.presentation.carb.openCarbCaptureInChatGpt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CarbCaptureScreen(
    innerPadding: PaddingValues,
    developerPreviewEnabled: Boolean,
    onClose: () -> Unit,
    analyzerOverride: CarbCaptureAnalyzer? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val remoteAnalysisConfigured = developerPreviewEnabled ||
        BuildConfig.CARB_CAPTURE_ENDPOINT.trim().startsWith("https://")
    val chatGptInstalled = remember(context) { isChatGptInstalled(context) }
    val analyzer = remember(developerPreviewEnabled, analyzerOverride) {
        analyzerOverride ?: if (developerPreviewEnabled) {
            DeveloperPreviewCarbCaptureAnalyzer()
        } else {
            RemoteCarbCaptureAnalyzer()
        }
    }
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    val capturedFileState = remember { mutableStateOf<File?>(null) }
    var capturedFile by capturedFileState
    var note by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var capturing by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    var estimate by remember { mutableStateOf<CarbCaptureEstimate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val preserveCaptureForHandoff = remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            error = "Camera permission is required to photograph a meal."
        }
    }

    LaunchedEffect(permissionGranted, permissionRequested) {
        if (!permissionGranted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        // Captures are private cache artifacts, not a meal-photo library. Remove anything left
        // behind by a prior process crash before beginning a new workflow.
        withContext(Dispatchers.IO) {
            File(context.cacheDir, "carb-capture").listFiles()?.forEach(File::delete)
        }
    }

    DisposableEffect(permissionGranted, lifecycleOwner, cameraController) {
        if (permissionGranted) {
            runCatching { cameraController.bindToLifecycle(lifecycleOwner) }
                .onFailure { error = "The camera could not be started." }
        }
        onDispose { cameraController.unbind() }
    }

    DisposableEffect(Unit) {
        onDispose {
            // ChatGPT reads this file through a temporary content URI after its activity opens.
            // Keep a successful handoff alive until the next Carb Capture session cleans the cache.
            if (!preserveCaptureForHandoff.value) capturedFileState.value?.delete()
        }
    }

    fun closeCapture() {
        capturedFile?.delete()
        capturedFile = null
        onClose()
    }

    fun openInChatGpt(photo: File) {
        error = null
        handleChatGptCarbCaptureHandoff(
            openChatGpt = { openCarbCaptureInChatGpt(context, photo, note.text) },
            onOpened = {
                preserveCaptureForHandoff.value = true
                onClose()
            },
            onFailure = { error = "ChatGPT could not be opened." },
        )
    }

    if (estimate != null) {
        CarbCaptureResult(
            estimate = checkNotNull(estimate),
            imageFile = capturedFile,
            onClose = ::closeCapture,
            innerPadding = innerPadding,
        )
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Photograph your meal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Keep the whole portion visible. A package label or note can improve " +
                        "the estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = capturedFile
                    when {
                        photo != null -> {
                            CapturedImage(
                                file = photo,
                                modifier = Modifier.fillMaxSize(),
                            )
                            FilledIconButton(
                                onClick = {
                                    photo.delete()
                                    capturedFile = null
                                    error = null
                                },
                                enabled = !analyzing,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp),
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retake photo")
                            }
                        }
                        permissionGranted -> {
                            AndroidView(
                                factory = { cameraContext ->
                                    PreviewView(cameraContext).apply {
                                        scaleType = PreviewView.ScaleType.FILL_CENTER
                                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                        controller = cameraController
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                            )
                            FilledIconButton(
                                onClick = {
                                    error = null
                                    capturing = true
                                    val directory = File(context.cacheDir, "carb-capture").apply {
                                        mkdirs()
                                    }
                                    val outputFile = File(
                                        directory,
                                        "meal-${System.currentTimeMillis()}.jpg",
                                    )
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                    cameraController.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(
                                                outputFileResults: ImageCapture.OutputFileResults,
                                            ) {
                                                capturing = false
                                                capturedFile?.delete()
                                                capturedFile = outputFile
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                capturing = false
                                                outputFile.delete()
                                                error = "The photo could not be captured. Try again."
                                            }
                                        },
                                    )
                                },
                                enabled = !capturing,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(20.dp)
                                    .size(68.dp),
                                shape = CircleShape,
                            ) {
                                if (capturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.CameraAlt,
                                        contentDescription = "Capture meal photo",
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                                Text("Camera access is off")
                                Button(
                                    onClick = {
                                        error = null
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                ) {
                                    Text("Allow Camera")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = note,
                onValueChange = { value ->
                    if (value.text.length <= MAX_NOTE_LENGTH) note = value
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional note") },
                placeholder = { Text("Example: restaurant portion, dressing on the side") },
                supportingText = { Text("${note.text.length}/$MAX_NOTE_LENGTH") },
                minLines = 2,
                maxLines = 4,
                enabled = !analyzing,
            )
        }

        error?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        if (remoteAnalysisConfigured) {
            item {
                Button(
                    onClick = {
                        val photo = capturedFile ?: return@Button
                        analyzing = true
                        error = null
                        scope.launch {
                            runCatching { analyzer.analyze(photo, note.text) }
                                .onSuccess { estimate = it }
                                .onFailure {
                                    error = it.message ?: "The estimate could not be completed."
                                }
                            analyzing = false
                        }
                    },
                    enabled = capturedFile != null && !analyzing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (analyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Estimating carbohydrates…")
                    } else {
                        Text("Submit for Estimate", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            if (remoteAnalysisConfigured) {
                OutlinedButton(
                    onClick = {
                        val photo = capturedFile ?: return@OutlinedButton
                        openInChatGpt(photo)
                    },
                    enabled = capturedFile != null && !analyzing && chatGptInstalled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Open in ChatGPT", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = {
                        val photo = capturedFile ?: return@Button
                        openInChatGpt(photo)
                    },
                    enabled = capturedFile != null && !analyzing && chatGptInstalled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Open in ChatGPT", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!remoteAnalysisConfigured) {
            item {
                Text(
                    text = if (chatGptInstalled) {
                        "The photo and prompt will open in ChatGPT. The answer stays in ChatGPT; " +
                            "return to MobiControl when you are finished."
                    } else {
                        "Install the ChatGPT app to analyze the captured meal, or configure the " +
                            "MobiControl analysis server."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                text = "AI estimates can be wrong, especially when portion size or ingredients " +
                    "are unclear. Verify the result using labels or another trusted source. " +
                    "Carb Capture does not send a bolus or change pump settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            OutlinedButton(
                onClick = ::closeCapture,
                enabled = !analyzing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun CarbCaptureResult(
    estimate: CarbCaptureEstimate,
    imageFile: File?,
    onClose: () -> Unit,
    innerPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "AI carb estimate",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        imageFile?.let { file ->
            item {
                CapturedImage(
                    file = file,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Estimated carbohydrates", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${estimate.estimatedCarbsGrams} g",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Likely range: ${estimate.rangeLowGrams}–${estimate.rangeHighGrams} g",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text("Confidence: ${estimate.confidence.displayName}")
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What I see", style = MaterialTheme.typography.titleMedium)
                Text(estimate.description)
            }
        }

        if (estimate.assumptions.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Assumptions", style = MaterialTheme.typography.titleMedium)
                    estimate.assumptions.forEach { assumption ->
                        Text("• $assumption")
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = "Review this estimate before using it. Carb Capture does not send " +
                        "this value to the bolus calculator or the pump.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        item {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Close and Return to Dashboard")
            }
        }
    }
}

@Composable
private fun CapturedImage(file: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = file.path) {
        value = withContext(Dispatchers.IO) { decodePreviewBitmap(file)?.asImageBitmap() }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Captured meal",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}

private fun decodePreviewBitmap(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > 1600 || bounds.outHeight / sampleSize > 1600) {
        sampleSize *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null
    val exif = runCatching { ExifInterface(file) }.getOrNull() ?: return decoded
    if (exif.rotationDegrees == 0 && !exif.isFlipped) return decoded

    val transform = Matrix().apply {
        if (exif.isFlipped) postScale(-1f, 1f)
        if (exif.rotationDegrees != 0) postRotate(exif.rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(
        decoded,
        0,
        0,
        decoded.width,
        decoded.height,
        transform,
        true,
    ).also { oriented ->
        if (oriented !== decoded) decoded.recycle()
    }
}
