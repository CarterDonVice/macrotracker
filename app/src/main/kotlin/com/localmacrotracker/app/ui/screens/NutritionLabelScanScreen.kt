package com.localmacrotracker.app.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.NutritionLabelViewModel
import java.util.concurrent.ExecutorService
import kotlin.math.abs
import java.util.concurrent.Executors

private enum class LabelScanStep {
    PREVIEW, CROP, ANALYZING, REVIEW
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NutritionLabelScanScreen(
    mealSection: String,
    logDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: NutritionLabelViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var step by remember { mutableStateOf(LabelScanStep.PREVIEW) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropRect by remember { mutableStateOf(Rect(0.1f, 0.1f, 0.9f, 0.9f)) }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (step) {
                            LabelScanStep.PREVIEW -> "Capture Label"
                            LabelScanStep.CROP -> "Crop Region"
                            LabelScanStep.ANALYZING -> "Analyzing…"
                            LabelScanStep.REVIEW -> "Review Values"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            LabelScanStep.PREVIEW -> onBack()
                            LabelScanStep.CROP -> step = LabelScanStep.PREVIEW
                            LabelScanStep.ANALYZING -> { /* wait for analysis */ }
                            LabelScanStep.REVIEW -> step = LabelScanStep.PREVIEW
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!cameraPermission.status.isGranted && step == LabelScanStep.PREVIEW) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                        Text(
                            "Camera permission is needed to photograph nutrition labels.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { cameraPermission.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black)
                        ) { Text("Grant Permission") }
                    }
                }
                return@Scaffold
            }

            when (step) {
                LabelScanStep.PREVIEW -> {
                    CameraPreviewWithCapture(
                        onPhotoCaptured = { bitmap ->
                            capturedBitmap = bitmap
                            step = LabelScanStep.CROP
                        }
                    )
                }

                LabelScanStep.CROP -> {
                    capturedBitmap?.let { bmp ->
                        CropScreen(
                            bitmap = bmp,
                            cropRect = cropRect,
                            onCropRectChanged = { cropRect = it },
                            onAnalyze = {
                                step = LabelScanStep.ANALYZING
                                runOcrOnCrop(
                                    bitmap = bmp,
                                    cropRect = cropRect,
                                    onResult = { text ->
                                        viewModel.parseOcrText(text)
                                        step = LabelScanStep.REVIEW
                                    },
                                    onError = {
                                        step = LabelScanStep.REVIEW
                                    }
                                )
                            }
                        )
                    }
                }

                LabelScanStep.ANALYZING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = AccentGreen)
                            Text("Recognizing nutrition values…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                LabelScanStep.REVIEW -> {
                    ReviewForm(
                        displayName = displayName,
                        draft = draft,
                        onDisplayNameChange = viewModel::setDisplayName,
                        onDraftChange = viewModel::setDraft,
                        onSaveAndAdd = {
                            viewModel.saveAndAddToLog(mealSection, logDate, save = true)
                            onDone()
                        },
                        onAddWithoutSaving = {
                            viewModel.saveAndAddToLog(mealSection, logDate, save = false)
                            onDone()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithCapture(onPhotoCaptured: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var imageCaptureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setTargetResolution(Size(1920, 1080))
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build()
                    imageCaptureUseCase = imageCapture

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Text(
            text = "Align the nutrition facts label in frame",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )

        Button(
            onClick = {
                imageCaptureUseCase?.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        @androidx.camera.core.ExperimentalGetImage
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val rawBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val degrees = image.imageInfo.rotationDegrees.toFloat()
                            val rotated = if (degrees != 0f) {
                                val matrix = Matrix().apply { postRotate(degrees) }
                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                            } else rawBitmap
                            image.close()
                            onPhotoCaptured(rotated)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            exception.printStackTrace()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .size(72.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(30.dp))
        }
    }
}

private enum class DragHandle { NONE, INSIDE, TL, TR, BL, BR }

@Composable
private fun CropScreen(
    bitmap: Bitmap,
    cropRect: Rect,
    onCropRectChanged: (Rect) -> Unit,
    onAnalyze: () -> Unit
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Initialize once from parent; we keep localCrop in sync via onCropRectChanged
    var localCrop by remember { mutableStateOf(cropRect) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Captured nutrition label",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(containerSize) {
                    val handleTouchPx = 44.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (containerSize.width == 0 || containerSize.height == 0) return@awaitEachGesture

                        val w = containerSize.width.toFloat()
                        val h = containerSize.height.toFloat()
                        val px = down.position.x / w
                        val py = down.position.y / h
                        val hx = handleTouchPx / w
                        val hy = handleTouchPx / h

                        // Determine which zone was touched
                        val handle = when {
                            abs(px - localCrop.left) < hx && abs(py - localCrop.top) < hy -> DragHandle.TL
                            abs(px - localCrop.right) < hx && abs(py - localCrop.top) < hy -> DragHandle.TR
                            abs(px - localCrop.left) < hx && abs(py - localCrop.bottom) < hy -> DragHandle.BL
                            abs(px - localCrop.right) < hx && abs(py - localCrop.bottom) < hy -> DragHandle.BR
                            px > localCrop.left && px < localCrop.right && py > localCrop.top && py < localCrop.bottom -> DragHandle.INSIDE
                            else -> DragHandle.NONE
                        }

                        var prevPos = down.position
                        var prevCentroid = down.position
                        var prevDist = 0f
                        var pinchStarted = false

                        var cont = true
                        while (cont) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val active = event.changes.filter { it.pressed }
                            cont = active.isNotEmpty()

                            if (active.size >= 2) {
                                // Two-finger: pinch to resize + translate by centroid movement
                                pinchStarted = true
                                val p1 = active[0].position
                                val p2 = active[1].position
                                val centroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                                val dist = (p1 - p2).getDistance()

                                if (prevDist > 0f) {
                                    val scale = dist / prevDist
                                    val cx = centroid.x / w
                                    val cy = centroid.y / h
                                    val dcx = (centroid.x - prevCentroid.x) / w
                                    val dcy = (centroid.y - prevCentroid.y) / h

                                    // Translate the box, then scale it around the centroid
                                    val tl = localCrop.left + dcx
                                    val tr = localCrop.right + dcx
                                    val tt = localCrop.top + dcy
                                    val tb = localCrop.bottom + dcy

                                    val newLeft = (cx + (tl - cx) * scale).coerceIn(0f, 0.95f)
                                    val newRight = (cx + (tr - cx) * scale).coerceIn(0.05f, 1f)
                                    val newTop = (cy + (tt - cy) * scale).coerceIn(0f, 0.95f)
                                    val newBottom = (cy + (tb - cy) * scale).coerceIn(0.05f, 1f)

                                    if (newRight - newLeft > 0.05f && newBottom - newTop > 0.05f) {
                                        localCrop = Rect(
                                            left = minOf(newLeft, newRight - 0.05f),
                                            top = minOf(newTop, newBottom - 0.05f),
                                            right = maxOf(newRight, newLeft + 0.05f),
                                            bottom = maxOf(newBottom, newTop + 0.05f)
                                        )
                                        onCropRectChanged(localCrop)
                                    }
                                }
                                prevDist = dist
                                prevCentroid = centroid
                                active.forEach { it.consume() }

                            } else if (active.size == 1 && !pinchStarted) {
                                // Single finger: move or resize depending on hit zone
                                val curr = active[0].position
                                val dx = (curr.x - prevPos.x) / w
                                val dy = (curr.y - prevPos.y) / h
                                val MIN = 0.08f

                                localCrop = when (handle) {
                                    DragHandle.INSIDE -> {
                                        val bw = localCrop.right - localCrop.left
                                        val bh = localCrop.bottom - localCrop.top
                                        val nl = (localCrop.left + dx).coerceIn(0f, 1f - bw)
                                        val nt = (localCrop.top + dy).coerceIn(0f, 1f - bh)
                                        Rect(nl, nt, nl + bw, nt + bh)
                                    }
                                    DragHandle.TL -> localCrop.copy(
                                        left = (localCrop.left + dx).coerceIn(0f, localCrop.right - MIN),
                                        top = (localCrop.top + dy).coerceIn(0f, localCrop.bottom - MIN)
                                    )
                                    DragHandle.TR -> localCrop.copy(
                                        right = (localCrop.right + dx).coerceIn(localCrop.left + MIN, 1f),
                                        top = (localCrop.top + dy).coerceIn(0f, localCrop.bottom - MIN)
                                    )
                                    DragHandle.BL -> localCrop.copy(
                                        left = (localCrop.left + dx).coerceIn(0f, localCrop.right - MIN),
                                        bottom = (localCrop.bottom + dy).coerceIn(localCrop.top + MIN, 1f)
                                    )
                                    DragHandle.BR -> localCrop.copy(
                                        right = (localCrop.right + dx).coerceIn(localCrop.left + MIN, 1f),
                                        bottom = (localCrop.bottom + dy).coerceIn(localCrop.top + MIN, 1f)
                                    )
                                    DragHandle.NONE -> localCrop
                                }
                                onCropRectChanged(localCrop)
                                prevPos = curr
                                active.forEach { it.consume() }
                            }
                        }
                    }
                }
        ) {
            val left = localCrop.left * size.width
            val top = localCrop.top * size.height
            val right = localCrop.right * size.width
            val bottom = localCrop.bottom * size.height

            // Dim outside crop area
            drawRect(Color.Black.copy(alpha = 0.5f))

            // Clear the crop window (lighter overlay)
            drawRect(
                color = Color.White.copy(alpha = 0.05f),
                topLeft = Offset(left, top),
                size = ComposeSize(right - left, bottom - top)
            )

            // Green border
            drawRect(
                color = AccentGreen,
                topLeft = Offset(left, top),
                size = ComposeSize(right - left, bottom - top),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Corner handles
            val handleRadius = 6.dp.toPx()
            listOf(
                Offset(left, top),
                Offset(right, top),
                Offset(left, bottom),
                Offset(right, bottom)
            ).forEach { corner ->
                drawCircle(color = AccentGreen, radius = handleRadius, center = corner)
            }
        }

        Text(
            text = "Drag inside to move · drag corners to resize · pinch to scale",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )

        Button(
            onClick = onAnalyze,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Analyze Label", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewForm(
    displayName: String,
    draft: NutritionLabelViewModel.LabelDraft,
    onDisplayNameChange: (String) -> Unit,
    onDraftChange: (NutritionLabelViewModel.LabelDraft) -> Unit,
    onSaveAndAdd: () -> Unit,
    onAddWithoutSaving: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Review parsed values and correct any errors before adding.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        LabelTextField(
            label = "Food Name",
            value = displayName,
            onValueChange = onDisplayNameChange
        )
        LabelTextField(
            label = "Serving Size",
            value = draft.servingText,
            onValueChange = { onDraftChange(draft.copy(servingText = it)) }
        )
        LabelNumberField(
            label = "Calories",
            value = draft.calories,
            onValueChange = { onDraftChange(draft.copy(calories = it)) }
        )
        LabelNumberField(
            label = "Protein (g)",
            value = draft.protein,
            onValueChange = { onDraftChange(draft.copy(protein = it)) }
        )
        LabelNumberField(
            label = "Carbs (g)",
            value = draft.carbs,
            onValueChange = { onDraftChange(draft.copy(carbs = it)) }
        )
        LabelNumberField(
            label = "Fat (g)",
            value = draft.fat,
            onValueChange = { onDraftChange(draft.copy(fat = it)) }
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onSaveAndAdd,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save + Add to Log", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        }

        OutlinedButton(
            onClick = onAddWithoutSaving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Add without Saving", modifier = Modifier.padding(vertical = 4.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LabelTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = Divider,
            focusedLabelColor = AccentGreen,
            cursorColor = AccentGreen,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        )
    )
}

@Composable
private fun LabelNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = Divider,
            focusedLabelColor = AccentGreen,
            cursorColor = AccentGreen,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        )
    )
}

private fun runOcrOnCrop(
    bitmap: Bitmap,
    cropRect: Rect,
    onResult: (String) -> Unit,
    onError: () -> Unit
) {
    val x = (cropRect.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val y = (cropRect.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val w = ((cropRect.right - cropRect.left) * bitmap.width).toInt().coerceAtLeast(1)
    val h = ((cropRect.bottom - cropRect.top) * bitmap.height).toInt().coerceAtLeast(1)
    val safeW = minOf(w, bitmap.width - x)
    val safeH = minOf(h, bitmap.height - y)
    val cropped = Bitmap.createBitmap(bitmap, x, y, safeW, safeH)
    val image = InputImage.fromBitmap(cropped, 0)
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    recognizer.process(image)
        .addOnSuccessListener { result -> onResult(result.text) }
        .addOnFailureListener { onError() }
}
