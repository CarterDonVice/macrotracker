package com.localmacrotracker.app.data.model

/** Broad family a unit belongs to, used to decide how a serving size is stored. */
enum class UnitKind { WEIGHT, VOLUME, COUNT }

/**
 * A selectable unit of measurement for manual food entry.
 *
 * @param label     short form shown in the field, e.g. "g", "cup"
 * @param fullName  spelled-out name, also matched when searching the dropdown
 * @param kind      weight / volume / count
 * @param grams     grams per 1 unit (WEIGHT units only) — lets us store servingWeightGrams
 * @param milliliters ml per 1 unit (VOLUME units only) — lets us store servingVolumeMl
 */
data class MeasurementUnit(
    val label: String,
    val fullName: String,
    val kind: UnitKind,
    val grams: Double? = null,
    val milliliters: Double? = null
)

object MeasurementUnits {
    val ALL: List<MeasurementUnit> = listOf(
        // ── Weight ──
        MeasurementUnit("g", "grams", UnitKind.WEIGHT, grams = 1.0),
        MeasurementUnit("mg", "milligrams", UnitKind.WEIGHT, grams = 0.001),
        MeasurementUnit("kg", "kilograms", UnitKind.WEIGHT, grams = 1000.0),
        MeasurementUnit("oz", "ounces", UnitKind.WEIGHT, grams = 28.3495),
        MeasurementUnit("lb", "pounds", UnitKind.WEIGHT, grams = 453.592),
        // ── Volume ──
        MeasurementUnit("ml", "milliliters", UnitKind.VOLUME, milliliters = 1.0),
        MeasurementUnit("L", "liters", UnitKind.VOLUME, milliliters = 1000.0),
        MeasurementUnit("tsp", "teaspoon", UnitKind.VOLUME, milliliters = 4.92892),
        MeasurementUnit("tbsp", "tablespoon", UnitKind.VOLUME, milliliters = 14.7868),
        MeasurementUnit("fl oz", "fluid ounce", UnitKind.VOLUME, milliliters = 29.5735),
        MeasurementUnit("cup", "cup", UnitKind.VOLUME, milliliters = 236.588),
        MeasurementUnit("pint", "pint", UnitKind.VOLUME, milliliters = 473.176),
        MeasurementUnit("quart", "quart", UnitKind.VOLUME, milliliters = 946.353),
        MeasurementUnit("gallon", "gallon", UnitKind.VOLUME, milliliters = 3785.41),
        // ── Count ──
        MeasurementUnit("serving", "serving", UnitKind.COUNT),
        MeasurementUnit("piece", "piece", UnitKind.COUNT),
        MeasurementUnit("slice", "slice", UnitKind.COUNT),
        MeasurementUnit("scoop", "scoop", UnitKind.COUNT),
        MeasurementUnit("packet", "packet", UnitKind.COUNT),
        MeasurementUnit("can", "can", UnitKind.COUNT),
        MeasurementUnit("bottle", "bottle", UnitKind.COUNT),
        MeasurementUnit("egg", "egg", UnitKind.COUNT),
        MeasurementUnit("bar", "bar", UnitKind.COUNT),
        MeasurementUnit("cookie", "cookie", UnitKind.COUNT),
        MeasurementUnit("handful", "handful", UnitKind.COUNT)
    )

    /** Case-insensitive filter over both the short label and the full name. */
    fun search(query: String): List<MeasurementUnit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return ALL
        return ALL.filter { it.label.lowercase().contains(q) || it.fullName.lowercase().contains(q) }
    }
}
