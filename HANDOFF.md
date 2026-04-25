# MacroTracker Android — Engineering Handoff

**Repo:** `CarterDonVice/macrotracker` | **Branch:** `claude/android-app-project-zZL4w`  
**Stack:** Kotlin · Jetpack Compose · Material3 · Hilt · Room · CameraX · ML Kit · OkHttp · Retrofit

---

## App overview

Local-first Android macro tracker. No backend, no cloud sync. Food is logged via six entry methods:

| Entry method | How it works |
|---|---|
| Labeless food | User types/speaks → Claude Haiku parses → USDA/OFF lookup → confirm & log |
| Barcode scan | CameraX + ML Kit → OpenFoodFacts barcode lookup → log |
| Nutrition label OCR | CameraX + ML Kit text recognition → regex parser → log |
| Manual entry | User types name + macros |
| Saved foods search | Search user's personal food DB |
| Recipe builder | Multi-ingredient recipe → log one serving |

Start destination: `DailyLogScreen`. Navigation is in `AppNavigation.kt`.

---

## What is broken / needs work

### 1. Labeless food entry — Claude → USDA/OFF pipeline

**File:** `ui/viewmodel/LabelessFoodViewModel.kt` · `llm/ClaudeInferenceEngine.kt`

**What it's supposed to do:**
User types e.g. "2 scrambled eggs and toast with butter" → Claude extracts structured food items → parallel USDA + OFF searches → confirmation screen shows each item with macros → user edits if needed → logs to DB.

**Current flow:**
```
submit(input) 
  → ClaudeInferenceEngine.parseFoods(input)          // calls claude-haiku-4-5
  → returns List<ParsedFoodItem>
  → for each item: async { lookupItem(item, apiKey) } // USDA + OFF in parallel  
  → ConfirmedEntry list → UiState.Confirmation
  → confirmAndSave() → Room insert
```

**Suspected problems:**

1. **Scale calculation is wrong for USDA per-100g results.**  
   USDA frequently returns foods where `servingWeightGrams = 100.0` (per-100g basis). `computeScale()` does:
   ```kotlin
   item.quantity ?: 1.0   // if no weight given, scale = 1 or the parsed quantity
   ```
   So "2 eggs" → Claude returns `quantity=2, unit="large"` → scale = 2.0 → calories = 2 × (100g egg calories = ~155 kcal) = 310 kcal. Correct for 200g of egg, but a large egg is ~50g, so it should be ~160 kcal for 2 eggs. The scale needs to account for standard serving weight when `unit` is a count (egg, slice, cup, etc.) rather than a weight.

2. **USDA search query is too specific.**  
   `buildUsdaQuery()` concatenates `foodName + preparation + leanness + part + cookedOrRaw` → e.g. `"eggs scrambled cooked whole"` instead of just `"scrambled eggs"`. USDA's full-text search works better with simpler queries.

3. **Claude parse result might silently return empty.**  
   If Claude returns `{"type": "error", ...}`, `ClaudeInferenceEngine` catches this and returns `ParseResult.ParsedItems(emptyList())`. The ViewModel then shows "No foods found." The prompt and error handling are technically correct — but if Claude isn't returning well-structured JSON for common inputs, this will fail silently.

4. **No retry / fallback when both USDA and OFF return null.**  
   `lookupItem()` returns a `ConfirmedEntry` with `needsManualEntry = true` and all-null macros. The confirmation card shows a warning "Not found — tap edit to fill in values." This is intentional but the UX is poor if it happens frequently.

**Key files:**
```
llm/ClaudeInferenceEngine.kt         HTTP call to Claude, JSON parsing
llm/LlmOutputParser.kt               JSON array extraction helpers
llm/model/ParsedFoodItem.kt          Claude output schema
assets/prompts/search_planner_prompt.txt   System prompt sent to Claude
ui/viewmodel/LabelessFoodViewModel.kt  Full pipeline: Claude → lookup → confirm → save
ui/screens/LabelessFoodEntryScreen.kt  3-phase UI: Idle → Working → Confirmation
```

**Claude API details:**
- Endpoint: `https://api.anthropic.com/v1/messages`
- Model: `claude-haiku-4-5`
- Headers: `x-api-key: <key>`, `anthropic-version: 2023-06-01`
- Max tokens: 1024
- API key: stored in `local.properties` as `claude.api.key=...`, compiled into `BuildConfig.DEFAULT_CLAUDE_API_KEY`, falls back from DataStore → BuildConfig at runtime via `AppPreferences.claudeApiKey`
- Prompt template: `assets/prompts/search_planner_prompt.txt` — uses `{{USER_INPUT}}` placeholder

**USDA API:**
- Hardcoded fallback key in `LabelessFoodViewModel`: `FALLBACK_USDA_KEY`
- User can override in Settings → API Keys (saved to DataStore as `usdaApiKey`)
- Endpoint called via Retrofit `UsdaApi` — `searchFoods(query, apiKey, pageSize=3)`

---

### 2. Barcode scanner — CameraX + ML Kit + OpenFoodFacts

**File:** `ui/screens/BarcodeScanScreen.kt` · `ui/viewmodel/BarcodeScanViewModel.kt` · `data/network/providers/OpenFoodFactsProvider.kt`

**What it's supposed to do:**
Open camera → detect barcode → check local DB first → if not found, hit OpenFoodFacts barcode API → show result card → "Save + Add" saves to SavedFoodDao and logs, "Add Only" just logs.

**Current flow:**
```
BarcodeCameraPreview
  → ImageAnalysis analyzer → BarcodeScanning.getClient().process(image)
  → onBarcodeDetected(barcode.rawValue)
  → BarcodeScanViewModel.onBarcodeScanned(barcode)
      → savedFoodDao.getFoodByBarcode(barcode)    // local DB check
      → openFoodFactsProvider.lookup(barcode)     // OFF API if not local
      → _scanResult = Found / NewFood / NotFound
```

**Suspected problems:**

1. **`@ExperimentalGetImage` annotation in lambda doesn't propagate.**  
   In `BarcodeCameraPreview`, the analyzer lambda does:
   ```kotlin
   analysis.setAnalyzer(executor) { imageProxy ->
       @androidx.camera.core.ExperimentalGetImage
       val mediaImage = imageProxy.image   // annotation here is ignored in Kotlin
   ```
   The `@ExperimentalGetImage` annotation on a local variable declaration inside a lambda is not valid — it will either not compile cleanly or the lint suppression won't apply. Should be extracted to a separate annotated function or use `@SuppressLint`.

2. **`ScanOverlay` uses `BlendMode.Clear` which requires hardware acceleration layer.**  
   The `Canvas` composable draws a dark overlay then tries to punch a transparent hole with `BlendMode.Clear`. This only works if the Canvas is rendered into an offscreen layer. Without `Modifier.graphicsLayer { }` or similar, `BlendMode.Clear` may render as black rather than transparent on some devices.

3. **`setTargetResolution` is deprecated** in CameraX 1.3+. Should use `ResolutionSelector` instead. This may produce warnings but shouldn't break functionality.

4. **OpenFoodFacts barcode lookup may fail for some products.**  
   `OpenFoodFactsProvider.lookup(barcode)` checks `response.status != 1` — this is correct per the OFF API. However, many products only have `nutriments.caloriesPer100g` (no per-serving data). The provider falls back to per-100g correctly, but sets `servingText = "100g"` and `servingWeightGrams = 100.0`, which means the barcode result always shows 100g macros rather than the actual package serving size. This is confusing for users who expect to see macros per serving.

**Key files:**
```
ui/screens/BarcodeScanScreen.kt           Camera preview, overlay, result cards
ui/viewmodel/BarcodeScanViewModel.kt      DB lookup, OFF lookup, state machine
data/network/providers/OpenFoodFactsProvider.kt   Both barcode lookup() and text search()
data/network/api/OpenFoodFactsApi.kt      Retrofit interface
```

**OpenFoodFacts API:**
- Barcode endpoint: `https://world.openfoodfacts.org/api/v0/product/{barcode}.json`
- Search endpoint: `https://world.openfoodfacts.org/cgi/search.pl?search_terms={query}&json=1`
- No API key required
- `response.status == 1` means product found; `response.product` contains the data

---

### 3. Nutrition label OCR

**File:** `ui/screens/NutritionLabelScanScreen.kt` · `ui/viewmodel/NutritionLabelViewModel.kt` · `domain/OcrLabelParserImpl.kt`

This feature uses CameraX + ML Kit text recognition to photograph a nutrition label and extract macros via regex. Has the same `@ExperimentalGetImage` annotation issue as barcode. The regex parser in `OcrLabelParserImpl` may also miss values in certain label layouts.

---

## Project structure (quick reference)

```
app/src/main/kotlin/com/localmacrotracker/app/
├── data/
│   ├── db/                        Room DB — AppDatabase, 5 DAOs, entities
│   ├── model/                     Domain models (FoodCandidate, MealSection, etc.)
│   ├── network/
│   │   ├── api/                   UsdaApi.kt, OpenFoodFactsApi.kt (Retrofit)
│   │   └── providers/             USDA, OFF, Brand, Restaurant, Grocery, LocalSaved
│   └── prefs/AppPreferences.kt    DataStore wrapper; Claude key: DataStore → BuildConfig fallback
├── di/AppModule.kt                Hilt: OkHttp, Room, Retrofit, OCR binding
├── domain/
│   ├── FoodLookupOrchestrator.kt  Provider chain (used by SavedFoods, not labeless)
│   ├── OcrLabelParserImpl.kt      Regex-based nutrition label parser
│   └── ServingMath.kt             Portion factor helpers
├── llm/
│   ├── ClaudeInferenceEngine.kt   OkHttp call to Anthropic API, sealed ParseResult
│   ├── LlmOutputParser.kt         JSON extraction from Claude response text
│   └── model/ParsedFoodItem.kt    Claude output schema (food_name, quantity, unit, etc.)
└── ui/
    ├── navigation/AppNavigation.kt
    ├── screens/                   12 screens
    ├── theme/                     Color.kt (light palette), Theme.kt, Type.kt
    └── viewmodel/                 One per screen
```

### Theme (light mode — recently redesigned)
```kotlin
DarkBackground    = #F0F7F3   // soft mint-white page
DarkSurface       = #FFFFFF   // white cards
AccentGreen       = #1B6B45   // deep forest emerald (primary)
TextPrimary       = #1A2B22   // near-black
TextSecondary     = #5A7266   // muted sage
MacroProtein      = #2563EB   // blue
MacroCarbs        = #D97706   // amber
MacroFat          = #9333EA   // purple
MacroCalories     = #EA580C   // orange
```

### Navigation routes
```
daily_log                                          (start)
add_entry_chooser/{meal_section}/{log_date}
labeless_food/{meal_section}/{log_date}
barcode_scan/{meal_section}/{log_date}
nutrition_label_scan/{meal_section}/{log_date}
manual_food/{meal_section}/{log_date}
saved_foods_search/{meal_section}/{log_date}
food_review/{entry_id}
recipe/new?meal_section={}&log_date={}
saved_food_detail/{food_id}
settings
weight_tracker
```

---

## What to fix — prioritized

### Priority 1 — Barcode scanner rendering fix
In `BarcodeScanScreen.kt`, fix the `ScanOverlay` so the transparent scan window actually renders correctly:
```kotlin
// Current (broken on some devices):
Canvas(modifier = Modifier.fillMaxSize()) {
    drawRect(Color.Black.copy(alpha = 0.45f))
    drawRoundRect(color = Color.Transparent, ..., blendMode = BlendMode.Clear)  // doesn't work
}

// Fix: wrap in graphicsLayer to force offscreen rendering
Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) {
    drawRect(Color.Black.copy(alpha = 0.45f))
    drawRoundRect(color = Color.Transparent, ..., blendMode = BlendMode.Clear)
}
```

Also fix the `@ExperimentalGetImage` annotation — extract the image processing into a proper annotated function:
```kotlin
@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onBarcodeDetected: (Barcode) -> Unit,
    lastScanned: String,
    onScanned: (String) -> Unit
) { ... }
```

### Priority 2 — Labeless food scale calculation
In `LabelessFoodViewModel.computeScale()`, the current logic treats `item.quantity` as a direct multiplier on top of whatever serving size USDA returned. Fix:
- If USDA returns per-100g data AND item has a count unit (egg, slice, piece, cup, tbsp, etc.), use standard weight lookup table to convert count → grams → scale
- If item has explicit `weightG` or `weightOz`, use that (current behavior, correct)
- If item has quantity + volume unit, estimate gram weight via density lookup
- Fallback: keep current behavior (quantity × serving)

### Priority 3 — USDA search query quality
In `buildUsdaQuery()`, simplify the query to just `"$foodName $preparation"` instead of appending all modifiers. The USDA API's full-text search doesn't benefit from leanness/part/fatContent appended to the query string.

### Priority 4 — OCR label scanner `@ExperimentalGetImage`
Same annotation fix as barcode scanner — extract the `imageProxy.image` access into a separate `@ExperimentalGetImage`-annotated function in `NutritionLabelScanScreen.kt`.

### Priority 5 — OpenFoodFacts serving size display
When `usesPer100g = true` in `OpenFoodFactsProvider.mapToCandidate()`, try to use `product.servingSize` as the display text even if the nutriment values are per-100g. If serving size is e.g. "30g", show "30g serving" and scale macros × 0.3 rather than showing raw 100g values.

---

## Do NOT touch
- Room database schema — any field rename or addition needs a proper `Migration` in `AppDatabase.kt`
- `FoodLogEntryEntity` field names — referenced in DAO queries throughout
- Navigation route strings — deep links depend on exact route patterns
- `AppPreferences` DataStore key strings — changing them loses user's saved settings

---

## Git / push instructions
The local git proxy at `127.0.0.1:...` breaks between sessions. Before every push:
```bash
git remote set-url origin https://<YOUR_GITHUB_PAT>@github.com/CarterDonVice/macrotracker.git
git push -u origin claude/android-app-project-zZL4w
```
Never put the PAT value in any committed file — GitHub's secret scanning will block the push.

The `local.properties` file (gitignored) contains the Claude API key and Android SDK path. It must exist locally to build — it is never committed.
