package com.github.heroslender.lgtvcontroller.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.heroslender.lgtvcontroller.ui.controller.ControllerScreen
import com.github.heroslender.lgtvcontroller.ui.devicelist.DeviceListScreen
import com.github.heroslender.lgtvcontroller.ui.editor.TvEditScreen
import com.github.heroslender.lgtvcontroller.ui.home.HomeScreen

@Composable
fun ControllerNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = NavDest.Home.route,
        modifier = modifier,
        // Forward: the new screen slides in; back (also the predictive back gesture): the
        // current one slides away shrinking while the previous one comes back
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() + scaleOut(targetScale = 0.9f)
        },
    ) {
        composable(NavDest.DeviceList.route) {
            DeviceListScreen(
                navigateToController = {
                    // Back to the remote, which is below the list in the back stack
                    if (!navController.popBackStack(NavDest.Home.route, false)) {
                        navController.navigate(NavDest.Home.route) {
                            popUpTo(NavDest.DeviceList.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(NavDest.Home.route) {
            HomeScreen(
                navigateToDeviceList = {
                    navController.navigate(NavDest.DeviceList.route) {
                        launchSingleTop = true
                    }
                },
                navigateToSetup = {
                    // First use, nothing to show: replace the remote with the device search
                    navController.navigate(NavDest.DeviceList.route) {
                        popUpTo(NavDest.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                navigateToController = {
                    navController.navigate(NavDest.Controller.route)
                },
                navigateToEditDevice = {
                    navController.navigate("${NavDest.EditDevice.route}/$it")
                },
            )
        }

        composable(NavDest.Controller.route) {
            ControllerScreen(
                navigateUp = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = NavDest.EditDevice.routeWithArgs,
            arguments = listOf(navArgument(NavDest.EditDevice.tvIdArg) {
                type = NavType.StringType
            }),
        ) {
            TvEditScreen(
                navigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}