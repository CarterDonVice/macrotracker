package com.localmacrotracker.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import com.localmacrotracker.app.ui.screens.*

sealed class Screen(val route: String) {
    object DailyLog : Screen("daily_log")
    object AddEntryChooser : Screen("add_entry_chooser/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) = "add_entry_chooser/$mealSection/$logDate"
    }
    object SavedFoodsSearch : Screen("saved_foods_search/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) =
            "saved_foods_search/$mealSection/$logDate"
    }
    object FoodSearch : Screen("food_search/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) =
            "food_search/$mealSection/$logDate"
    }
    object ManualFoodEntry : Screen("manual_food/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) =
            "manual_food/$mealSection/$logDate"
    }
    object BarcodeScan : Screen("barcode_scan/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) =
            "barcode_scan/$mealSection/$logDate"
    }
    object NutritionLabelScan : Screen("nutrition_label_scan/{meal_section}/{log_date}") {
        fun routeFor(mealSection: String, logDate: String) =
            "nutrition_label_scan/$mealSection/$logDate"
    }
    object FoodReview : Screen("food_review/{entry_id}") {
        fun routeFor(entryId: Long) = "food_review/$entryId"
        const val ARG_ENTRY_ID = "entry_id"
    }
    object RecipeScreen : Screen("recipe/new?meal_section={meal_section}&log_date={log_date}") {
        fun routeForNew(mealSection: String, logDate: String) =
            "recipe/new?meal_section=$mealSection&log_date=$logDate"
    }
    object SavedFoodDetail : Screen("saved_food_detail/{food_id}") {
        fun routeFor(foodId: Long) = "saved_food_detail/$foodId"
    }
    object SavedFoodsBrowse : Screen("saved_foods_browse/{log_date}") {
        fun routeFor(logDate: String) = "saved_foods_browse/$logDate"
    }
    object Settings : Screen("settings")
    object WeightTracker : Screen("weight_tracker")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.DailyLog.route
    ) {
        composable(Screen.DailyLog.route) {
            DailyLogScreen(
                onAddEntry = { mealSection, logDate ->
                    navController.navigate(Screen.AddEntryChooser.routeFor(mealSection, logDate))
                },
                onSearchFoods = { mealSection, logDate ->
                    navController.navigate(Screen.SavedFoodsSearch.routeFor(mealSection, logDate))
                },
                onBrowseFoods = { logDate ->
                    navController.navigate(Screen.SavedFoodsBrowse.routeFor(logDate))
                },
                onNavigateToWeightTracker = {
                    navController.navigate(Screen.WeightTracker.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onEntryTapped = { entryId ->
                    navController.navigate(Screen.FoodReview.routeFor(entryId))
                }
            )
        }

        composable(
            route = Screen.AddEntryChooser.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val mealSection = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val logDate = backStack.arguments?.getString("log_date") ?: LocalDate.now().toString()
            AddEntryChooserScreen(
                mealSection = mealSection,
                logDate = logDate,
                onSavedFood = { ms, date ->
                    navController.navigate(Screen.SavedFoodsSearch.routeFor(ms, date))
                },
                onFoodSearch = { ms, date ->
                    navController.navigate(Screen.FoodSearch.routeFor(ms, date))
                },
                onManualEntry = { ms, date ->
                    navController.navigate(Screen.ManualFoodEntry.routeFor(ms, date))
                },
                onRecipe = { ms, date ->
                    navController.navigate(Screen.RecipeScreen.routeForNew(ms, date))
                },
                onBarcode = { ms, date ->
                    navController.navigate(Screen.BarcodeScan.routeFor(ms, date))
                },
                onNutritionLabel = { ms, date ->
                    navController.navigate(Screen.NutritionLabelScan.routeFor(ms, date))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SavedFoodsSearch.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            SavedFoodsSearchScreen(
                mealSection = ms,
                logDate = date,
                onBack = { navController.popBackStack() },
                onFoodSelected = { navController.popBackStack(Screen.DailyLog.route, false) },
                onFoodDetail = { foodId -> navController.navigate(Screen.SavedFoodDetail.routeFor(foodId)) }
            )
        }

        composable(
            route = Screen.FoodSearch.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            FoodSearchScreen(
                mealSection = ms,
                logDate = date,
                onDone = { navController.popBackStack(Screen.DailyLog.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ManualFoodEntry.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            ManualFoodEntryScreen(
                mealSection = ms,
                logDate = date,
                onDone = { navController.popBackStack(Screen.DailyLog.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.BarcodeScan.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            BarcodeScanScreen(
                mealSection = ms,
                logDate = date,
                onFoodFound = { navController.popBackStack(Screen.DailyLog.route, false) },
                onReviewNeeded = { navController.popBackStack(Screen.DailyLog.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.NutritionLabelScan.route,
            arguments = listOf(
                navArgument("meal_section") { type = NavType.StringType },
                navArgument("log_date") { type = NavType.StringType }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            NutritionLabelScanScreen(
                mealSection = ms,
                logDate = date,
                onDone = { navController.popBackStack(Screen.DailyLog.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FoodReview.route,
            arguments = listOf(navArgument(Screen.FoodReview.ARG_ENTRY_ID) {
                type = NavType.LongType
            })
        ) { backStack ->
            val entryId = backStack.arguments?.getLong(Screen.FoodReview.ARG_ENTRY_ID) ?: -1L
            FoodReviewScreen(
                entryId = entryId,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.RecipeScreen.route,
            arguments = listOf(
                navArgument("meal_section") {
                    type = NavType.StringType
                    defaultValue = "BREAKFAST"
                },
                navArgument("log_date") {
                    type = NavType.StringType
                    defaultValue = LocalDate.now().toString()
                }
            )
        ) { backStack ->
            val ms = backStack.arguments?.getString("meal_section") ?: "BREAKFAST"
            val date = backStack.arguments?.getString("log_date") ?: ""
            RecipeScreen(
                mealSection = ms,
                logDate = date,
                onDone = { navController.popBackStack(Screen.DailyLog.route, false) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SavedFoodDetail.route,
            arguments = listOf(navArgument("food_id") { type = NavType.LongType })
        ) { backStack ->
            val foodId = backStack.arguments?.getLong("food_id") ?: -1L
            SavedFoodDetailScreen(
                foodId = foodId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SavedFoodsBrowse.route,
            arguments = listOf(navArgument("log_date") { type = NavType.StringType })
        ) { backStack ->
            val date = backStack.arguments?.getString("log_date") ?: LocalDate.now().toString()
            SavedFoodsSearchScreen(
                mealSection = "BREAKFAST",
                logDate = date,
                browseOnly = true,
                onBack = { navController.popBackStack() },
                onFoodSelected = { },
                onFoodDetail = { foodId -> navController.navigate(Screen.SavedFoodDetail.routeFor(foodId)) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.WeightTracker.route) {
            WeightTrackerScreen(onBack = { navController.popBackStack() })
        }
    }
}
