package com.localmacrotracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.ui.viewmodel.RecipeViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun RecipeScreen(
    mealSection: String,
    logDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: RecipeViewModel = hiltViewModel()
) {
    val section = MealSection.fromName(mealSection)
    val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }

    val recipeName by viewModel.recipeName.collectAsState()
    val servingsMade by viewModel.servingsMade.collectAsState()
    val ingredients by viewModel.ingredients.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    var showAddIngredientDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe / Meal Prep") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            totals?.let { t ->
                Surface(tonalElevation = 3.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Per Serving", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${t.perServingCalories.roundToInt()} kcal")
                            Text("P: ${t.perServingProtein.roundToInt()}g")
                            Text("C: ${t.perServingCarbs.roundToInt()}g")
                            Text("F: ${t.perServingFat.roundToInt()}g")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveRecipe()
                                    onDone()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving && recipeName.isNotBlank() && ingredients.isNotEmpty()
                            ) { Text("Save Recipe") }
                            Button(
                                onClick = {
                                    viewModel.saveAndAddToLog(section, date)
                                    onDone()
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSaving && recipeName.isNotBlank() && ingredients.isNotEmpty()
                            ) { Text("Save + Add") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = recipeName,
                    onValueChange = viewModel::setName,
                    label = { Text("Recipe Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                // Keep the raw text locally so typing/deleting doesn't fight a reformatted
                // Double (which made the cursor jump). Push the parsed value to the VM only
                // when it's valid; the VM stays the source of truth for the macro math.
                var servingsText by remember {
                    mutableStateOf(
                        if (servingsMade % 1.0 == 0.0) servingsMade.toInt().toString()
                        else servingsMade.toString()
                    )
                }
                OutlinedTextField(
                    value = servingsText,
                    onValueChange = { s ->
                        servingsText = s
                        s.toDoubleOrNull()?.let { viewModel.setServings(it) }
                    },
                    label = { Text("Servings Made") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ingredients", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showAddIngredientDialog = true }) {
                        Icon(Icons.Default.Add, "Add Ingredient")
                    }
                }
            }
            itemsIndexed(ingredients) { index, ingredient ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ingredient.displayNameSnapshot, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${ingredient.quantity} ${ingredient.unit} • ${ingredient.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "P:${ingredient.protein.roundToInt()}g C:${ingredient.carbs.roundToInt()}g F:${ingredient.fat.roundToInt()}g",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeIngredient(index) }) {
                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }

    if (showAddIngredientDialog) {
        AddIngredientDialog(
            onDismiss = { showAddIngredientDialog = false },
            onAdd = { name, qty, unit, cal, pro, carb, fat ->
                viewModel.addManualIngredient(name, qty, unit, cal, pro, carb, fat)
                showAddIngredientDialog = false
            }
        )
    }
}

@Composable
private fun AddIngredientDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Double, String, Double, Double, Double, Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("serving") }
    var cal by remember { mutableStateOf("") }
    var pro by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Ingredient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it },
                        label = { Text("Qty") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = unit, onValueChange = { unit = it },
                        label = { Text("Unit") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = cal, onValueChange = { cal = it },
                    label = { Text("Calories") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = pro, onValueChange = { pro = it },
                    label = { Text("Protein (g)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = carb, onValueChange = { carb = it },
                    label = { Text("Carbs (g)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = fat, onValueChange = { fat = it },
                    label = { Text("Fat (g)") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    name,
                    qty.toDoubleOrNull() ?: 1.0,
                    unit,
                    cal.toDoubleOrNull() ?: 0.0,
                    pro.toDoubleOrNull() ?: 0.0,
                    carb.toDoubleOrNull() ?: 0.0,
                    fat.toDoubleOrNull() ?: 0.0
                )
            }, enabled = name.isNotBlank() && cal.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
