package com.localmacrotracker.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.llm.ModelStatus
import com.localmacrotracker.app.ui.viewmodel.SettingsViewModel

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val modelDisplayName by viewModel.modelDisplayName.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val displayName = uri.lastPathSegment ?: "Selected Model"
            viewModel.selectModel(uri, displayName)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "MacroTracker",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Track your daily nutrition with AI-powered food recognition — fully local, fully private.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = Divider)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Local AI Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "For labeless food recognition, a local on-device language model is required. " +
                            "Select a compatible .task or .bin model file to enable AI features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Status chip
            ModelStatusChip(status = modelStatus, displayName = modelDisplayName)

            // Select model button
            Button(
                onClick = {
                    filePicker.launch(arrayOf("*/*"))
                },
                enabled = modelStatus != ModelStatus.LOADING,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (modelStatus == ModelStatus.LOADING) "Loading Model…" else "Select Model File",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = modelStatus == ModelStatus.LOADING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentGreen,
                    trackColor = DarkSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Text(
                    text = "Continue Without Model",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = modelStatus == ModelStatus.READY) {
                Text(
                    text = "Model loaded. You can continue to the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ModelStatusChip(
    status: ModelStatus,
    displayName: String?
) {
    val (label, color, icon) = when (status) {
        ModelStatus.NOT_CONFIGURED -> Triple("No Model Selected", TextSecondary, null)
        ModelStatus.LOADING -> Triple("Loading…", ReminderAmber, null)
        ModelStatus.READY -> Triple(displayName ?: "Model Ready", AccentGreen, Icons.Filled.CheckCircle)
        ModelStatus.LOAD_FAILED -> Triple("Load Failed", ErrorRed, Icons.Filled.Error)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}
