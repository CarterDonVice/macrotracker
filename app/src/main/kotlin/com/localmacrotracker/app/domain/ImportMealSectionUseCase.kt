package com.localmacrotracker.app.domain

import com.localmacrotracker.app.data.db.dao.FoodLogDao
import com.localmacrotracker.app.data.db.entities.FoodLogEntryEntity
import com.localmacrotracker.app.data.model.MealSection
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies the previous day's meal section entries into today's log.
 * Creates NEW independent entries — no live link to the originals.
 */
@Singleton
class ImportMealSectionUseCase @Inject constructor(
    private val foodLogDao: FoodLogDao
) {
    suspend fun import(
        targetDate: LocalDate,
        mealSection: MealSection
    ): List<FoodLogEntryEntity> {
        val previousDate = targetDate.minusDays(1).toString()
        val sourceEntries = foodLogDao.getEntriesForDateAndSection(
            previousDate, mealSection.name
        )
        if (sourceEntries.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val copies = sourceEntries.map { source ->
            source.copy(
                id = 0,  // auto-generate new ID
                logDate = targetDate.toString(),
                createdAt = now,
                updatedAt = now
            )
        }

        val ids = foodLogDao.insertEntries(copies)
        return copies.mapIndexed { i, entry -> entry.copy(id = ids[i]) }
    }
}
