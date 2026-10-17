package com.srinidhi.kharch.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.srinidhi.kharch.R
import com.srinidhi.kharch.ui.addedit.AddEditRoute
import com.srinidhi.kharch.ui.categories.CategoryManagerScreen
import com.srinidhi.kharch.ui.dashboard.DashboardRoute
import com.srinidhi.kharch.ui.dashboard.DashboardViewModel
import com.srinidhi.kharch.ui.expenses.ExpensesScreen
import com.srinidhi.kharch.ui.glass.GlassSurface
import com.srinidhi.kharch.ui.settings.AccountsScreen
import com.srinidhi.kharch.ui.settings.SettingsScreen
import com.srinidhi.kharch.ui.theme.GlassShape
import com.srinidhi.kharch.ui.theme.GlassTier

/**
 * The app's navigation graph (plan §7.1).
 *
 * Hosts every destination behind a single [Scaffold] with an edge-to-edge glass nav bar
 * and a morphing add control. Predictive back is honoured by the Navigation Compose
 * back stack and the manifest's `enableOnBackInvokedCallback`.
 */
@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showChrome = currentDestination?.hasRoute<Routes.AddEdit>() != true &&
        currentDestination?.hasRoute<Routes.Auth>() != true

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        bottomBar = {
            if (showChrome) {
                GlassBottomBar(
                    navController = navController,
                    currentDestination = currentDestination,
                )
            }
        },
        floatingActionButton = {
            if (showChrome) {
                MorphingAddFab(onClick = { navController.navigate(Routes.AddEdit()) })
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Dashboard,
            modifier = Modifier.padding(padding),
        ) {
            composable<Routes.Dashboard> {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardRoute(viewModel = viewModel)
            }
            composable<Routes.Expenses> {
                ExpensesScreen()
            }
            composable<Routes.AddEdit> { entry ->
                val route = entry.toRoute<Routes.AddEdit>()
                AddEditRoute(
                    mode = route.mode,
                    expenseId = route.expenseId,
                    dateEpochDay = route.dateEpochDay,
                    quickAdd = route.quickAdd,
                    onDismiss = { navController.popBackStack() },
                    onManageCategories = {
                        navController.navigate(Routes.Categories)
                    },
                )
            }
            composable<Routes.Settings> {
                SettingsScreen()
            }
            composable<Routes.Accounts> {
                AccountsScreen()
            }
            composable<Routes.Categories> {
                CategoryManagerScreen()
            }
            composable<Routes.Auth> {
                Text(text = "Auth")
            }
        }
    }
}

@Composable
private fun GlassBottomBar(
    navController: NavHostController,
    currentDestination: androidx.navigation.NavDestination?,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        tier = GlassTier.Exaggerated,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Routes.Dashboard>() == true,
                onClick = {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cat_home),
                        contentDescription = "Dashboard",
                    )
                },
                label = { Text(text = "Dashboard") },
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Routes.Expenses>() == true,
                onClick = {
                    navController.navigate(Routes.Expenses) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cat_receipt_long),
                        contentDescription = "Expenses",
                    )
                },
                label = { Text(text = "Expenses") },
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Routes.Accounts>() == true,
                onClick = {
                    navController.navigate(Routes.Accounts) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cat_favorite),
                        contentDescription = "Accounts",
                    )
                },
                label = { Text(text = "Accounts") },
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute<Routes.Settings>() == true,
                onClick = {
                    navController.navigate(Routes.Settings) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_cat_more_horiz),
                        contentDescription = "Settings",
                    )
                },
                label = { Text(text = "Settings") },
            )
        }
    }
}

@Composable
private fun MorphingAddFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color.Transparent,
    ) {
        GlassSurface(
            tier = GlassTier.Inset,
            shape = CircleShape,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Add expense",
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

