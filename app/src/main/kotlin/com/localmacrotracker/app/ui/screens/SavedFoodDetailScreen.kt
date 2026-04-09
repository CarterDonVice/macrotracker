package com.localmacrotracker.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.localmacrotracker.app.ui.viewmodel.SavedFoodDetailViewModel

@Composable
fun SavedFoodDetailScreen(
    foodId: Long,
    onBack: () -> Unit,
    viewModel: SavedFoodDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(foodId) { viewModel.loadFood(foodId) }

    val displayName by viewModel.displayName.collectAsState()
    val servingText by viewModel.servingText.collectAsState()
    val servingWeightGrams by viewModel.servingWeightGrams.collectAsState()
    val calories by viewModel.calories.collectAsState()
    val protein by viewModel.protein.collectAsState()
    val carbs by viewModel.carbs.collectAsState()
    val fat by viewModel.fat.collectAsState()
    val barcode by viewModel.barcode.collectAsState()
    val sourceType by viewModel.sourceType.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Saved Food") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            )) {
                Text(
                    "Editing this food only affects future uses. Past log entries will not change.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = viewModel::setDisplayName,
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = servingText,
                onValueChange = viewModel::setServingText,
                label = { Text("Serving Description") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = servingWeightGrams,
                onValueChange = viewModel::setServingWeight,
                label = { Text("Serving Weight (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()
            Text("Nutrition per Serving", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(value = calories, onValueChange = viewModel::setCalories,
                label = { Text("Calories") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = protein, onValueChange = viewModel::setProtein,
                label = { Text("Protein (g)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = carbs, onValueChange = viewModel::setCarbs,
                label = { Text("Carbs (g)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = fat, onValueChange = viewModel::setFat,
                label = { Text("Fat (g)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

            if (!barcode.isNullOrBlank()) {
                HorizontalDivider()
                Text("Barcode: $barcode", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!sourceType.isNullOrBlank()) {
                Text("Source: $sourceType", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.saveFood() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving && displayName.isNotBlank()
            ) { Text(if (isSaving) "Saving…" else "Save Changes") }
        }
    }
}
