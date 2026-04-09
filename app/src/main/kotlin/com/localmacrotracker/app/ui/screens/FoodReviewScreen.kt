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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.ui.viewmodel.FoodReviewViewModel

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
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onSaved()
    }

    // Local string buffers for numeric fields (avoids Double↔String round-trip on every keystroke)
    var calStr by remember(calories) { mutableStateOf(if (calories == 0.0) "" else calories.toString()) }
    var proStr by remember(protein) { mutableStateOf(if (protein == 0.0) "" else protein.toString()) }
    var carbStr by remember(carbs) { mutableStateOf(if (carbs == 0.0) "" else carbs.toString()) }
    var fatStr by remember(fat) { mutableStateOf(if (fat == 0.0) "" else fat.toString()) }
    var qtyStr by remember(quantity) { mutableStateOf(if (quantity == 1.0) "1" else quantity.toString()) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = qtyStr,
                    onValueChange = { s ->
                        qtyStr = s
                        s.toDoubleOrNull()?.let { viewModel.setQuantity(it) }
                    },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = viewModel::setUnit,
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()
            Text("Nutrition", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = calStr,
                onValueChange = { s ->
                    calStr = s
                    s.toDoubleOrNull()?.let { viewModel.setCalories(it) }
                },
                label = { Text("Calories") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = proStr,
                onValueChange = { s ->
                    proStr = s
                    s.toDoubleOrNull()?.let { viewModel.setProtein(it) }
                },
                label = { Text("Protein (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = carbStr,
                onValueChange = { s ->
                    carbStr = s
                    s.toDoubleOrNull()?.let { viewModel.setCarbs(it) }
                },
                label = { Text("Carbs (g)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = fatStr,
                onValueChange = { s ->
                    fatStr = s
                    s.toDoubleOrNull()?.let { viewModel.setFat(it) }
                },
                label = { Text("Fat (g)") },
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
