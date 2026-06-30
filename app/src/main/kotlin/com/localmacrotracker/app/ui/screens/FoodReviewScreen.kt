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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.FoodReviewViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodReviewScreen(
    entryId: Long,          // passed by nav for reference; ViewModel loads via SavedStateHandle
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: FoodReviewViewModel = hiltViewModel()
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val calories by viewModel.calories.collectAsStateWithLifecycle()
    val protein by viewModel.protein.collectAsStateWithLifecycle()
    val carbs by viewModel.carbs.collectAsStateWithLifecycle()
    val fat by viewModel.fat.collectAsStateWithLifecycle()
    val quantity by viewModel.quantity.collectAsStateWithLifecycle()
    val unit by viewModel.unit.collectAsStateWithLifecycle()
    val availableUnits by viewModel.availableUnits.collectAsStateWithLifecycle()
    val refWeightGrams by viewModel.refWeightGrams.collectAsStateWithLifecycle()
    val refVolumeMl by viewModel.refVolumeMl.collectAsStateWithLifecycle()
    val scaledCalories by viewModel.scaledCalories.collectAsStateWithLifecycle()
    val scaledProtein by viewModel.scaledProtein.collectAsStateWithLifecycle()
    val scaledCarbs by viewModel.scaledCarbs.collectAsStateWithLifecycle()
    val scaledFat by viewModel.scaledFat.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Log Entry") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Local string buffers seeded once, after the entry has loaded (the early
        // return above guarantees the VM values are populated by this point). They
        // hold the raw typed text so decimals never snap/reformat mid-keystroke.
        var calStr by remember { mutableStateOf(if (calories == 0.0) "" else trimNum(calories)) }
        var proStr by remember { mutableStateOf(if (protein == 0.0) "" else trimNum(protein)) }
        var carbStr by remember { mutableStateOf(if (carbs == 0.0) "" else trimNum(carbs)) }
        var fatStr by remember { mutableStateOf(if (fat == 0.0) "" else trimNum(fat)) }
        var qtyStr by remember { mutableStateOf(trimNum(quantity)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = viewModel::setDisplayName,
                label = { Text("Food Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Amount + unit. Type any custom amount; pick the unit (serving, or a weight /
            // volume unit if the food defines one). Everything scales live from there.
            Text("Amount", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            var unitExpanded by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { s ->
                        if (s.isEmpty() || s.toDoubleOrNull() != null) {
                            qtyStr = s
                            s.toDoubleOrNull()?.let { viewModel.setQuantity(it) }
                        }
                    },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f)
                )
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(1.2f)
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        availableUnits.forEach { u ->
                            DropdownMenuItem(
                                text = { Text("${u.label}   ${u.fullName}") },
                                onClick = {
                                    viewModel.setUnit(u.label)
                                    // setUnit may convert the amount to keep servings constant.
                                    qtyStr = trimNum(viewModel.quantity.value)
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            // Reference reminder so it's clear what the macros below are based on.
            servingBasis(refWeightGrams, refVolumeMl)?.let { basis ->
                Text(basis, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }

            // Live totals — recompute instantly as the serving amount changes.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Totals for this entry",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TotalStat("$scaledCalories", "kcal", MacroCalories)
                        TotalStat(trimNum(scaledProtein), "P (g)", MacroProtein)
                        TotalStat(trimNum(scaledCarbs), "C (g)", MacroCarbs)
                        TotalStat(trimNum(scaledFat), "F (g)", MacroFat)
                    }
                }
            }

            HorizontalDivider()
            Text("Per serving", style = MaterialTheme.typography.titleMedium)
            Text(
                "Edit these to fix the base values for one serving. Totals above scale by the amount.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            OutlinedTextField(
                value = calStr,
                onValueChange = { s ->
                    calStr = s
                    s.toDoubleOrNull()?.let { viewModel.setCalories(it) }
                },
                label = { Text("Calories (per serving)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proStr,
                onValueChange = { s ->
                    proStr = s
                    s.toDoubleOrNull()?.let { viewModel.setProtein(it) }
                },
                label = { Text("Protein per serving (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = carbStr,
                onValueChange = { s ->
                    carbStr = s
                    s.toDoubleOrNull()?.let { viewModel.setCarbs(it) }
                },
                label = { Text("Carbs per serving (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fatStr,
                onValueChange = { s ->
                    fatStr = s
                    s.toDoubleOrNull()?.let { viewModel.setFat(it) }
                },
                label = { Text("Fat per serving (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.saveEntry() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) { Text(if (isSaving) "Saving…" else "Save Entry") }

            if (entry?.needsManualSaveReminder == true) {
                OutlinedButton(
                    onClick = { viewModel.saveLinkedFood() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) { Text("Save to Database") }
                Text(
                    "★ This food is not yet saved in your database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun TotalStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

/** Human-readable note describing what one serving of macros is based on. */
private fun servingBasis(refWeightGrams: Double?, refVolumeMl: Double?): String? = when {
    refWeightGrams != null && refWeightGrams > 0 ->
        "Macros below are for one serving (${trimNum(refWeightGrams)} g). Change the amount or unit to rescale."
    refVolumeMl != null && refVolumeMl > 0 ->
        "Macros below are for one serving (${trimNum(refVolumeMl)} ml). Change the amount or unit to rescale."
    else -> null
}

/** Format a double without a trailing ".0" for whole numbers; otherwise one decimal. */
private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else (Math.round(value * 10.0) / 10.0).toString()
