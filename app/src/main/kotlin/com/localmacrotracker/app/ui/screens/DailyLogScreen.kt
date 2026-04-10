package com.localmacrotracker.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.domain.DailyTotalsCalculator
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.DailyLogViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyLogScreen(
    onAddEntry: (mealSection: String, logDate: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onEntryTapped: (entryId: Long) -> Unit,
    viewModel: DailyLogViewModel = hiltViewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val entriesBySection by viewModel.entriesBySection.collectAsStateWithLifecycle()
    val dailyTotals by viewModel.dailyTotals.collectAsStateWithLifecycle()

    val displayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

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
                        Text(
                            text = when (selectedDate) {
                                LocalDate.now() -> "Today"
                                LocalDate.now().minusDays(1) -> "Yesterday"
                                else -> selectedDate.format(displayFormatter)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        IconButton(onClick = {
                            viewModel.setDate(selectedDate.plusDays(1))
                        }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sticky daily totals bar at top
            DailyTotalsBar(totals = dailyTotals)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                MealSection.values().forEach { section ->
                    val entries = entriesBySection[section] ?: emptyList()
                    val sectionTotals = DailyTotalsCalculator.calculate(entries)

                    item(key = "header_${section.name}") {
                        MealSectionHeader(
                            section = section,
                            totals = sectionTotals,
                            onAddEntry = { onAddEntry(section.name, selectedDate.toString()) },
                            onImportPrevious = { viewModel.importPreviousSection(section) }
                        )
                    }

                    if (entries.isEmpty()) {
                        item(key = "empty_${section.name}") {
                            Text(
                                text = "No entries yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                            )
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            SwipeToDeleteFoodRow(
                                entry = entry,
                                onTap = { onEntryTapped(entry.id) },
                                onDelete = { viewModel.deleteEntry(entry.id) }
                            )
                        }
                    }

                    item(key = "divider_${section.name}") {
                        HorizontalDivider(
                            color = Divider,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyTotalsBar(totals: DailyTotalsCalculator.DailyTotals) {
    Surface(
        color = DarkSurface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when (totals) {
                is DailyTotalsCalculator.DailyTotals.Empty -> {
                    Text(
                        text = "No entries for this day",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is DailyTotalsCalculator.DailyTotals.Exact -> {
                    val t = totals.totals
                    MacroTotalItem(label = "Cal", value = "${t.calories.toInt()}")
                    MacroTotalItem(label = "Protein", value = "${t.proteinGrams.toInt()}g", color = MacroProtein)
                    MacroTotalItem(label = "Carbs", value = "${t.carbsGrams.toInt()}g", color = MacroCarbs)
                    MacroTotalItem(label = "Fat", value = "${t.fatGrams.toInt()}g", color = MacroFat)
                }
                is DailyTotalsCalculator.DailyTotals.Range -> {
                    val t = totals.totals
                    MacroTotalItem(
                        label = "Cal",
                        value = "${t.caloriesMin.toInt()}–${t.caloriesMax.toInt()}",
                        color = EstimatedColor
                    )
                    MacroTotalItem(
                        label = "Protein",
                        value = "${t.proteinMin.toInt()}–${t.proteinMax.toInt()}g",
                        color = MacroProtein
                    )
                    MacroTotalItem(
                        label = "Carbs",
                        value = "${t.carbsMin.toInt()}–${t.carbsMax.toInt()}g",
                        color = MacroCarbs
                    )
                    MacroTotalItem(
                        label = "Fat",
                        value = "${t.fatMin.toInt()}–${t.fatMax.toInt()}g",
                        color = MacroFat
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroTotalItem(
    label: String,
    value: String,
    color: Color = TextPrimary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 13.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MealSectionHeader(
    section: MealSection,
    totals: DailyTotalsCalculator.DailyTotals,
    onAddEntry: () -> Unit,
    onImportPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AccentGreen,
                modifier = Modifier.weight(1f)
            )
            // Section macro summary
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
                        text = "${t.caloriesMin.toInt()}–${t.caloriesMax.toInt()} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = EstimatedColor
                    )
                }
                is DailyTotalsCalculator.DailyTotals.Empty -> { /* nothing */ }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = onAddEntry,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = AccentGreen
                )
                Spacer(Modifier.width(4.dp))
                Text("Add", color = AccentGreen, style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = onImportPrevious,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Import Yesterday's ${section.displayName}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
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
    Surface(
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    color = if (entry.isEstimated) EstimatedColor else TextPrimary
                )
                Text(
                    text = buildMacroLabel(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
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
