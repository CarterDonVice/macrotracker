package com.localmacrotracker.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.BuildConfig
import com.localmacrotracker.app.llm.ModelStatus
import com.localmacrotracker.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val usdaKey by viewModel.usdaKey.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val modelDisplayName by viewModel.modelDisplayName.collectAsStateWithLifecycle()

    var keyInput by remember(usdaKey) { mutableStateOf(usdaKey) }
    var keyVisible by remember { mutableStateOf(false) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val displayName = uri.lastPathSegment ?: "Selected Model"
            // Persist permission so we can access the file later
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.selectModel(uri, displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Local Model ──────────────────────────────────────────────
            SettingsSection("Local Model") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            modelDisplayName ?: "No model loaded",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ModelStatusChip(modelStatus)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            modelPicker.launch(
                                arrayOf("application/octet-stream", "*/*")
                            )
                        },
                        modifier = Modifier.weight(1f),
                        enabled = modelStatus != ModelStatus.LOADING
                    ) { Text("Select Model File") }
                    if (modelStatus != ModelStatus.NOT_CONFIGURED) {
                        OutlinedButton(
                            onClick = { viewModel.clearModel() },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear Model") }
                    }
                }
                Text(
                    "Compatible formats: .bin, .task (MediaPipe Tasks GenAI / Gemma)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── API Keys ─────────────────────────────────────────────────
            SettingsSection("API Keys") {
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("USDA FoodData Central API Key") },
                    placeholder = { Text("Get free key at fdc.nal.usda.gov") },
                    visualTransformation = if (keyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (keyVisible) "Hide key" else "Show key"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.saveUsdaKey(keyInput) },
                    enabled = keyInput != usdaKey
                ) { Text("Save Key") }
                Text(
                    "Key is stored locally. Never transmitted to any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── About ─────────────────────────────────────────────────────
            SettingsSection("About") {
                Text("Local Macro Tracker", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Local-first. No backend. No cloud LLM. No ads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ModelStatusChip(status: ModelStatus) {
    val (label, color) = when (status) {
        ModelStatus.NOT_CONFIGURED -> "No model loaded" to MaterialTheme.colorScheme.onSurfaceVariant
        ModelStatus.LOADING -> "Loading…" to MaterialTheme.colorScheme.secondary
        ModelStatus.READY -> "Model ready" to MaterialTheme.colorScheme.primary
        ModelStatus.LOAD_FAILED -> "Load failed" to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}
