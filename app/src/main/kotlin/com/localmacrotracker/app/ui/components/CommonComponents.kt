package com.localmacrotracker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.localmacrotracker.app.ui.theme.ReminderAmber
import com.localmacrotracker.app.ui.theme.EstimatedColor

/** Small amber badge shown when a log entry is not yet saved to the DB. */
@Composable
fun ReminderBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(ReminderAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Not saved",
            tint = ReminderAmber,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "Not saved",
            style = MaterialTheme.typography.labelSmall,
            color = ReminderAmber
        )
    }
}

/** Chip showing "Estimated" for range entries. */
@Composable
fun EstimatedChip(modifier: Modifier = Modifier) {
    Surface(
        color = EstimatedColor.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = "~",
            style = MaterialTheme.typography.labelSmall,
            color = EstimatedColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/** Simple loading overlay. */
@Composable
fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** Section divider. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 4.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline
    )
}

/** Macro quick-view chips for a food item in a list. */
@Composable
fun MacroChips(
    calories: Double,
    protein: Double,
    carbs: Double,
    fat: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${calories.toInt()} kcal", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("P:${protein.toInt()}g", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("C:${carbs.toInt()}g", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("F:${fat.toInt()}g", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
