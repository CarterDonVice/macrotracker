package com.localmacrotracker.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.domain.DailyTotalsCalculator
import com.localmacrotracker.app.ui.components.CalorieRing
import com.localmacrotracker.app.ui.components.MacroGoalBar
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.DailyLogViewModel
import com.localmacrotracker.app.ui.viewmodel.SettingsViewModel
import com.localmacrotracker.app.ui.viewmodel.WeightViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(
    onAddEntry: (mealSection: String, logDate: String) -> Unit,
    onSearchFoods: (mealSection: String, logDate: String) -> Unit,
    onBrowseFoods: (logDate: String) -> Unit,
    onNavigateToWeightTracker: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onEntryTapped: (entryId: Long) -> Unit,
    viewModel: DailyLogViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
    weightVm: WeightViewModel = hiltViewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val entriesBySection by viewModel.entriesBySection.collectAsStateWithLifecycle()
    val dailyTotals by viewModel.dailyTotals.collectAsStateWithLifecycle()
    val goalCalories by settingsVm.goalCalories.collectAsStateWithLifecycle()
    val goalProtein by settingsVm.goalProtein.collectAsStateWithLifecycle()
    val goalCarbs by settingsVm.goalCarbs.collectAsStateWithLifecycle()
    val goalFat by settingsVm.goalFat.collectAsStateWithLifecycle()
    val todayWeight by weightVm.todayEntry.collectAsStateWithLifecycle()

    val displayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
    var showMealPicker by remember { mutableStateOf(false) }

    if (showMealPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMealPicker = false },
            containerColor = DarkSurface,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Add saved food to…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                MealSection.values().forEach { section ->
                    TextButton(
                        onClick = {
                            showMealPicker = false
                            onSearchFoods(section.name, selectedDate.toString())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            mealIcon(section),
                            contentDescription = null,
                            tint = mealTint(section),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            section.displayName,
                            modifier = Modifier.weight(1f),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = {
                            viewModel.setDate(selectedDate.minusDays(1))
                        }) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
                        }
                        // Tapping the date jumps back to today
                        Text(
                            text = when (selectedDate) {
                                LocalDate.now() -> "Today"
                                LocalDate.now().minusDays(1) -> "Yesterday"
                                else -> selectedDate.format(displayFormatter)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setDate(LocalDate.now()) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        IconButton(onClick = {
                            viewModel.setDate(selectedDate.plusDays(1))
                        }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                        }
                    }
                },
                actions = {
                    // Direct food database browse (no meal picker)
                    IconButton(onClick = { onBrowseFoods(selectedDate.toString()) }) {
                        Icon(Icons.Filled.FoodBank, contentDescription = "Browse food database", tint = AccentGreen)
                    }
                    // Search saved foods (with meal picker)
                    IconButton(onClick = { showMealPicker = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search saved foods", tint = TextSecondary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextSecondary
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero summary: calorie ring + macro progress
            item(key = "summary") {
                DailySummaryCard(
                    dailyTotals = dailyTotals,
                    goalCalories = goalCalories,
                    goalProtein = goalProtein,
                    goalCarbs = goalCarbs,
                    goalFat = goalFat,
                    onSetGoals = onNavigateToSettings
                )
            }

            // Weight quick-entry widget
            item(key = "weight_widget") {
                WeightQuickEntry(
                    todayWeight = todayWeight?.weightLbs,
                    onLog = { lbs -> weightVm.logWeight(lbs) },
                    onNavigateToTracker = onNavigateToWeightTracker
                )
            }

            items(
                items = MealSection.values().toList(),
                key = { "meal_${it.name}" }
            ) { section ->
                MealCard(
                    section = section,
                    entries = entriesBySection[section] ?: emptyList(),
                    onAddEntry = { onAddEntry(section.name, selectedDate.toString()) },
                    onImportPrevious = { viewModel.importPreviousSection(section) },
                    onEntryTapped = onEntryTapped,
                    onDeleteEntry = { viewModel.deleteEntry(it) }
                )
            }
        }
    }
}

private fun mealIcon(section: MealSection): ImageVector = when (section) {
    MealSection.BREAKFAST -> Icons.Filled.FreeBreakfast
    MealSection.LUNCH -> Icons.Filled.LunchDining
    MealSection.DINNER -> Icons.Filled.DinnerDining
    MealSection.SNACKS -> Icons.Filled.Cookie
}

private fun mealTint(section: MealSection): Color = when (section) {
    MealSection.BREAKFAST -> ReminderAmber
    MealSection.LUNCH -> AccentGreen
    MealSection.DINNER -> MacroProtein
    MealSection.SNACKS -> MacroFat
}

@Composable
private fun DailySummaryCard(
    dailyTotals: DailyTotalsCalculator.DailyTotals,
    goalCalories: Int,
    goalProtein: Int,
    goalCarbs: Int,
    goalFat: Int,
    onSetGoals: () -> Unit
) {
    val isEstimated = dailyTotals is DailyTotalsCalculator.DailyTotals.Range
    val (cal, pro, carb, fat) = when (dailyTotals) {
        is DailyTotalsCalculator.DailyTotals.Exact -> {
            val t = dailyTotals.totals
            listOf(t.calories, t.proteinGrams, t.carbsGrams, t.fatGrams)
        }
        is DailyTotalsCalculator.DailyTotals.Range -> {
            val t = dailyTotals.totals
            // Midpoint of the estimated range drives the progress display
            listOf(
                (t.caloriesMin + t.caloriesMax) / 2,
                (t.proteinMin + t.proteinMax) / 2,
                (t.carbsMin + t.carbsMax) / 2,
                (t.fatMin + t.fatMax) / 2
            )
        }
        is DailyTotalsCalculator.DailyTotals.Empty -> listOf(0.0, 0.0, 0.0, 0.0)
    }
    val hasAnyGoal = goalCalories > 0 || goalProtein > 0 || goalCarbs > 0 || goalFat > 0

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CalorieRing(
                    consumed = cal.toInt(),
                    goal = goalCalories,
                    isEstimated = isEstimated,
                    progressColor = AccentGreen
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MacroGoalBar("Protein", pro.toInt(), goalProtein, MacroProtein)
                    MacroGoalBar("Carbs", carb.toInt(), goalCarbs, MacroCarbs)
                    MacroGoalBar("Fat", fat.toInt(), goalFat, MacroFat)
                }
            }
            if (!hasAnyGoal) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onSetGoals,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Set daily goals to track progress",
                        color = AccentGreen,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MealCard(
    section: MealSection,
    entries: List<FoodLogEntryEntity>,
    onAddEntry: () -> Unit,
    onImportPrevious: () -> Unit,
    onEntryTapped: (entryId: Long) -> Unit,
    onDeleteEntry: (entryId: Long) -> Unit
) {
    val totals = DailyTotalsCalculator.calculate(entries)
    val tint = mealTint(section)

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        mealIcon(section),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    when (totals) {
                        is DailyTotalsCalculator.DailyTotals.Exact -> {
                            val t = totals.totals
                            Text(
                                text = "${t.calories.toInt()} kcal · P${t.proteinGrams.toInt()} C${t.carbsGrams.toInt()} F${t.fatGrams.toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        is DailyTotalsCalculator.DailyTotals.Range -> {
                            val t = totals.totals
                            Text(
                                text = "~${t.caloriesMin.toInt()}–${t.caloriesMax.toInt()} kcal",
                                style = MaterialTheme.typography.labelSmall,
                                color = EstimatedColor
                            )
                        }
                        is DailyTotalsCalculator.DailyTotals.Empty -> { /* nothing */ }
                    }
                }
                // Copy yesterday's section
                IconButton(onClick = onImportPrevious) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy yesterday's ${section.displayName}",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Primary action: add food
                Button(
                    onClick = onAddEntry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tint.copy(alpha = 0.14f),
                        contentColor = tint
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    elevation = null,
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            if (entries.isEmpty()) {
                // Friendly empty state — entire row is tappable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddEntry)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.AddCircleOutline,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Nothing logged yet — tap to add",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = Divider.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                    SwipeToDeleteFoodRow(
                        entry = entry,
                        onTap = { onEntryTapped(entry.id) },
                        onDelete = { onDeleteEntry(entry.id) }
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightQuickEntry(
    todayWeight: Double?,
    onLog: (Double) -> Unit,
    onNavigateToTracker: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf("") }

    LaunchedEffect(todayWeight) {
        if (todayWeight != null && input.isBlank()) {
            input = if (todayWeight == todayWeight.toLong().toDouble())
                todayWeight.toLong().toString() else todayWeight.toString()
        }
    }

    Surface(
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp,
        onClick = onNavigateToTracker
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (todayWeight != null) "$todayWeight lbs" else "Log weight",
                style = MaterialTheme.typography.bodyMedium,
                color = if (todayWeight != null) TextPrimary else TextSecondary,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("lbs", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboard?.hide()
                    input.toDoubleOrNull()?.let { onLog(it) }
                }),
                modifier = Modifier.width(88.dp),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = Divider,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentGreen,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )
            TextButton(
                onClick = {
                    keyboard?.hide()
                    input.toDoubleOrNull()?.let { onLog(it) }
                },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text(
                    if (todayWeight != null) "Update" else "Log",
                    color = AccentGreen,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteFoodRow(
    entry: FoodLogEntryEntity,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    ErrorRed.copy(alpha = 0.8f) else Color.Transparent,
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    ) {
        FoodLogEntryRow(entry = entry, onTap = onTap)
    }
}

@Composable
private fun FoodLogEntryRow(
    entry: FoodLogEntryEntity,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .clickable(onClick = onTap)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.displayNameSnapshot,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.needsManualSaveReminder) {
                    ReminderBadge()
                }
            }
            Text(
                text = buildServingLabel(entry),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = buildCalorieLabel(entry),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.isEstimated) EstimatedColor else MacroCalories
            )
            Text(
                text = buildMacroLabel(entry),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ReminderBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ReminderAmber.copy(alpha = 0.15f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = ReminderAmber,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "Not saved",
            style = MaterialTheme.typography.labelSmall,
            color = ReminderAmber,
            fontSize = 9.sp
        )
    }
}

private fun buildServingLabel(entry: FoodLogEntryEntity): String {
    val qty = if (entry.quantity == entry.quantity.toLong().toDouble())
        entry.quantity.toInt().toString()
    else entry.quantity.toString()
    return "$qty ${entry.unit}"
}

private fun buildCalorieLabel(entry: FoodLogEntryEntity): String {
    return if (entry.isEstimated) {
        "${entry.caloriesMin?.toInt() ?: 0}–${entry.caloriesMax?.toInt() ?: 0} kcal"
    } else {
        "${entry.caloriesExact?.toInt() ?: 0} kcal"
    }
}

private fun buildMacroLabel(entry: FoodLogEntryEntity): String {
    return if (entry.isEstimated) {
        val p = "${entry.proteinMin?.toInt() ?: 0}–${entry.proteinMax?.toInt() ?: 0}"
        val c = "${entry.carbsMin?.toInt() ?: 0}–${entry.carbsMax?.toInt() ?: 0}"
        val f = "${entry.fatMin?.toInt() ?: 0}–${entry.fatMax?.toInt() ?: 0}"
        "P${p} C${c} F${f}g"
    } else {
        val p = entry.proteinExact?.toInt() ?: 0
        val c = entry.carbsExact?.toInt() ?: 0
        val f = entry.fatExact?.toInt() ?: 0
        "P${p} C${c} F${f}g"
    }
}
