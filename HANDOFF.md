# MacroTracker Android — Session Handoff

Use this document to pick up where we left off in a new Claude Code session.  
**Repo:** `CarterDonVice/macrotracker`  
**Branch:** `claude/android-app-project-zZL4w`  
**Last commit:** `d197bf8` — Fix: downgrade mlkit barcode-scanning 18.3.1 → 17.3.0

---

## What This App Is

A local-first Android macro/calorie tracking app. No backend, no cloud sync, no ads.  
Built with Kotlin + Jetpack Compose + Material3, Hilt DI, Room database, CameraX, ML Kit.

Key entry methods:
- **Labeless food entry** — type a food description → Claude Haiku parses it → searches USDA / OpenFoodFacts / saved foods → logs macros
- **Barcode scan** — CameraX + ML Kit barcode scanning → OpenFoodFacts lookup
- **Nutrition label OCR** — CameraX + ML Kit text recognition → regex parser extracts macros
- **Manual entry** — user types name + macros directly
- **Recipe builder** — multi-ingredient recipe → log one serving
- **Saved foods search** — search user's personal food database

---

## Project Structure

```
app/src/main/kotlin/com/localmacrotracker/app/
├── MacroTrackerApp.kt               Hilt application class
├── MainActivity.kt
├── data/
│   ├── db/                          Room database, DAOs, entities
│   │   ├── AppDatabase.kt
│   │   ├── dao/  (FoodLog, LookupCache, Recipe, SavedFood, Weight)
│   │   └── entities/
│   ├── model/                       Domain models (FoodCandidate, MealSection, etc.)
│   ├── network/                     Retrofit APIs + provider chain
│   │   ├── FoodLookupProvider.kt    Interface all providers implement
│   │   ├── api/  (UsdaApi, OpenFoodFactsApi)
│   │   └── providers/  (USDA, OFF, Brand, Restaurant, Grocery, LocalSaved)
│   └── prefs/
│       └── AppPreferences.kt        DataStore wrapper; Claude key fallback → BuildConfig
├── di/
│   └── AppModule.kt                 Hilt bindings (OkHttp, Room, OCR parser)
├── domain/
│   ├── DailyTotalsCalculator.kt
│   ├── FoodLookupOrchestrator.kt    Provider chain orchestration (no LLM dependency)
│   ├── ImportMealSectionUseCase.kt
│   ├── OcrLabelParserImpl.kt
│   ├── RecipeCalculator.kt
│   └── ServingMath.kt
├── llm/
│   ├── ClaudeInferenceEngine.kt     Calls claude-haiku-4-5 via OkHttp; sealed ParseResult
│   ├── LlmOutputParser.kt           JSON extraction helpers
│   └── model/  (ParsedFoodItem, PlannerOutput, CandidateSelection, RangeResult)
└── ui/
    ├── components/  (CommonComponents, MacroSummaryRow)
    ├── navigation/  AppNavigation.kt
    ├── screens/     (12 screens — see list below)
    ├── theme/       Color.kt, Theme.kt, Type.kt
    └── viewmodel/   (one per screen)
```

### Screens

| Screen | Route |
|--------|-------|
| DailyLogScreen | `daily_log` (start destination) |
| AddEntryChooserScreen | `add_entry_chooser/{meal_section}/{log_date}` |
| SavedFoodsSearchScreen | `saved_foods_search/{meal_section}/{log_date}` |
| LabelessFoodEntryScreen | `labeless_food/{meal_section}/{log_date}` |
| ManualFoodEntryScreen | `manual_food/{meal_section}/{log_date}` |
| BarcodeScanScreen | `barcode_scan/{meal_section}/{log_date}` |
| NutritionLabelScanScreen | `nutrition_label_scan/{meal_section}/{log_date}` |
| FoodReviewScreen | `food_review/{entry_id}` |
| RecipeScreen | `recipe/new?meal_section={}&log_date={}` |
| SavedFoodDetailScreen | `saved_food_detail/{food_id}` |
| SettingsScreen | `settings` |
| WeightTrackerScreen | `weight_tracker` |

---

## Key Dependencies (`gradle/libs.versions.toml`)

```toml
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
composeBom = "2024.12.01"
room = "2.6.1"
hilt = "2.54"
camerax = "1.4.1"
mlkitBarcode = "17.3.0"          # was 18.3.1, downgraded — that version doesn't exist
mlkitTextRecognition = "16.0.1"
okhttp = "4.12.0"
retrofit = "2.11.0"
kotlinSerialization = "1.7.3"
datastore = "1.1.1"
coil = "2.7.0"
workmanager = "2.10.0"
accompanist = "0.34.0"
```

---

## What Was Done This Session (in order)

### 1. Secret scanning fix
- GitHub push protection blocked a commit because `AppPreferences.kt` contained a hardcoded Anthropic API key literal
- Moved key to `local.properties` (gitignored)
- Added `buildConfigField("String", "DEFAULT_CLAUDE_API_KEY", ...)` in `app/build.gradle.kts`
- Added `import java.util.Properties` at the top of the Gradle file (Kotlin DSL requires explicit import)
- `AppPreferences.claudeApiKey` now falls back to `BuildConfig.DEFAULT_CLAUDE_API_KEY` if DataStore is empty

### 2. MediaPipe / local LLM — completely removed

**Files deleted:**
- `LocalInferenceEngine.kt`
- `MediaPipeInferenceEngine.kt`
- `OnboardingScreen.kt`

**Files modified:**
- `libs.versions.toml` — removed `mediapipeTasks` version + `mediapipe-tasks-genai` library
- `app/build.gradle.kts` — removed `implementation(libs.mediapipe.tasks.genai)`
- `AppPreferences.kt` — removed `KEY_MODEL_URI`, `KEY_MODEL_DISPLAY_NAME`, `modelUri`, `modelDisplayName`, `setModelUri()`, `clearModel()`
- `AppModule.kt` — removed `bindInferenceEngine` Hilt binding
- `AppNavigation.kt` — removed `Onboarding` screen, changed `startDestination` to `DailyLog`
- `SettingsViewModel.kt` — removed `inferenceEngine`, `modelStatus`, `modelDisplayName`, `isBusy`, `selectModel()`, `clearModel()`
- `SettingsScreen.kt` — removed entire "Local Model" section and `ModelStatusChip`
- `LabelessFoodEntryScreen.kt` — removed model-readiness warning banner
- `FoodLookupOrchestrator.kt` — removed LLM dependency; now uses pure heuristics (local saved → exact match → first result)

### 3. Claude Haiku wired to labeless food entry

**`ClaudeInferenceEngine.kt`** (standalone `@Singleton`, no interface):
```kotlin
sealed class ParseResult {
    object NoApiKey : ParseResult()
    object NetworkError : ParseResult()
    object ParseFailed : ParseResult()
    data class ParsedItems(val items: List<ParsedFoodItem>) : ParseResult()
}

suspend fun parseFoods(userInput: String): ParseResult
```
- Reads API key from `prefs.claudeApiKey.first()` at call time
- Calls `https://api.anthropic.com/v1/messages`, model `claude-haiku-4-5`, max 1024 tokens
- Auth header: `x-api-key`, `anthropic-version: 2023-06-01`
- Loads system prompt from `assets/prompts/search_planner_prompt.txt`
- Handles: markdown fence stripping, single-object vs array JSON, error type responses

**`LabelessFoodViewModel.kt`** injects `ClaudeInferenceEngine` directly and maps each `ParseResult` to a user-facing error string:
- `NoApiKey` → "Claude API key not set. Add it in Settings → API Keys."
- `NetworkError` → "Network error reaching Claude. Check your connection and try again."
- `ParseFailed` → "Could not parse foods. Try describing one food at a time."

### 4. Visual redesign — light wellness theme

**`Color.kt`** — full rewrite, dark → light palette:

| Variable | Old (dark) | New (light) |
|----------|------------|-------------|
| `DarkBackground` | `#121212` | `#F0F7F3` soft mint-white |
| `DarkSurface` | `#1E1E1E` | `#FFFFFF` pure white |
| `DarkSurfaceVariant` | `#2A2A2A` | `#E4EFE9` light mint |
| `AccentGreen` | `#4CAF50` neon green | `#1B6B45` deep forest emerald |
| `AccentGreenDim` | `#388E3C` | `#145236` dark emerald |
| `TextPrimary` | `#EEEEEE` | `#1A2B22` near-black |
| `TextSecondary` | `#AAAAAA` | `#5A7266` muted sage |
| `MacroProtein` | `#81C784` pale green | `#2563EB` vibrant blue |
| `MacroCarbs` | `#FFB74D` light amber | `#D97706` warm amber |
| `MacroFat` | `#E57373` light red | `#9333EA` rich purple |
| `ReminderAmber` | `#FFB300` | `#B45309` dark amber |
| `EstimatedColor` | `#90CAF9` light blue | `#0369A1` steel blue |
| `ErrorRed` | `#CF6679` | `#DC2626` clear red |
| `Divider` | `#333333` | `#D4E5DA` soft mint |
| `MacroCalories` | *(didn't exist)* | `#EA580C` warm orange **(new)** |

**`Theme.kt`** — switched from `darkColorScheme` to `lightColorScheme`; `onPrimary = Color.White` (deep emerald needs white text).

**`Type.kt`** — bumped body sizes: `bodyLarge` 15→16sp, `bodyMedium` 13→14sp, `bodySmall` 11→12sp.

**`DailyLogScreen.kt`** — calories shown in `MacroCalories` orange (totals bar, goal bars, food row); progress bars 5→6dp; food row `Surface` gains `shadowElevation = 1.dp` + `shape = RoundedCornerShape(8.dp)` (removed redundant `.clip()`).

**`AddEntryChooserScreen.kt`** — option cards gain `shadowElevation = 2.dp`; removed redundant `.clip()`.

**All screens with `containerColor = AccentGreen` buttons** — `contentColor` changed from `Color.Black` → `Color.White` (deep emerald is dark, black text fails contrast). Affected: `LabelessFoodEntryScreen`, `ManualFoodEntryScreen`, `BarcodeScanScreen`, `NutritionLabelScanScreen`, `WeightTrackerScreen` (also fixed date-picker selected day/year colors).

### 5. ML Kit barcode version fix
- `mlkitBarcode = "18.3.1"` → `"17.3.0"` — version 18.3.1 does not exist in Google Maven
- No code changes needed; the barcode API is unchanged between 17.x versions
- Build now resolves the dependency correctly

---

## Current State

- App builds successfully (last confirmed: `assembleDebug` completed past all dependency resolution)
- All changes are on branch `claude/android-app-project-zZL4w`
- Claude API key (`sk-ant-api03-...`) is in `local.properties` (gitignored) — baked into `BuildConfig.DEFAULT_CLAUDE_API_KEY` at build time; user can override in Settings
- MediaPipe is completely gone — zero references remain
- The app is light-themed, consumer-friendly, local-first

---

## Important Technical Notes

### API key flow
```
local.properties  →  BuildConfig.DEFAULT_CLAUDE_API_KEY
                                    ↓ (fallback if DataStore empty)
DataStore (user sets in Settings) → AppPreferences.claudeApiKey → ClaudeInferenceEngine
```

### Git remote
The local proxy at `127.0.0.1:36477` breaks between sessions. Before pushing, always reset the remote:
```bash
git remote set-url origin https://<YOUR_GITHUB_PAT>@github.com/CarterDonVice/macrotracker.git
git push -u origin claude/android-app-project-zZL4w
```

### Hilt DI structure
- `ClaudeInferenceEngine` — `@Singleton`, injected directly (no interface)
- `OcrLabelParserImpl` — bound via `@Binds` in `AppModule.BindingsModule`
- All ViewModels are `@HiltViewModel`
- `FoodLookupOrchestrator` — `@Singleton`, orchestrates the provider chain without LLM

### Do NOT touch (untouched, stable)
- Barcode scanning logic (`BarcodeScanViewModel`, `BarcodeScanScreen`)
- Nutrition label OCR (`NutritionLabelViewModel`, `OcrLabelParserImpl`)
- Room database schema (any schema change needs a migration)
- Navigation routes (any rename breaks deep links)
- `FoodLogEntryEntity` field names (used in Room queries throughout)

---

## Potential Next Tasks

Things that haven't been done yet that would be natural next steps:

1. **Test Claude integration end-to-end** on a physical device — check that `search_planner_prompt.txt` produces valid JSON for various food inputs
2. **Improve the food lookup provider chain** — `FoodLookupOrchestrator` currently uses pure heuristics; could use Claude's structured output (`sourceOrder`, `searchQuery`) from `PlannerOutput` more intelligently
3. **Saved food creation flow** — after logging a food that has `needsManualSaveReminder = true`, prompt user to save it to their database from `FoodReviewScreen`
4. **Dark mode support** — currently only light theme; could add system-adaptive theming
5. **Widget or quick-add notification** — WorkManager is already a dependency
6. **Export / backup** — Room database export to CSV or JSON
7. **Nutrition completeness** — micronutrients (sodium, fiber, sugar) are partially modeled in `SavedFoodNutrientEntity` but not shown in the UI
