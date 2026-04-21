package com.localmacrotracker.app.ui.screens

import android.Manifest
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.BarcodeScanViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanScreen(
    mealSection: String,
    logDate: String,
    onFoodFound: () -> Unit,
    onReviewNeeded: () -> Unit,
    onBack: () -> Unit,
    viewModel: BarcodeScanViewModel = hiltViewModel()
) {
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

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
                        "Barcode Scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            when {
                !cameraPermission.status.isGranted -> {
                    PermissionDeniedContent(
                        rationale = cameraPermission.status.shouldShowRationale,
                        onRequest = { cameraPermission.launchPermissionRequest() }
                    )
                }
                else -> {
                    when (val result = scanResult) {
                        is BarcodeScanViewModel.ScanResult.Idle -> {
                            BarcodeCameraPreview(
                                onBarcodeDetected = { barcode ->
                                    barcode.rawValue?.let { viewModel.onBarcodeScanned(it) }
                                }
                            )
                            ScanOverlay()
                            Text(
                                text = "Point camera at a barcode",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        is BarcodeScanViewModel.ScanResult.Found -> {
                            BarcodeCameraPreview(onBarcodeDetected = {})
                            ScanResultOverlay {
                                FoundFoodCard(
                                    result = result,
                                    onAddToLog = {
                                        viewModel.addToLog(mealSection, logDate)
                                        onFoodFound()
                                    },
                                    onViewEdit = { onReviewNeeded() }
                                )
                            }
                        }

                        is BarcodeScanViewModel.ScanResult.NewFood -> {
                            BarcodeCameraPreview(onBarcodeDetected = {})
                            ScanResultOverlay {
                                NewFoodCard(
                                    result = result,
                                    onSaveAndAdd = {
                                        viewModel.saveAndAddToLog(mealSection, logDate, save = true)
                                        onFoodFound()
                                    },
                                    onAddWithoutSaving = {
                                        viewModel.saveAndAddToLog(mealSection, logDate, save = false)
                                        onFoodFound()
                                    }
                                )
                            }
                        }

                        is BarcodeScanViewModel.ScanResult.NotFound -> {
                            BarcodeCameraPreview(onBarcodeDetected = {})
                            ScanResultOverlay {
                                NotFoundCard(onManualEntry = onReviewNeeded)
                            }
                        }

                        is BarcodeScanViewModel.ScanResult.Error -> {
                            BarcodeCameraPreview(onBarcodeDetected = {})
                            ScanResultOverlay {
                                ErrorCard(message = result.message, onRetry = { viewModel.reset() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    onBarcodeDetected: (Barcode) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    var lastScannedValue by remember { mutableStateOf("") }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            @androidx.camera.core.ExperimentalGetImage
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull()?.let { barcode ->
                                            val raw = barcode.rawValue ?: return@addOnSuccessListener
                                            if (raw != lastScannedValue) {
                                                lastScannedValue = raw
                                                onBarcodeDetected(barcode)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScanOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanWidth = size.width * 0.7f
        val scanHeight = scanWidth * 0.5f
        val left = (size.width - scanWidth) / 2f
        val top = (size.height - scanHeight) / 2f
        val cornerLen = 40.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val color = Color(0xFF4CAF50)

        // Dim overlay
        drawRect(Color.Black.copy(alpha = 0.45f))

        // Clear the scan window
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = ComposeSize(scanWidth, scanHeight),
            cornerRadius = CornerRadius(8.dp.toPx()),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Corner lines
        val corners = listOf(
            Pair(Offset(left, top), Pair(Offset(left + cornerLen, top), Offset(left, top + cornerLen))),
            Pair(Offset(left + scanWidth, top), Pair(Offset(left + scanWidth - cornerLen, top), Offset(left + scanWidth, top + cornerLen))),
            Pair(Offset(left, top + scanHeight), Pair(Offset(left + cornerLen, top + scanHeight), Offset(left, top + scanHeight - cornerLen))),
            Pair(Offset(left + scanWidth, top + scanHeight), Pair(Offset(left + scanWidth - cornerLen, top + scanHeight), Offset(left + scanWidth, top + scanHeight - cornerLen)))
        )
        corners.forEach { (corner, lines) ->
            drawLine(color, corner, lines.first, strokeWidth)
            drawLine(color, corner, lines.second, strokeWidth)
        }
    }
}

@Composable
private fun ScanResultOverlay(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun FoundFoodCard(
    result: BarcodeScanViewModel.ScanResult.Found,
    onAddToLog: () -> Unit,
    onViewEdit: () -> Unit
) {
    Text(
        text = result.food.displayName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Text(
        text = result.food.servingText ?: "1 serving",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${result.food.calories.toInt()} kcal", color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("P${result.food.proteinGrams.toInt()}g", color = MacroProtein, style = MaterialTheme.typography.labelMedium)
        Text("C${result.food.carbsGrams.toInt()}g", color = MacroCarbs, style = MaterialTheme.typography.labelMedium)
        Text("F${result.food.fatGrams.toInt()}g", color = MacroFat, style = MaterialTheme.typography.labelMedium)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onAddToLog,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)
        ) { Text("Add to Log", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onViewEdit, modifier = Modifier.weight(1f)) {
            Text("View / Edit")
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NewFoodCard(
    result: BarcodeScanViewModel.ScanResult.NewFood,
    onSaveAndAdd: () -> Unit,
    onAddWithoutSaving: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = EstimatedColor, modifier = Modifier.size(20.dp))
        Text("Found from API", style = MaterialTheme.typography.labelSmall, color = EstimatedColor)
    }
    Text(
        text = result.foodName,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Text(result.servingText ?: "1 serving", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${result.calories.toInt()} kcal", color = TextPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("P${result.protein.toInt()}g", color = MacroProtein, style = MaterialTheme.typography.labelMedium)
        Text("C${result.carbs.toInt()}g", color = MacroCarbs, style = MaterialTheme.typography.labelMedium)
        Text("F${result.fat.toInt()}g", color = MacroFat, style = MaterialTheme.typography.labelMedium)
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onSaveAndAdd,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)
        ) { Text("Save + Add", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onAddWithoutSaving, modifier = Modifier.weight(1f)) {
            Text("Add Only")
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NotFoundCard(onManualEntry: () -> Unit) {
    Icon(Icons.Filled.SearchOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
    Text(
        "Food not found",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Text(
        "This barcode isn't in the database. Would you like to enter the details manually?",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
        textAlign = TextAlign.Center
    )
    Button(
        onClick = onManualEntry,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)
    ) { Text("Enter Manually", fontWeight = FontWeight.Bold) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Icon(Icons.Filled.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(32.dp))
    Text("Something went wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
    Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun PermissionDeniedContent(rationale: Boolean, onRequest: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(56.dp))
            Text(
                text = if (rationale)
                    "Camera permission is needed to scan barcodes. Please grant permission."
                else
                    "Camera access is required for barcode scanning.",
                textAlign = TextAlign.Center,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, contentColor = Color.White)) {
                Text("Grant Permission")
            }
        }
    }
}
