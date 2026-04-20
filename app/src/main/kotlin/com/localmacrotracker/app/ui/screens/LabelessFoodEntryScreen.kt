package com.localmacrotracker.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val confirmedEntries by viewModel.confirmedEntries.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    // Navigate away after successful save (state returns to Idle with empty entries)
    val prevStateWasConfirmation = remember { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        if (uiState is LabelessFoodViewModel.UiState.Confirmation) {
            prevStateWasConfirmation.value = true
        }
        if (uiState is LabelessFoodViewModel.UiState.Idle && prevStateWasConfirmation.value) {
            prevStateWasConfirmation.value = false
            onDone()
        }
        if (uiState is LabelessFoodViewModel.UiState.Error) {
            showErrorDialog = (uiState as LabelessFoodViewModel.UiState.Error).message
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.let { inputText = it }
        }
    }

    // Error dialog
    showErrorDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = null
                viewModel.resetState()
            },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = null
                    viewModel.resetState()
                }) { Text("OK") }
            },
            title = { Text("Could not parse food") },
            text = { Text(msg) },
            containerColor = DarkSurface
        )
    }

    // Full-form edit dialog
    editingIndex?.let { idx ->
        val entry = confirmedEntries.getOrNull(idx)
        if (entry != null) {
            EditEntryDialog(
                entry = entry,
                onDismiss = { editingIndex = null },
                onSave = { updated ->
                    viewModel.updateEntry(idx, updated)
                    editingIndex = null
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState is LabelessFoodViewModel.UiState.Confirmation)
                            "Confirm Entries" else "Labeless Food",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is LabelessFoodViewModel.UiState.Confirmation) {
                            viewModel.cancel()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        when (val state = uiState) {

            // ── Input phase ────────────────────────────────────────────────
            is LabelessFoodViewModel.UiState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = {
                            Text(
                                "Describe what you ate…\ne.g. \"2 scrambled eggs with toast and butter\"",
                                color = TextSecondary
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe the food you ate")
                                }
                                speechLauncher.launch(intent)
                            }) {
                                Icon(Icons.Filled.Mic, "Voice input", tint = AccentGreen)
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

                    Button(
                        onClick = {
                            val section = MealSection.fromName(mealSection)
                            val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
                            viewModel.submit(inputText, section, date)
                        },
                        enabled = inputText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Analyze Food",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // ── Working phase ──────────────────────────────────────────────
            is LabelessFoodViewModel.UiState.Working -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AccentGreen, strokeWidth = 3.dp)
                        Text(state.stage, color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── Confirmation phase ─────────────────────────────────────────
            is LabelessFoodViewModel.UiState.Confirmation -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(confirmedEntries) { index, entry ->
                            ConfirmedEntryCard(
                                entry = entry,
                                onEditClick = { editingIndex = index }
                            )
                        }
                    }

                    Surface(color = DarkSurface) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancel() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel", color = TextSecondary)
                            }
                            Button(
                                onClick = { viewModel.confirmAndSave() },
                                modifier = Modifier.weight(2f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add to Log", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Error is shown as dialog above; nothing extra here
            is LabelessFoodViewModel.UiState.Error -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DarkSurface, strokeWidth = 0.dp)
                }
            }
        }
    }
}

// ── Confirmed entry card ──────────────────────────────────────────────────────

@Composable
private fun ConfirmedEntryCard(
    entry: LabelessFoodViewModel.ConfirmedEntry,
    onEditClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                if (entry.brand != null) {
                    Text(
                        entry.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen,
                        fontSize = 10.sp
                    )
                }
                val qty = entry.quantity.let {
                    if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                }
                val servingLabel = entry.servingSize ?: "$qty ${entry.unit}"
                Text(
                    servingLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                if (entry.needsManualEntry) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = ReminderAmber,
                            modifier = Modifier.size(12.dp))
                        Text(
                            "Not found — tap edit to fill in values",
                            style = MaterialTheme.typography.labelSmall,
                            color = ReminderAmber,
                            fontSize = 10.sp
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${entry.calories?.toInt() ?: 0} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary)
                        Text("P ${entry.proteinGrams?.toInt() ?: 0}g",
                            style = MaterialTheme.typography.labelSmall, color = MacroProtein)
                        Text("C ${entry.carbsGrams?.toInt() ?: 0}g",
                            style = MaterialTheme.typography.labelSmall, color = MacroCarbs)
                        Text("F ${entry.fatGrams?.toInt() ?: 0}g",
                            style = MaterialTheme.typography.labelSmall, color = MacroFat)
                    }
                }
            }
            IconButton(onClick = onEditClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, "Edit", tint = TextSecondary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Full edit dialog ──────────────────────────────────────────────────────────

@Composable
private fun EditEntryDialog(
    entry: LabelessFoodViewModel.ConfirmedEntry,
    onDismiss: () -> Unit,
    onSave: (LabelessFoodViewModel.ConfirmedEntry) -> Unit
) {
    // Food info
    var name by remember { mutableStateOf(entry.displayName) }
    var brand by remember { mutableStateOf(entry.brand ?: "") }

    // Quantity / serving
    var servingSize by remember { mutableStateOf(entry.servingSize ?: "") }
    var quantity by remember {
        mutableStateOf(
            entry.quantity.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
        )
    }
    var unit by remember { mutableStateOf(entry.unit) }
    var weightG by remember { mutableStateOf(entry.weightG?.toString() ?: "") }
    var weightOz by remember { mutableStateOf(entry.weightOz?.toString() ?: "") }

    // Description
    var preparation by remember { mutableStateOf(entry.preparation ?: "") }
    var leanness by remember { mutableStateOf(entry.leanness ?: "") }
    var part by remember { mutableStateOf(entry.part ?: "") }
    var fatContent by remember { mutableStateOf(entry.fatContent ?: "") }

    // Nutrition
    var calories by remember { mutableStateOf(entry.calories?.toInt()?.toString() ?: "") }
    var protein by remember { mutableStateOf(entry.proteinGrams?.toInt()?.toString() ?: "") }
    var carbs by remember { mutableStateOf(entry.carbsGrams?.toInt()?.toString() ?: "") }
    var fat by remember { mutableStateOf(entry.fatGrams?.toInt()?.toString() ?: "") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Edit Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss", tint = TextSecondary,
                            modifier = Modifier.size(20.dp))
                    }
                }

                HorizontalDivider(color = Divider, thickness = 0.5.dp)

                // Scrollable form body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // ── Food info ──────────────────────────────────────────
                    SectionLabel("Food")
                    EditField("Food name *", name, KeyboardType.Text) { name = it }
                    EditField("Brand (optional)", brand, KeyboardType.Text) { brand = it }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)

                    // ── Quantity / serving ─────────────────────────────────
                    SectionLabel("Quantity")
                    EditField("Serving size (e.g. 1 cup, 85g)", servingSize, KeyboardType.Text) { servingSize = it }
                    EditField("Number of servings", quantity, KeyboardType.Decimal) { quantity = it }
                    EditField("Unit (e.g. g, oz, cup)", unit, KeyboardType.Text) { unit = it }
                    EditField("Weight (g)", weightG, KeyboardType.Decimal) { weightG = it }
                    EditField("Weight (oz)", weightOz, KeyboardType.Decimal) { weightOz = it }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)

                    // ── Description ────────────────────────────────────────
                    SectionLabel("Description")
                    EditField("Preparation (e.g. cooked, raw, frozen)", preparation, KeyboardType.Text) { preparation = it }
                    EditField("Leanness (e.g. lean, 80/20, extra-lean)", leanness, KeyboardType.Text) { leanness = it }
                    EditField("Part (e.g. breast, thigh, whole)", part, KeyboardType.Text) { part = it }
                    EditField("Fat content (e.g. low-fat, full-fat)", fatContent, KeyboardType.Text) { fatContent = it }

                    HorizontalDivider(color = Divider, thickness = 0.5.dp)

                    // ── Nutrition ──────────────────────────────────────────
                    SectionLabel("Nutrition per serving")
                    EditField("Calories (kcal)", calories, KeyboardType.Number) { calories = it }
                    EditField("Protein (g)", protein, KeyboardType.Decimal) { protein = it }
                    EditField("Carbs (g)", carbs, KeyboardType.Decimal) { carbs = it }
                    EditField("Fat (g)", fat, KeyboardType.Decimal) { fat = it }

                    Spacer(Modifier.height(8.dp))
                }

                HorizontalDivider(color = Divider, thickness = 0.5.dp)

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            val cal = calories.toDoubleOrNull()
                            onSave(
                                entry.copy(
                                    displayName = name.ifBlank { entry.displayName },
                                    brand = brand.ifBlank { null },
                                    servingSize = servingSize.ifBlank { null },
                                    quantity = quantity.toDoubleOrNull() ?: entry.quantity,
                                    unit = unit.ifBlank { entry.unit },
                                    weightG = weightG.toDoubleOrNull(),
                                    weightOz = weightOz.toDoubleOrNull(),
                                    preparation = preparation.ifBlank { null },
                                    leanness = leanness.ifBlank { null },
                                    part = part.ifBlank { null },
                                    fatContent = fatContent.ifBlank { null },
                                    calories = cal,
                                    proteinGrams = protein.toDoubleOrNull(),
                                    carbsGrams = carbs.toDoubleOrNull(),
                                    fatGrams = fat.toDoubleOrNull(),
                                    needsManualEntry = cal == null
                                )
                            )
                        },
                        modifier = Modifier.weight(2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EditField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = Divider,
            focusedLabelColor = AccentGreen,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentGreen,
            focusedContainerColor = DarkSurface,
            unfocusedContainerColor = DarkSurface
        )
    )
}
