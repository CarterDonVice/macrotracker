package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.MealSection
import com.localmacrotracker.app.domain.DailyTotalsCalculator
import com.localmacrotracker.app.domain.ImportMealSectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DailyLogViewModel @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val importMealSectionUseCase: ImportMealSectionUseCase
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val allEntries: StateFlow<List<FoodLogEntryEntity>> = _selectedDate
        .flatMapLatest { date -> foodLogDao.getEntriesForDate(date.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val entriesBySection: StateFlow<Map<MealSection, List<FoodLogEntryEntity>>> = _selectedDate
        .flatMapLatest { date -> foodLogDao.getEntriesForDate(date.toString()) }
        .map { entries ->
            MealSection.values().associateWith { section ->
                entries.filter { it.mealSection == section.name }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val dailyTotals: StateFlow<DailyTotalsCalculator.DailyTotals> = allEntries
        .map { entries -> DailyTotalsCalculator.calculate(entries) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DailyTotalsCalculator.DailyTotals.Empty
        )

    private val _clipboard = MutableStateFlow<List<FoodLogEntryEntity>?>(null)
    val clipboard: StateFlow<List<FoodLogEntryEntity>?> = _clipboard.asStateFlow()

    fun setDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            foodLogDao.deleteEntryById(entryId)
        }
    }

    fun copySection(section: MealSection) {
        viewModelScope.launch {
            _clipboard.value = foodLogDao.getEntriesForDateAndSection(
                _selectedDate.value.toString(), section.name
            )
        }
    }

    fun pasteSection(targetSection: MealSection) {
        val entries = _clipboard.value?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val newEntries = entries.map { e ->
                e.copy(
                    id = 0,
                    logDate = _selectedDate.value.toString(),
                    mealSection = targetSection.name,
                    createdAt = now,
                    updatedAt = now
                )
            }
            foodLogDao.insertEntries(newEntries)
        }
    }

    fun importPreviousSection(mealSection: MealSection) {
        viewModelScope.launch {
            importMealSectionUseCase.import(
                targetDate = _selectedDate.value,
                mealSection = mealSection
            )
        }
    }

    fun updateEntry(entry: FoodLogEntryEntity) {
        viewModelScope.launch {
            foodLogDao.updateEntry(entry.copy(updatedAt = System.currentTimeMillis()))
        }
    }
}
