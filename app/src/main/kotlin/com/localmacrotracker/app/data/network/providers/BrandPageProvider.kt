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

private const val TAG = "BrandPageProvider"

/**
 * Attempts to fetch nutrition from a brand's public nutrition page.
 * Configuration-driven: uses the likely_domain_hint from the planner if set.
 * Fetches at most ONE page per attempt and fails cleanly if unparseable.
 */
@Singleton
class BrandPageProvider @Inject constructor(
    private val httpClient: OkHttpClient
) : FoodLookupProvider {

    override val providerName = "brand_page"

    override suspend fun search(query: String, plannerItem: PlannerItem?): List<FoodCandidate> {
        // Without a specific domain hint we cannot do targeted brand lookup
        val domainHint = plannerItem?.likelyDomainHint?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        val brandName = plannerItem.brandName?.takeIf { it.isNotBlank() }

        return try {
            val url = buildBrandUrl(domainHint, query, brandName)
            val html = fetchPage(url) ?: return emptyList()
            parseBrandPageForNutrition(html, query, url)
        } catch (e: Exception) {
            Log.w(TAG, "Brand page fetch/parse failed for '$query' on '$domainHint'", e)
            emptyList()
        }
    }

    private fun buildBrandUrl(domain: String, query: String, brand: String?): String {
        val base = if (domain.startsWith("http")) domain else "https://$domain"
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return "$base/search?q=$q"
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

    private fun parseBrandPageForNutrition(
        html: String,
        query: String,
        sourceUrl: String
    ): List<FoodCandidate> {
        // Generic heuristic: look for common nutrition fact patterns in the page
        val doc = Jsoup.parse(html)

        // Many nutrition pages use these CSS patterns
        val calText = doc.select("[class*=calorie],[class*=calories],[class*=nutrition] [class*=cal]")
            .firstOrNull()?.text()
        val calories = extractNumber(calText) ?: return emptyList()

        val proteinText = doc.select("[class*=protein]").firstOrNull()?.text()
        val carbText = doc.select("[class*=carb]").firstOrNull()?.text()
        val fatText = doc.select("[class*=fat]").firstOrNull()?.text()

        val protein = extractNumber(proteinText) ?: 0.0
        val carbs = extractNumber(carbText) ?: 0.0
        val fat = extractNumber(fatText) ?: 0.0

        if (calories <= 0.0) return emptyList()

        return listOf(
            FoodCandidate(
                id = "brand_${sourceUrl.hashCode()}",
                displayName = query,
                sourceType = SourceType.BRAND_PAGE,
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
        val match = Regex("""(\d+(?:\.\d+)?)""").find(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }
}
