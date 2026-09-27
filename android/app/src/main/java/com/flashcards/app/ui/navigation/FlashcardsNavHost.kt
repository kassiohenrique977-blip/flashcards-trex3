package com.flashcards.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flashcards.app.ui.common.appContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.flashcards.app.R
import com.flashcards.app.ui.card.CardEditorScreen
import com.flashcards.app.ui.deck.DeckDetailScreen
import com.flashcards.app.ui.home.HomeScreen
import com.flashcards.app.ui.importer.ImportScreen
import com.flashcards.app.ui.settings.SettingsScreen
import com.flashcards.app.ui.stats.StatsScreen
import com.flashcards.app.ui.study.StudyScreen
import com.flashcards.app.ui.watch.WatchSyncScreen
import kotlin.reflect.KClass

private data class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    @param:DrawableRes val icon: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(HomeRoute, HomeRoute::class, "Decks", R.drawable.ic_nav_decks),
    TopLevelDestination(StatsRoute, StatsRoute::class, "Estatísticas", R.drawable.ic_nav_stats),
    TopLevelDestination(WatchSyncRoute, WatchSyncRoute::class, "Relógio", R.drawable.ic_nav_watch),
    TopLevelDestination(SettingsRoute, SettingsRoute::class, "Ajustes", R.drawable.ic_nav_settings),
)

private fun NavDestination?.isIn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true

@Composable
fun FlashcardsNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Arquivo aberto de outro app: vai direto para a importação.
    val container = appContainer()
    val incoming by container.incomingFiles.pending.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) {
        if (incoming != null && navController.currentDestination?.hasRoute(ImportRoute::class) != true) {
            navController.navigate(ImportRoute()) { launchSingleTop = true }
        }
    }
    // A barra inferior só aparece nas telas principais; estudo e edição usam a tela toda.
    val showBottomBar = currentDestination == null ||
        topLevelDestinations.any { currentDestination.isIn(it.routeClass) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination.isIn(destination.routeClass),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            // As telas internas têm seu próprio Scaffold; consumir os insets evita espaço dobrado.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenDeck = { deckId -> navController.navigate(DeckDetailRoute(deckId)) },
                    onImport = { navController.navigate(ImportRoute()) },
                )
            }
            composable<DeckDetailRoute> { entry ->
                val route = entry.toRoute<DeckDetailRoute>()
                DeckDetailScreen(
                    deckId = route.deckId,
                    onBack = { navController.popBackStack() },
                    onStudy = { navController.navigate(StudyRoute(route.deckId)) },
                    onAddCard = { navController.navigate(CardEditorRoute(route.deckId)) },
                    onEditCard = { cardId -> navController.navigate(CardEditorRoute(route.deckId, cardId)) },
                    onImport = { navController.navigate(ImportRoute(route.deckId)) },
                )
            }
            composable<CardEditorRoute> { entry ->
                val route = entry.toRoute<CardEditorRoute>()
                CardEditorScreen(
                    deckId = route.deckId,
                    cardId = route.cardId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<ImportRoute> { entry ->
                val route = entry.toRoute<ImportRoute>()
                ImportScreen(
                    deckId = route.deckId,
                    onBack = { navController.popBackStack() },
                    onOpenDeck = { deckId ->
                        if (deckId == route.deckId) {
                            // Veio da tela do próprio deck: basta voltar para ela.
                            navController.popBackStack()
                        } else {
                            navController.navigate(DeckDetailRoute(deckId)) {
                                popUpTo<ImportRoute> { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<StudyRoute> { entry ->
                val route = entry.toRoute<StudyRoute>()
                StudyScreen(deckId = route.deckId, onBack = { navController.popBackStack() })
            }
            composable<StatsRoute> { StatsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
            composable<WatchSyncRoute> { WatchSyncScreen() }
        }
    }
}
