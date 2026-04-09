package com.localmacrotracker.app.data.network.providers

import android.util.Log
import com.localmacrotracker.app.data.model.ExactnessType
import com.localmacrotracker.app.data.model.FoodCandidate
import com.localmacrotracker.app.data.model.SourceType
import com.localmacrotracker.app.data.network.FoodLookupProvider
import com.localmacrotracker.app.llm.model.PlannerItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RestaurantPageProvider"

/**
 * Known restaurant nutrition page templates.
 * Each entry: restaurantNameKey (lowercased) -> URL template.
 * %s is replaced by URL-encoded item query.
 */
private val KNOWN_RESTAURANTS = mapOf(
    "mcdonald's" to "https://www.mcdonalds.com/us/en-us/product/%s.html",
    "chipotle" to "https://www.chipotle.com/en-us/nutrition-calculator",
    "subway" to "https://www.subway.com/en-US/MenuNutrition/Menu",
    "olive garden" to "https://www.olivegarden.com/menu/nutritional-info",
    "panera" to "https://www.panerabread.com/en-us/articles/nutritional-information.html",
)

@Singleton
class RestaurantPageProvider @Inject constructor(
    private val httpClient: OkHttpClient
) : FoodLookupProvider {

    override val providerName = "restaurant_page"

    override suspend fun search(query: String, plannerItem: PlannerItem?): List<FoodCandidate> {
        val restaurantName = plannerItem?.restaurantName?.lowercase()?.trim()
            ?: return emptyList()

        // We only support known chains with public static nutrition pages
        val urlTemplate = KNOWN_RESTAURANTS.entries
            .firstOrNull { restaurantName.contains(it.key) }
            ?.value ?: run {
            Log.d(TAG, "No known URL template for restaurant: $restaurantName")
            return emptyList()
        }

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = urlTemplate.replace("%s", encodedQuery)

        return try {
            val html = fetchPage(url) ?: return emptyList()
            parseRestaurantPage(html, query, url)
        } catch (e: Exception) {
            Log.w(TAG, "Restaurant page parse failed for '$query'", e)
            emptyList()
        }
    }

    private fun fetchPage(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; MacroTracker nutrition lookup)")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP fetch failed: $url", e)
            null
        }
    }

    private fun parseRestaurantPage(html: String, query: String, sourceUrl: String): List<FoodCandidate> {
        val doc = Jsoup.parse(html)
        // Generic heuristic for restaurant nutrition pages
        val calEl = doc.select(
            "[class*=calories],[data-nutrient=calories],[id*=calorie],[class*=calorie]"
        ).firstOrNull()
        val calories = extractNumber(calEl?.text()) ?: return emptyList()

        val protein = extractNumber(
            doc.select("[class*=protein],[data-nutrient=protein]").firstOrNull()?.text()
        ) ?: 0.0
        val carbs = extractNumber(
            doc.select("[class*=carb],[data-nutrient=carbohydrate]").firstOrNull()?.text()
        ) ?: 0.0
        val fat = extractNumber(
            doc.select("[class*=fat],[data-nutrient=fat]").firstOrNull()?.text()
        ) ?: 0.0

        if (calories <= 0.0) return emptyList()

        return listOf(
            FoodCandidate(
                id = "restaurant_${sourceUrl.hashCode()}",
                displayName = query,
                sourceType = SourceType.RESTAURANT_PAGE,
                sourceUrl = sourceUrl,
                calories = calories,
                proteinGrams = protein,
                carbsGrams = carbs,
                fatGrams = fat,
                exactnessType = ExactnessType.EXACT
            )
        )
    }

    private fun extractNumber(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        return Regex("""(\d+(?:\.\d+)?)""").find(text)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
