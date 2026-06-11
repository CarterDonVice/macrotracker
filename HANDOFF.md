# MacroTracker Android — Engineering Handoff

**Repo:** `CarterDonVice/macrotracker` | **Branch:** `claude/android-app-project-zZL4w`
**Stack:** Kotlin · Jetpack Compose · Material3 · Hilt · Room · CameraX · ML Kit · OkHttp · Retrofit

---

## App overview

Local-first Android macro tracker. No backend, no cloud sync, no AI. Food is logged via six entry methods:

| Entry method | How it works |
|---|---|
| Food search | Debounced search across local library + USDA + OpenFoodFacts → quantity sheet → log |
| Barcode scan | CameraX + ML Kit → local DB first, then OpenFoodFacts barcode lookup → log |
| Nutrition label OCR | CameraX + ML Kit text recognition → regex parser → review form → log |
| Manual entry | User types name + macros |
| Saved foods search | Search user's personal food DB |
| Recipe builder | Multi-ingredient recipe → log one serving |

Start destination: `DailyLogScreen`. Navigation is in `AppNavigation.kt`.

---

## APIs

**USDA FoodData Central** (search):
- `GET https://api.nal.usda.gov/fdc/v1/foods/search?query=&api_key=` (note the `/fdc` prefix)
- Requires a free API key, entered by the user in Settings → API Keys (DataStore `usda_api_key`)
- No key → `UsdaProvider.search()` logs a warning and returns empty; search still works via OFF

**OpenFoodFacts** (search + barcode, no key needed):
- Barcode: `GET /api/v2/product/{barcode}` — `status == 1` means found
- Search: `GET /cgi/search.pl?search_terms=&json=1`
- A `User-Agent` header is set on the OFF OkHttp client (required by OFF API guidelines)
- When a product only has per-100g nutriments but declares a gram serving size,
  `OpenFoodFactsProvider` scales macros to the serving (`parseServingGrams`)

---

## Project structure

```
app/src/main/kotlin/com/localmacrotracker/app/
├── data/
│   ├── db/                        Room DB — AppDatabase (v2), 5 DAOs, 8 entities
│   ├── model/                     FoodCandidate, MealSection, SourceType, etc.
│   ├── network/
│   │   ├── api/                   UsdaApi.kt, OpenFoodFactsApi.kt (Retrofit + kotlinx.serialization)
│   │   └── providers/             UsdaProvider, OpenFoodFactsProvider, LocalSavedFoodProvider
│   └── prefs/AppPreferences.kt    DataStore: USDA key, onboarding flag, daily goals
├── di/AppModule.kt                Hilt: Room + DAOs, OCR binding (network in NetworkModule)
├── domain/                        DailyTotalsCalculator, OcrLabelParserImpl, ServingMath, etc.
└── ui/
    ├── navigation/AppNavigation.kt
    ├── screens/                   12 screens
    ├── theme/                     Color.kt (warm light palette), Theme.kt, Type.kt
    └── viewmodel/                 One @HiltViewModel per screen
```

### Theme (light, warm)
```kotlin
DarkBackground    = #F4F1EC   // warm linen cream (page)
DarkSurface       = #FEFCF9   // near-white warm (cards)
AccentGreen       = #2C4A3E   // deep forest teal (primary)
TextPrimary       = #1C2018   // deep warm charcoal
MacroProtein      = #1D4ED8   // deep blue
MacroCarbs        = #B45309   // warm amber
MacroFat          = #6D28D9   // deep purple
MacroCalories     = #C2430E   // burnt orange
```

### Navigation routes
```
daily_log                                          (start)
add_entry_chooser/{meal_section}/{log_date}
food_search/{meal_section}/{log_date}
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

## Build

- `minSdk 26`, `targetSdk 35`, JDK 17, AGP 8.7.3, Kotlin 2.1.0, Gradle wrapper 8.11.1
- `local.properties` needs only the Android SDK path (no API keys are compiled in)
- Camera permission is requested at runtime (Accompanist); INTERNET is the only other permission

## Do NOT touch
- Room database schema — any field rename or addition needs a proper `Migration` in `AppDatabase.kt`
- `FoodLogEntryEntity` field names — referenced in DAO queries throughout
- Navigation route strings — deep links depend on exact route patterns
- `AppPreferences` DataStore key strings — changing them loses user's saved settings
