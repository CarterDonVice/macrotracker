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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.BuildConfig
import com.localmacrotracker.app.ui.theme.AccentGreen
import com.localmacrotracker.app.ui.theme.DarkBackground
import com.localmacrotracker.app.ui.theme.DarkSurface
import com.localmacrotracker.app.ui.theme.TextPrimary
import com.localmacrotracker.app.ui.theme.TextSecondary
import com.localmacrotracker.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val goalCalories by viewModel.goalCalories.collectAsStateWithLifecycle()
    val goalProtein by viewModel.goalProtein.collectAsStateWithLifecycle()
    val goalCarbs by viewModel.goalCarbs.collectAsStateWithLifecycle()
    val goalFat by viewModel.goalFat.collectAsStateWithLifecycle()

    var calInput by remember(goalCalories) { mutableStateOf(if (goalCalories > 0) goalCalories.toString() else "") }
    var proInput by remember(goalProtein) { mutableStateOf(if (goalProtein > 0) goalProtein.toString() else "") }
    var carbInput by remember(goalCarbs) { mutableStateOf(if (goalCarbs > 0) goalCarbs.toString() else "") }
    var fatInput by remember(goalFat) { mutableStateOf(if (goalFat > 0) goalFat.toString() else "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextSecondary
                )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Food Database ─────────────────────────────────────────────
            SettingsSection("Food Database") {
                Text(
                    "Food search is powered by the USDA FoodData Central and Open Food Facts " +
                        "databases, plus your own saved library. No setup or API key needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Daily Goals ───────────────────────────────────────────────
            SettingsSection("Daily Goals") {
                Text(
                    "Set targets to see progress bars on the home screen. Leave blank to hide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                GoalField("Calories (kcal)", calInput) { calInput = it }
                GoalField("Protein (g)", proInput) { proInput = it }
                GoalField("Carbs (g)", carbInput) { carbInput = it }
                GoalField("Fat (g)", fatInput) { fatInput = it }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = {
                        viewModel.setGoals(
                            calories = calInput.toIntOrNull() ?: 0,
                            protein = proInput.toIntOrNull() ?: 0,
                            carbs = carbInput.toIntOrNull() ?: 0,
                            fat = fatInput.toIntOrNull() ?: 0
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save Goals") }
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
                    "Local-first. No backend. No cloud. No ads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GoalField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
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
            fontWeight = FontWeight.SemiBold,
            color = AccentGreen
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}
