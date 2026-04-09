package com.localmacrotracker.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmacrotracker.app.domain.DailyTotalsCalculator
import com.localmacrotracker.app.ui.theme.MacroCarbs
import com.localmacrotracker.app.ui.theme.MacroFat
import com.localmacrotracker.app.ui.theme.MacroProtein
import kotlin.math.roundToInt

@Composable
fun MacroSummaryRow(
    totals: DailyTotalsCalculator.DailyTotals,
    modifier: Modifier = Modifier
) {
    when (totals) {
        is DailyTotalsCalculator.DailyTotals.Empty -> {
            MacroRowContent(
                calories = "0", protein = "0g", carbs = "0g", fat = "0g",
                modifier = modifier
            )
        }
        is DailyTotalsCalculator.DailyTotals.Exact -> {
            val t = totals.totals
            MacroRowContent(
                calories = "${t.calories.roundToInt()}",
                protein = "${t.proteinGrams.roundToInt()}g",
                carbs = "${t.carbsGrams.roundToInt()}g",
                fat = "${t.fatGrams.roundToInt()}g",
                modifier = modifier
            )
        }
        is DailyTotalsCalculator.DailyTotals.Range -> {
            val t = totals.totals
            MacroRowContent(
                calories = "~${t.caloriesMin.roundToInt()}–${t.caloriesMax.roundToInt()}",
                protein = "~${t.proteinMin.roundToInt()}–${t.proteinMax.roundToInt()}g",
                carbs = "~${t.carbsMin.roundToInt()}–${t.carbsMax.roundToInt()}g",
                fat = "~${t.fatMin.roundToInt()}–${t.fatMax.roundToInt()}g",
                modifier = modifier
            )
        }
    }
}

@Composable
fun MacroRowContent(
    calories: String,
    protein: String,
    carbs: String,
    fat: String,
    modifier: Modifier = Modifier,
    caloriesColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MacroCell(label = "Cal", value = calories, color = caloriesColor)
        MacroCell(label = "Pro", value = protein, color = MacroProtein)
        MacroCell(label = "Carb", value = carbs, color = MacroCarbs)
        MacroCell(label = "Fat", value = fat, color = MacroFat)
    }
}

@Composable
private fun MacroCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Compact inline macro display for a single log entry row. */
@Composable
fun InlineMacroText(
    calories: Double?,
    caloriesMin: Double?,
    caloriesMax: Double?,
    isEstimated: Boolean,
    modifier: Modifier = Modifier
) {
    val calText = if (isEstimated && caloriesMin != null && caloriesMax != null) {
        "~${caloriesMin.roundToInt()}–${caloriesMax.roundToInt()} kcal"
    } else {
        "${(calories ?: 0.0).roundToInt()} kcal"
    }
    Text(
        text = calText,
        style = MaterialTheme.typography.bodySmall,
        color = if (isEstimated) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
