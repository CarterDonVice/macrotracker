package com.localmacrotracker.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.ui.components.PressableCard
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.FoodSearchViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(
    mealSection: String,
    logDate: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: FoodSearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addResult by viewModel.addResult.collectAsStateWithLifecycle()

    var selectedFood by remember { mutableStateOf<FoodCandidate?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // Navigate away on success
    LaunchedEffect(addResult) {
        if (addResult is FoodSearchViewModel.AddResult.Success) {
            viewModel.resetAddResult()
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Foods") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field
            Surface(
                color = DarkSurface,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text("Search foods, brands, recipes…") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                Icon(Icons.Default.Clear, "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .focusRequester(focusRequester)
                )
            }

            if (state.query.isBlank()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Type to search your library\nor the food database",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Local results section
                    if (state.isLoadingLocal || state.localResults.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Your Library",
                                isLoading = state.isLoadingLocal,
                                count = state.localResults.size
                            )
                        }
                        items(state.localResults, key = { it.id }) { food ->
                            FoodResultRow(
                                food = food,
                                onClick = { selectedFood = food },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    // Remote results section
                    if (state.isLoadingRemote || state.remoteResults.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Food Database",
                                isLoading = state.isLoadingRemote,
                                count = state.remoteResults.size
                            )
                        }
                        items(state.remoteResults, key = { it.id }) { food ->
                            FoodResultRow(
                                food = food,
                                onClick = { selectedFood = food },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }

                    // No results state
                    if (!state.isLoadingLocal && !state.isLoadingRemote &&
                        state.localResults.isEmpty() && state.remoteResults.isEmpty()
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        "No results for \"${state.query}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Text(
                                        "Try a different search or add food manually.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add-to-log bottom sheet
    selectedFood?.let { food ->
        AddFoodBottomSheet(
            food = food,
            mealSection = mealSection,
            logDate = logDate,
            addResult = addResult,
            onAdd = { qty, save ->
                val ms = MealSection.fromName(mealSection)
                val date = runCatching { LocalDate.parse(logDate) }.getOrElse { LocalDate.now() }
                viewModel.addToLog(food, qty, ms, date, save)
            },
            onDismiss = {
                selectedFood = null
                viewModel.resetAddResult()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodBottomSheet(
    food: FoodCandidate,
    mealSection: String,
    logDate: String,
    addResult: FoodSearchViewModel.AddResult,
    onAdd: (quantity: Double, saveFood: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var quantityText by remember { mutableStateOf("1") }
    var saveFood by remember { mutableStateOf(!food.isLocalSaved) }

    val qty = quantityText.toDoubleOrNull() ?: 1.0
    val scaledCalories = (food.calories * qty).roundToInt()
    val scaledProtein = food.proteinGrams * qty
    val scaledCarbs = food.carbsGrams * qty
    val scaledFat = food.fatGrams * qty

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Food name
            Text(
                food.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Serving info
            food.servingText?.let { serving ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Per serving: $serving",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Macro summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroChip("${scaledCalories} cal", MacroCalories, Modifier.weight(1f))
                MacroChip("${String.format("%.1f", scaledProtein)}g P", MacroProtein, Modifier.weight(1f))
                MacroChip("${String.format("%.1f", scaledCarbs)}g C", MacroCarbs, Modifier.weight(1f))
                MacroChip("${String.format("%.1f", scaledFat)}g F", MacroFat, Modifier.weight(1f))
            }

            HorizontalDivider(color = Divider)

            // Quantity
            Text(
                "Servings",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedIconButton(
                    onClick = {
                        val v = (qty - 0.5).coerceAtLeast(0.5)
                        quantityText = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, "Decrease")
                }
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) quantityText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.width(80.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
                OutlinedIconButton(
                    onClick = {
                        val v = qty + 0.5
                        quantityText = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, "Increase")
                }
            }

            // Save to library toggle (only for non-local foods)
            if (!food.isLocalSaved) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Save to library",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            "Remember this food for quick future access",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(checked = saveFood, onCheckedChange = { saveFood = it })
                }
            }

            // Error message
            if (addResult is FoodSearchViewModel.AddResult.Error) {
                Text(
                    (addResult as FoodSearchViewModel.AddResult.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Add button
            Button(
                onClick = { onAdd(qty.coerceAtLeast(0.1), saveFood) },
                enabled = quantityText.toDoubleOrNull() != null && quantityText.toDoubleOrNull()!! > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add to ${MealSection.fromName(mealSection).displayName}")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, isLoading: Boolean, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = AccentGreen
        )
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        } else if (count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

/** Visual identity for a result's data source, shown as a badge on each row. */
private data class SourceMeta(val icon: ImageVector, val label: String, val color: Color)

private fun sourceMetaFor(food: FoodCandidate): SourceMeta = when {
    food.isLocalSaved || food.sourceType == SourceType.LOCAL_SAVED ->
        SourceMeta(Icons.Default.Bookmark, "Saved", AccentGreen)
    food.sourceType == SourceType.USDA ->
        SourceMeta(Icons.Default.Verified, "USDA", EstimatedColor)
    food.sourceType == SourceType.OPEN_FOOD_FACTS || food.sourceType == SourceType.BARCODE ->
        SourceMeta(Icons.Default.Public, "Open Food Facts", MacroCarbs)
    else ->
        SourceMeta(Icons.Default.Restaurant, "Other", TextSecondary)
}

@Composable
private fun FoodResultRow(
    food: FoodCandidate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = sourceMetaFor(food)
    PressableCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Source badge — icon + colour tell you where the result came from.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(source.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    source.icon,
                    contentDescription = "Source: ${source.label}",
                    tint = source.color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    food.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${food.calories.toInt()} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MacroCalories,
                        fontWeight = FontWeight.Medium
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        "P ${food.proteinGrams.toInt()}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MacroProtein
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        "C ${food.carbsGrams.toInt()}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MacroCarbs
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        "F ${food.fatGrams.toInt()}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MacroFat
                    )
                }
                // Source label (+ serving size if present)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        source.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = source.color,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    food.servingText?.let {
                        Text(
                            "· $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MacroChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1
        )
    }
}
