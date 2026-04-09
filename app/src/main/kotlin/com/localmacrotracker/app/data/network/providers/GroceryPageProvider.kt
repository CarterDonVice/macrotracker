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

private const val TAG = "GroceryPageProvider"

@Singleton
class GroceryPageProvider @Inject constructor(
    private val httpClient: OkHttpClient
) : FoodLookupProvider {

    override val providerName = "grocery_page"

    override suspend fun search(query: String, plannerItem: PlannerItem?): List<FoodCandidate> {
        // Use Instacart's public product nutrition pages as a best-effort free source.
        // Only fetch if there's a brand hint; generic searches here are too noisy.
        val brandHint = plannerItem?.brandName?.takeIf { it.isNotBlank() }
            ?: plannerItem?.likelyDomainHint?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        val searchQuery = "$brandHint $query"
        val url = "https://www.instacart.com/store/items?utf8=%E2%9C%93&search_id=&q=" +
                java.net.URLEncoder.encode(searchQuery, "UTF-8")

        return try {
            val html = fetchPage(url) ?: return emptyList()
            parseGroceryPage(html, query, url)
        } catch (e: Exception) {
            Log.w(TAG, "Grocery page parse failed for '$query'", e)
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

    private fun parseGroceryPage(html: String, query: String, sourceUrl: String): List<FoodCandidate> {
        // Grocery sites are often JS-rendered — if we can't parse nutrition facts, fail cleanly
        val doc = Jsoup.parse(html)
        val calText = doc.select(
            "[class*=calorie],[class*=nutrition] span,[data-testid*=calorie]"
        ).firstOrNull()?.text() ?: return emptyList()

        val calories = extractNumber(calText) ?: return emptyList()
        val protein = extractNumber(
            doc.select("[class*=protein]").firstOrNull()?.text()
        ) ?: 0.0
        val carbs = extractNumber(
            doc.select("[class*=carb]").firstOrNull()?.text()
        ) ?: 0.0
        val fat = extractNumber(
            doc.select("[class*=fat]").firstOrNull()?.text()
        ) ?: 0.0

        if (calories <= 0.0) return emptyList()

        return listOf(
            FoodCandidate(
                id = "grocery_${sourceUrl.hashCode()}",
                displayName = query,
                sourceType = SourceType.GROCERY_PAGE,
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
