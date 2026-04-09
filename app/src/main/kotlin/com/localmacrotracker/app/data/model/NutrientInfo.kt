package com.localmacrotracker.app.data.model

data class NutrientInfo(
    val code: String,
    val displayName: String,
    val amount: Double,
    val unit: String
)

/** Static catalog of supported micronutrient codes and display names. */
object NutrientCatalog {
    data class NutrientDef(val code: String, val displayName: String, val unit: String)

    val ALL: List<NutrientDef> = listOf(
        NutrientDef("FIBTG", "Fiber", "g"),
        NutrientDef("SUGAR", "Sugar", "g"),
        NutrientDef("SUGAR.added", "Added Sugar", "g"),
        NutrientDef("NA", "Sodium", "mg"),
        NutrientDef("K", "Potassium", "mg"),
        NutrientDef("CA", "Calcium", "mg"),
        NutrientDef("FE", "Iron", "mg"),
        NutrientDef("MG", "Magnesium", "mg"),
        NutrientDef("P", "Phosphorus", "mg"),
        NutrientDef("ZN", "Zinc", "mg"),
        NutrientDef("SE", "Selenium", "mcg"),
        NutrientDef("VITA_RAE", "Vitamin A", "mcg RAE"),
        NutrientDef("VITC", "Vitamin C", "mg"),
        NutrientDef("VITD", "Vitamin D", "mcg"),
        NutrientDef("VITE", "Vitamin E", "mg"),
        NutrientDef("VITK1", "Vitamin K", "mcg"),
        NutrientDef("THIA", "Thiamin (B1)", "mg"),
        NutrientDef("RIBF", "Riboflavin (B2)", "mg"),
        NutrientDef("NIA", "Niacin (B3)", "mg"),
        NutrientDef("PANTAC", "Pantothenic Acid (B5)", "mg"),
        NutrientDef("VITB6A", "Vitamin B6", "mg"),
        NutrientDef("FOL", "Folate", "mcg"),
        NutrientDef("VITB12", "Vitamin B12", "mcg"),
        NutrientDef("CHOLN", "Choline", "mg"),
        NutrientDef("FASAT", "Saturated Fat", "g"),
        NutrientDef("FATRN", "Trans Fat", "g"),
        NutrientDef("FAMS", "Monounsaturated Fat", "g"),
        NutrientDef("FAPU", "Polyunsaturated Fat", "g"),
        NutrientDef("F18D2CN6", "Omega-6 (Linoleic)", "g"),
        NutrientDef("F18D3CN3", "Omega-3 (ALA)", "g"),
        NutrientDef("F20D5CN3", "Omega-3 (EPA)", "g"),
        NutrientDef("F22D6CN3", "Omega-3 (DHA)", "g"),
        NutrientDef("CHOLE", "Cholesterol", "mg"),
    )

    fun byCode(code: String): NutrientDef? = ALL.firstOrNull { it.code == code }
}
