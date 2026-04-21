package com.localmacrotracker.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localmacrotracker.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryChooserScreen(
    mealSection: String,
    logDate: String,
    onSavedFood: (mealSection: String, logDate: String) -> Unit,
    onLabelessFood: (mealSection: String, logDate: String) -> Unit,
    onManualEntry: (mealSection: String, logDate: String) -> Unit,
    onRecipe: (mealSection: String, logDate: String) -> Unit,
    onBarcode: (mealSection: String, logDate: String) -> Unit,
    onNutritionLabel: (mealSection: String, logDate: String) -> Unit,
    onBack: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Food",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "How would you like to add food?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            AddOptionCard(
                icon = Icons.Outlined.BookmarkBorder,
                title = "Saved / Premade Food",
                description = "Search your saved foods, premade items, and previous meal preps.",
                tint = AccentGreen,
                onClick = { onSavedFood(mealSection, logDate) }
            )

            AddOptionCard(
                icon = Icons.Outlined.Psychology,
                title = "Labeless Food",
                description = "Describe a food by name or voice. The AI model estimates the nutrition.",
                tint = EstimatedColor,
                onClick = { onLabelessFood(mealSection, logDate) }
            )

            AddOptionCard(
                icon = Icons.Outlined.Edit,
                title = "Manual Entry",
                description = "Type in a food name and enter nutrition values directly. No AI or scanning needed.",
                tint = MacroFat,
                onClick = { onManualEntry(mealSection, logDate) }
            )

            AddOptionCard(
                icon = Icons.Outlined.MenuBook,
                title = "Recipe / Meal Prep",
                description = "Build a recipe from ingredients and log one serving.",
                tint = MacroProtein,
                onClick = { onRecipe(mealSection, logDate) }
            )

            AddOptionCard(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Barcode Scan",
                description = "Scan a product barcode to look up nutrition facts automatically.",
                tint = ReminderAmber,
                onClick = { onBarcode(mealSection, logDate) }
            )

            AddOptionCard(
                icon = Icons.Outlined.DocumentScanner,
                title = "Nutrition Label Scan",
                description = "Photograph a nutrition facts label and extract values with OCR.",
                tint = MacroCarbs,
                onClick = { onNutritionLabel(mealSection, logDate) }
            )
        }
    }
}

@Composable
private fun AddOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
