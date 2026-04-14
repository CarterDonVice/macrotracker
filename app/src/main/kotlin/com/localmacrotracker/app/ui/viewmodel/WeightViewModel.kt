package com.localmacrotracker.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localmacrotracker.app.data.db.dao.WeightDao
import com.localmacrotracker.app.data.db.entities.WeightEntryEntity
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

enum class WeightTimeframe(val label: String, val days: Long?) {
    DAYS_7("7D", 7L),
    DAYS_30("30D", 30L),
    DAYS_90("90D", 90L),
    ALL("All", null)
}

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val weightDao: WeightDao
) : ViewModel() {

    private val _timeframe = MutableStateFlow(WeightTimeframe.DAYS_30)
    val timeframe: StateFlow<WeightTimeframe> = _timeframe.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val chartEntries: StateFlow<List<WeightEntryEntity>> = _timeframe
        .flatMapLatest { tf ->
            val cutoff = tf.days?.let { LocalDate.now().minusDays(it).toString() } ?: "0000-01-01"
            weightDao.getWeightsSince(cutoff)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayEntry: StateFlow<WeightEntryEntity?> = weightDao
        .getWeightsSince(LocalDate.now().toString())
        .map { list -> list.firstOrNull { it.logDate == LocalDate.now().toString() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTimeframe(tf: WeightTimeframe) {
        _timeframe.value = tf
    }

    fun logWeight(weightLbs: Double, date: LocalDate = LocalDate.now()) {
        if (weightLbs <= 0.0) return
        viewModelScope.launch {
            weightDao.upsertWeight(WeightEntryEntity(date.toString(), weightLbs))
        }
    }

    fun deleteEntry(date: String) {
        viewModelScope.launch {
            weightDao.deleteWeightForDate(date)
        }
    }
}
