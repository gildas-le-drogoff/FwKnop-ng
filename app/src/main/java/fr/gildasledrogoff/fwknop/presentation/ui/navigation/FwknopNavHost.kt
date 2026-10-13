package fr.gildasledrogoff.fwknop.presentation.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.gildasledrogoff.fwknop.presentation.ui.screens.configedit.ConfigEditScreen
import fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist.ConfigListNavigation
import fr.gildasledrogoff.fwknop.presentation.ui.screens.configlist.ConfigListScreen
import fr.gildasledrogoff.fwknop.presentation.ui.screens.help.HelpScreen
import fr.gildasledrogoff.fwknop.presentation.ui.screens.nfc.NfcWriteScreen
import fr.gildasledrogoff.fwknop.presentation.ui.screens.qrexport.QrExportScreen
import fr.gildasledrogoff.fwknop.presentation.ui.screens.settings.SettingsScreen

@Composable
fun FwknopNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = ConfigListRoute,
        
        enterTransition = {
            slideIntoContainer(SlideDirection.Start, motion(), initialOffset = { it / 4 }) + fadeIn(motion())
        },
        exitTransition = {
            slideOutOfContainer(SlideDirection.Start, motion(), targetOffset = { it / 10 }) + fadeOut(motion())
        },
        popEnterTransition = {
            slideIntoContainer(SlideDirection.End, motion(), initialOffset = { it / 10 }) + fadeIn(motion())
        },
        popExitTransition = {
            slideOutOfContainer(SlideDirection.End, motion(), targetOffset = { it / 4 }) + fadeOut(motion())
        },
    ) {
        composable<ConfigListRoute> { ConfigListScreen(navigation = navController.listNavigation()) }
        composable<ConfigEditRoute> { ConfigEditScreen(onBack = { navController.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable<HelpRoute> { HelpScreen(onBack = { navController.popBackStack() }) }
        composable<NfcWriteRoute> { NfcWriteScreen(onBack = { navController.popBackStack() }) }
        composable<QrExportRoute> { QrExportScreen(onBack = { navController.popBackStack() }) }
    }
}

private fun <T> motion() = tween<T>(durationMillis = 320, easing = FastOutSlowInEasing)

private fun NavHostController.listNavigation() = ConfigListNavigation(
    onCreate = { navigate(ConfigEditRoute()) },
    onEdit = { navigate(ConfigEditRoute(it)) },
    onWriteNfc = { navigate(NfcWriteRoute(it)) },
    onExportQr = { navigate(QrExportRoute(listOf(it))) },
    onSettings = { navigate(SettingsRoute) },
    onHelp = { navigate(HelpRoute) },
)
