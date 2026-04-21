package com.localmacrotracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localmacrotracker.app.data.db.entities.WeightEntryEntity
import com.localmacrotracker.app.ui.theme.*
import com.localmacrotracker.app.ui.viewmodel.WeightTimeframe
import com.localmacrotracker.app.ui.viewmodel.WeightViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightTrackerScreen(
    onBack: () -> Unit,
    viewModel: WeightViewModel = hiltViewModel()
) {
    val entries by viewModel.chartEntries.collectAsStateWithLifecycle()
    val todayEntry by viewModel.todayEntry.collectAsStateWithLifecycle()
    val timeframe by viewModel.timeframe.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    var weightInput by remember { mutableStateOf("") }

    // Past-entry dialog state
    var showPastEntryDialog by remember { mutableStateOf(false) }
    var pastEntryDate by remember { mutableStateOf(LocalDate.now()) }
    var pastEntryWeight by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Pre-fill input when today's entry loads
    LaunchedEffect(todayEntry) {
        if (todayEntry != null && weightInput.isBlank()) {
            weightInput = todayEntry!!.weightLbs.let {
                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Weight Tracker",
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
                actions = {
                    IconButton(onClick = {
                        pastEntryDate = LocalDate.now()
                        pastEntryWeight = ""
                        showPastEntryDialog = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Log past entry", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── Log today's weight ──────────────────────────────────────
            item {
                Surface(color = DarkSurface, shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (todayEntry != null) "Today's Weight" else "Log Today's Weight",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = { weightInput = it },
                                label = { Text("Weight (lbs)") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    keyboard?.hide()
                                    weightInput.toDoubleOrNull()?.let { viewModel.logWeight(it) }
                                }),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentGreen,
                                    unfocusedBorderColor = Divider,
                                    focusedLabelColor = AccentGreen,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = AccentGreen,
                                    focusedContainerColor = DarkSurface,
                                    unfocusedContainerColor = DarkSurface
                                )
                            )
                            Button(
                                onClick = {
                                    keyboard?.hide()
                                    weightInput.toDoubleOrNull()?.let { viewModel.logWeight(it) }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (todayEntry != null) "Update" else "Log",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        todayEntry?.let {
                            Text(
                                "Logged: ${it.weightLbs} lbs",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentGreen
                            )
                        }
                    }
                }
            }

            // ── Timeframe selector ──────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeightTimeframe.entries.forEach { tf ->
                        FilterChip(
                            selected = timeframe == tf,
                            onClick = { viewModel.setTimeframe(tf) },
                            label = {
                                Text(
                                    tf.label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.2f),
                                selectedLabelColor = AccentGreen,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }

            // ── Graph ───────────────────────────────────────────────────
            item {
                Surface(color = DarkSurface, shape = RoundedCornerShape(12.dp)) {
                    if (entries.size < 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (entries.isEmpty()) "No data for this period"
                                       else "Log at least 2 entries to see the graph",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val minW = entries.minOf { it.weightLbs }
                            val maxW = entries.maxOf { it.weightLbs }
                            Text(
                                text = "%.1f – %.1f lbs".format(minW, maxW),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.align(Alignment.End)
                            )
                            Spacer(Modifier.height(4.dp))
                            WeightGraph(
                                entries = entries,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    }
                }
            }

            // ── History header ──────────────────────────────────────────
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "No entries for this period.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(entries.reversed(), key = { it.logDate }) { entry ->
                    WeightEntryRow(
                        entry = entry,
                        onDelete = { viewModel.deleteEntry(entry.logDate) }
                    )
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    // ── Past entry dialog ───────────────────────────────────────────────
    if (showPastEntryDialog) {
        val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        AlertDialog(
            onDismissRequest = { showPastEntryDialog = false },
            title = {
                Text(
                    "Log Past Weight Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Date selector — tapping opens DatePickerDialog
                    OutlinedTextField(
                        value = pastEntryDate.format(dateFmt),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    Icons.Filled.CalendarToday,
                                    contentDescription = "Pick date",
                                    tint = AccentGreen
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = Divider,
                            focusedLabelColor = AccentGreen,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGreen,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        )
                    )
                    // Weight input
                    OutlinedTextField(
                        value = pastEntryWeight,
                        onValueChange = { pastEntryWeight = it },
                        label = { Text("Weight (lbs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = Divider,
                            focusedLabelColor = AccentGreen,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGreen,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pastEntryWeight.toDoubleOrNull()?.let { w ->
                            viewModel.logWeight(w, pastEntryDate)
                            showPastEntryDialog = false
                        }
                    },
                    enabled = pastEntryWeight.toDoubleOrNull() != null
                ) {
                    Text("Save", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastEntryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary
        )
    }

    // ── Date picker ─────────────────────────────────────────────────────
    if (showDatePicker) {
        val initialMillis = pastEntryDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    // Allow any date up to today
                    val today = LocalDate.now()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli()
                    return utcTimeMillis <= today
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        pastEntryDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = DarkSurface)
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary,
                    headlineContentColor = TextPrimary,
                    weekdayContentColor = TextSecondary,
                    subheadContentColor = TextSecondary,
                    navigationContentColor = TextSecondary,
                    yearContentColor = TextPrimary,
                    currentYearContentColor = AccentGreen,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = AccentGreen,
                    dayContentColor = TextPrimary,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = AccentGreen,
                    todayContentColor = AccentGreen,
                    todayDateBorderColor = AccentGreen
                )
            )
        }
    }
}

@Composable
private fun WeightGraph(
    entries: List<WeightEntryEntity>,
    modifier: Modifier = Modifier
) {
    val minW = entries.minOf { it.weightLbs }
    val maxW = entries.maxOf { it.weightLbs }
    val rawRange = (maxW - minW).coerceAtLeast(3.0)
    val pad = rawRange * 0.15
    val yMin = minW - pad
    val yMax = maxW + pad
    val today = LocalDate.now().toString()
    val n = entries.size

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun xOf(i: Int) = if (n > 1) (i.toFloat() / (n - 1)) * w else w / 2f
        fun yOf(weight: Double) = (h * (1.0 - (weight - yMin) / (yMax - yMin))).toFloat()
            .coerceIn(0f, h)

        listOf(0.25f, 0.5f, 0.75f).forEach { pct ->
            val yPx = h * pct
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, yPx),
                end = Offset(w, yPx),
                strokeWidth = 1.dp.toPx()
            )
        }

        val path = Path()
        entries.forEachIndexed { i, entry ->
            val x = xOf(i)
            val y = yOf(entry.weightLbs)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = AccentGreen, style = Stroke(width = 2.dp.toPx()))

        entries.forEachIndexed { i, entry ->
            val x = xOf(i)
            val y = yOf(entry.weightLbs)
            val isToday = entry.logDate == today
            drawCircle(
                color = if (isToday) AccentGreen else AccentGreen.copy(alpha = 0.55f),
                radius = if (isToday) 5.dp.toPx() else 3.dp.toPx(),
                center = Offset(x, y)
            )
            if (isToday) {
                drawCircle(
                    color = DarkBackground,
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun WeightEntryRow(
    entry: WeightEntryEntity,
    onDelete: () -> Unit
) {
    val fmt = DateTimeFormatter.ofPattern("EEE, MMM d")
    val dateLabel = runCatching {
        LocalDate.parse(entry.logDate).format(fmt)
    }.getOrElse { entry.logDate }
    val isToday = entry.logDate == LocalDate.now().toString()

    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isToday) "Today" else dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isToday) AccentGreen else TextPrimary
                )
                if (!isToday) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                text = "${entry.weightLbs} lbs",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
