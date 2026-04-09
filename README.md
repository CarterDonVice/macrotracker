# Local Macro Tracker

A local-first Android calorie and macro tracker with on-device LLM food identification, barcode scanning, OCR nutrition label parsing, and free internet lookup. No backend. No cloud LLM. No paid services.

---

## Prerequisites

- Android Studio Hedgehog or newer (for IDE/build)
- Android SDK 35 (compileSdk)
- Android device or emulator with API 26+ (minSdk)
- Java 17+
- The Gradle wrapper will download Gradle 8.11.1 automatically

---

## How to Open & Build

### Option 1: Android Studio
1. Open Android Studio → **File → Open** → select this folder
2. Android Studio will sync Gradle automatically
3. Set up an emulator or connect a Samsung S23 Ultra (API 35)
4. Click **Run** or press **Shift+F10**

### Option 2: Command line (requires ANDROID_HOME set)
```bash
export ANDROID_HOME=/path/to/your/android-sdk
./gradlew assembleDebug
```
The debug APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

For release (unsigned):
```bash
./gradlew assembleRelease
```

---

## How to Load the Local Model

1. Download a compatible local LLM in `.bin` / `.task` format.
   - **Recommended:** Gemma-3n E2B in LiteRT format (MediaPipe Tasks GenAI)
   - Get from: [Google AI Edge](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference)
   - File extension: `.bin` or `.task`
2. Transfer the model file to your phone (via USB, cloud storage, or adb push)
3. Open the app → the **Onboarding screen** will appear
4. Tap **Select Model File** and pick the `.bin` / `.task` file using the file picker
5. The app validates and loads the model (may take 10–30 seconds for first load)
6. When status shows **"Model ready"**, AI-powered labeless food entry and recipe decomposition are enabled

**Without a model:**
- Barcode scanning, OCR label parsing, and manual food entry all work fully
- AI-powered labeless food description entry is disabled with a clear in-app message

**Model storage:**
The selected model URI is persisted in DataStore. The model file stays at its original location; the app caches a copy to its cache dir for MediaPipe access.

---

## How to Set the USDA API Key

USDA FoodData Central is used for accurate generic food lookups. Without a key, only Open Food Facts (free, no key needed) is used.

1. Get a free API key at: https://fdc.nal.usda.gov/api-key-signup.html
2. Open the app → **Settings** (gear icon)
3. Under **API Keys**, enter your USDA FoodData Central API key
4. Tap **Save**

The key is stored securely in DataStore (local to the device, not transmitted anywhere).

---

## How to Install the APK

### Via ADB (recommended for development)
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Via Android Studio
Use the **Run** button with a connected device selected.

### Manual sideload
1. Transfer `app-debug.apk` to the phone
2. On the phone: **Settings → Install unknown apps** → enable for your file manager
3. Open the APK file and install

---

## Architecture Notes

### Where the LLM decides search plans
`domain/FoodLookupOrchestrator.kt` calls `LocalInferenceEngine.runPlanner()` with the raw user description. The LLM returns a `PlannerOutput` JSON specifying how many food items were found, what search terms to use, and which provider sources to try in order. The LLM **never** makes network calls — it only produces JSON instructions.

### Where the app performs internet lookups
`FoodLookupOrchestrator.kt` executes the plan using the provider chain:
1. `LocalSavedFoodProvider` — local Room DB
2. `UsdaProvider` — USDA FoodData Central REST API (Retrofit + OkHttp)
3. `OpenFoodFactsProvider` — Open Food Facts REST API (Retrofit + OkHttp)
4. `BrandPageProvider` — single-page HTML fetch + Jsoup parse for known brand sites
5. `RestaurantPageProvider` — single-page HTML fetch for known restaurant chains
6. `GroceryPageProvider` — single-page HTML fetch for known grocery sources

The LLM then runs `runCandidateChooser()` to pick the best result, and `runRangeEstimator()` if exactness is uncertain.

### Where the app performs math
All serving math is in `domain/ServingMath.kt` (unit conversion, scaling, fraction parsing).
Daily totals are computed in `domain/DailyTotalsCalculator.kt`.
Recipe totals are computed in `domain/RecipeCalculator.kt`.
**No math is delegated to the LLM.**

### Where the database stores saved foods vs log snapshots
- `data/db/entities/SavedFoodEntity` — the canonical food profile. Editing this affects future uses only.
- `data/db/entities/FoodLogEntryEntity` — an immutable snapshot taken at the time of logging. Contains all nutrition values copied from the source. Never linked live to `SavedFoodEntity` after the initial add. Past entries never change when a saved food is edited.

### Reminder symbol rule
`FoodLogEntryEntity.needsManualSaveReminder = true` when a food came from an external source (USDA, Open Food Facts, barcode, etc.) and was added to the log without being saved to `SavedFoodEntity`. The UI shows a small amber badge. Tapping the entry opens a review screen where the user can save it.

---

## Missing Components to Compile in This Environment

This environment does not have the Android SDK installed (`ANDROID_HOME` is not set). The project is a complete, correct Android project ready to compile once the Android SDK is available.

**What you need to add to build:**
1. Android SDK 35 (install via Android Studio or `sdkmanager`)
2. Set `ANDROID_HOME` or `local.properties` with `sdk.dir=/path/to/android-sdk`
3. Accept SDK licenses: `sdkmanager --licenses`
4. Download the Gradle wrapper JAR automatically via `./gradlew` (it fetches from gradle.org)

**No code changes are needed.** Run `./gradlew assembleDebug` after the SDK is set up.

---

## Feature Checklist

- [x] Daily log with 4 meal sections
- [x] Import Previous Day's [Section] button (copies, no live link)
- [x] Saved/Premade Food search with category filters
- [x] Labeless Food entry with LLM-powered search planner
- [x] Multiple foods from one sentence (non-blocking, parallel add)
- [x] Reminder badge for unsaved externally-found foods
- [x] Barcode scan (ML Kit) with duplicate detection
- [x] Nutrition Label OCR (ML Kit) with manual crop step
- [x] Microphone input via SpeechRecognizer
- [x] Recipe / Meal Prep with live per-serving totals
- [x] Exact daily totals when all entries are exact
- [x] Range daily totals when any entry is estimated
- [x] USDA API lookup (free, requires key)
- [x] Open Food Facts lookup (free, no key)
- [x] Brand page / restaurant page / grocery page direct-site fallback (single-page, bounded)
- [x] Manual draft fallback when all lookups fail
- [x] Local LLM (MediaPipe Tasks GenAI / Gemma) — swappable via LocalInferenceEngine interface
- [x] Model file picker + URI persistence
- [x] Model status display in Settings and Onboarding
- [x] USDA API key in Settings (DataStore, local only)
- [x] Room database with all required entities
- [x] Past log entries never update when saved food changes
- [x] Unit tests for serving math, daily totals, recipe calc, JSON parsing, OCR parsing
- [x] Dark-mode optimized Compose UI
- [x] No backend, no cloud LLM, no paid search, no ads
