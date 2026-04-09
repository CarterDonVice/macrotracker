package com.localmacrotracker.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.LabelessFoodViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelessFoodEntryScreen(
    mealSection: String,
    logDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: LabelessFoodViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isModelReady by viewModel.isModelReady.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { inputText = it }
        }
    }

    // Show error snackbar
    LaunchedEffect(uiState) {
        if (uiState is LabelessFoodViewModel.UiState.Error) {
            val msg = (uiState as LabelessFoodViewModel.UiState.Error).message
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Labeless Food",
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            // No model banner
            if (!isModelReady) {
                Surface(
                    color = ReminderAmber.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = ReminderAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI features disabled. Enter food name manually and fill in values.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ReminderAmber
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Text input with microphone
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = {
                    Text(
                        "Describe the food you ate…\ne.g. \"2 scrambled eggs with toast and butter\"",
                        color = TextSecondary
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the food you ate")
                            }
                            speechLauncher.launch(intent)
                        }
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Voice input",
                            tint = AccentGreen
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = Divider,
                    cursorColor = AccentGreen,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Submit button
            Button(
                onClick = {
                    val parsedSection = MealSection.fromName(mealSection)
                    val parsedDate = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
                    viewModel.submit(inputText, parsedSection, parsedDate)
                },
                enabled = inputText.isNotBlank() && uiState !is LabelessFoodViewModel.UiState.Processing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Analyze Food",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // State handling
            when (val state = uiState) {
                is LabelessFoodViewModel.UiState.Idle -> { /* empty */ }

                is LabelessFoodViewModel.UiState.Processing -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = AccentGreen)
                            Text(
                                text = "Analyzing food…",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                is LabelessFoodViewModel.UiState.Results -> {
                    Text(
                        text = "Resolved Entries",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.entries) { entry ->
                            LabelessResultCard(
                                name = entry.displayNameSnapshot,
                                quantity = entry.quantity,
                                unit = entry.unit,
                                isEstimated = entry.isEstimated,
                                needsReminder = entry.needsManualSaveReminder,
                                caloriesExact = entry.caloriesExact,
                                caloriesMin = entry.caloriesMin,
                                caloriesMax = entry.caloriesMax,
                                proteinExact = entry.proteinExact,
                                carbsExact = entry.carbsExact,
                                fatExact = entry.fatExact
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Confirm & Add to Log",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                is LabelessFoodViewModel.UiState.Error -> { /* handled via snackbar */ }
            }
        }
    }
}

@Composable
private fun LabelessResultCard(
    name: String,
    quantity: Double,
    unit: String,
    isEstimated: Boolean,
    needsReminder: Boolean,
    caloriesExact: Double?,
    caloriesMin: Double?,
    caloriesMax: Double?,
    proteinExact: Double?,
    carbsExact: Double?,
    fatExact: Double?
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (needsReminder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ReminderAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = ReminderAmber, modifier = Modifier.size(10.dp))
                        Text("Not saved", style = MaterialTheme.typography.labelSmall, color = ReminderAmber, fontSize = 9.sp)
                    }
                }
            }
            Text(
                text = "${if (quantity == quantity.toLong().toDouble()) quantity.toInt() else quantity} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val calLabel = if (isEstimated && caloriesMin != null && caloriesMax != null)
                    "${caloriesMin.toInt()}–${caloriesMax.toInt()} kcal"
                else "${caloriesExact?.toInt() ?: 0} kcal"

                Text(calLabel, style = MaterialTheme.typography.labelSmall, color = if (isEstimated) EstimatedColor else TextPrimary, fontWeight = FontWeight.SemiBold)
                Text("P${proteinExact?.toInt() ?: "?"}g", style = MaterialTheme.typography.labelSmall, color = MacroProtein)
                Text("C${carbsExact?.toInt() ?: "?"}g", style = MaterialTheme.typography.labelSmall, color = MacroCarbs)
                Text("F${fatExact?.toInt() ?: "?"}g", style = MaterialTheme.typography.labelSmall, color = MacroFat)
            }
            if (isEstimated) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Estimated range",
                    style = MaterialTheme.typography.labelSmall,
                    color = EstimatedColor,
                    fontSize = 9.sp
                )
            }
        }
    }
}
