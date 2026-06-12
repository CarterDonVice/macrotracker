package com.localmacrotracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.MeasurementUnit
import com.localmacrotracker.app.data.model.MeasurementUnits
import com.localmacrotracker.app.data.model.UnitKind
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.ManualFoodEntryViewModel
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFoodEntryScreen(
    mealSection: String,
    logDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualFoodEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var foodName by remember { mutableStateOf("") }
    var servingAmount by remember { mutableStateOf("") }
    var unitQuery by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf<MeasurementUnit?>(null) }
    var unitExpanded by remember { mutableStateOf(false) }
    var caloriesInput by remember { mutableStateOf("") }
    var proteinInput by remember { mutableStateOf("") }
    var carbsInput by remember { mutableStateOf("") }
    var fatInput by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is ManualFoodEntryViewModel.UiState.Saved) {
            onDone()
        }
    }

    val parsedSection = remember(mealSection) { MealSection.fromName(mealSection) }
    val parsedDate = remember(logDate) {
        runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Manual Entry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            ManualField(
                label = "Food name *",
                value = foodName,
                onValueChange = { foodName = it },
                keyboardType = KeyboardType.Text
            )

            // ── Serving size: amount + searchable unit dropdown ──
            Text(
                "Serving size",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = servingAmount,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) servingAmount = it },
                    label = { Text("Amount") },
                    placeholder = { Text("e.g. 10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = manualFieldColors()
                )
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(1.3f)
                ) {
                    OutlinedTextField(
                        value = unitQuery,
                        onValueChange = {
                            unitQuery = it
                            selectedUnit = null
                            unitExpanded = true
                        },
                        label = { Text("Unit") },
                        placeholder = { Text("g, cup, piece…") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = manualFieldColors()
                    )
                    val filteredUnits = MeasurementUnits.search(unitQuery)
                    ExposedDropdownMenu(
                        expanded = unitExpanded && filteredUnits.isNotEmpty(),
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        filteredUnits.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text("${unit.label}   ${unit.fullName}") },
                                onClick = {
                                    selectedUnit = unit
                                    unitQuery = unit.label
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Divider, thickness = 0.5.dp)

            Text(
                "Nutrition per serving",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            ManualField("Calories (kcal)", caloriesInput, { caloriesInput = it }, KeyboardType.Number)
            ManualField("Protein (g)", proteinInput, { proteinInput = it }, KeyboardType.Decimal)
            ManualField("Carbs (g)", carbsInput, { carbsInput = it }, KeyboardType.Decimal)
            ManualField("Fat (g)", fatInput, { fatInput = it }, KeyboardType.Decimal)

            if (uiState is ManualFoodEntryViewModel.UiState.Error) {
                Text(
                    (uiState as ManualFoodEntryViewModel.UiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Saved foods are added to your library so you can find them in search later.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    val amount = servingAmount.toDoubleOrNull()
                    val unit = selectedUnit
                    val servingTextValue = when {
                        servingAmount.isNotBlank() && unit != null -> "${servingAmount.trim()} ${unit.label}"
                        servingAmount.isNotBlank() -> servingAmount.trim()
                        unit != null -> unit.label
                        unitQuery.isNotBlank() -> unitQuery.trim()
                        else -> null
                    }
                    val grams = if (amount != null && unit?.kind == UnitKind.WEIGHT)
                        amount * (unit.grams ?: 0.0) else null
                    val milliliters = if (amount != null && unit?.kind == UnitKind.VOLUME)
                        amount * (unit.milliliters ?: 0.0) else null
                    viewModel.save(
                        mealSection = parsedSection,
                        logDate = parsedDate,
                        name = foodName,
                        servingText = servingTextValue,
                        unitLabel = unit?.label ?: unitQuery.trim().ifBlank { null },
                        servingWeightGrams = grams,
                        servingVolumeMl = milliliters,
                        calories = caloriesInput.toDoubleOrNull(),
                        proteinGrams = proteinInput.toDoubleOrNull(),
                        carbsGrams = carbsInput.toDoubleOrNull(),
                        fatGrams = fatInput.toDoubleOrNull()
                    )
                },
                enabled = foodName.isNotBlank() &&
                        uiState !is ManualFoodEntryViewModel.UiState.Saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    "Save Food & Add to Log",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ManualField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = manualFieldColors()
    )
}

@Composable
private fun manualFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGreen,
    unfocusedBorderColor = Divider,
    focusedLabelColor = AccentGreen,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = AccentGreen,
    focusedContainerColor = DarkSurface,
    unfocusedContainerColor = DarkSurface
)
