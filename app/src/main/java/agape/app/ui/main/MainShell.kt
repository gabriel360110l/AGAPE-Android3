package agape.app.ui.main

import agape.app.R
import agape.app.core.prefs.TourStop
import agape.app.ui.LocalContainer
import agape.app.ui.LocalSettings
import agape.app.ui.bible.BibleScreen
import agape.app.ui.bible.ReaderScreen
import agape.app.ui.church.ArticleScreen
import agape.app.ui.church.CouncilScreen
import agape.app.ui.components.AgapeIcons
import agape.app.ui.home.HomeScreen
import agape.app.ui.legal.LegalScreen
import agape.app.ui.me.AchievementsScreen
import agape.app.ui.me.ActivityScreen
import agape.app.ui.me.HelpScreen
import agape.app.ui.me.MeScreen
import agape.app.ui.me.SavedScreen
import agape.app.ui.me.SettingsScreen
import agape.app.ui.plans.PlanDayScreen
import agape.app.ui.plans.PlanScreen
import agape.app.ui.plans.PlansScreen
import agape.app.ui.plans.QuizScreen
import agape.app.ui.prayer.HourScreen
import agape.app.ui.prayer.PrayerScreen
import agape.app.ui.prayer.PrayerTextScreen
import agape.app.ui.theme.AgapeTheme
import agape.app.ui.tour.TourOverlay
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector, val stop: TourStop)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, R.string.tab_home, Icons.Outlined.Home, TourStop.HOME),
    Tab(BibleRoute, BibleRoute::class, R.string.tab_bible, Icons.AutoMirrored.Outlined.MenuBook, TourStop.BIBLE),
    Tab(PrayerRoute, PrayerRoute::class, R.string.tab_prayer, AgapeIcons.Candle, TourStop.PRAYER),
    Tab(MeRoute, MeRoute::class, R.string.tab_me, Icons.Outlined.Person, TourStop.ME),
)

@Composable
fun MainShell(newIntents: StateFlow<Intent?>, onIntentHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = tabs.firstOrNull { t -> destination?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }
    val settings = LocalSettings.current
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    val pending by newIntents.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { nav.handleDeepLink(it); onIntentHandled() }
    }

    Scaffold(
        containerColor = AgapeTheme.colors.background,
        bottomBar = {
            AnimatedVisibility(currentTab != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                BottomBar(currentTab) { tab -> nav.navigateToTab(tab.route) }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            AgapeNavHost(nav)
            // Guided tour: the first visit to each tab explains it. The Prayer tab's explanation lives in PrayerScreen.
            val stop = currentTab?.stop
            if (stop != null && stop != TourStop.PRAYER && stop !in settings.tourSeen) {
                TourOverlay(stop = stop, seen = settings.tourSeen, onDismiss = { scope.launch { container.settings.markTourSeen(stop) } })
            }
        }
    }
}

private fun NavHostController.navigateToTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit) {
    val c = AgapeTheme.colors
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            tabs.forEach { tab ->
                val selected = tab == current
                val isPrayer = tab.stop == TourStop.PRAYER
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab) },
                    icon = {
                        // Every icon sits in the same 32dp box so labels line up; the Prayer tab, the heart of the
                        // app, fills its box as a medallion.
                        Box(
                            Modifier.size(32.dp).clip(CircleShape)
                                .background(if (isPrayer) (if (selected) c.primary else c.surfaceSunken) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon, null, modifier = Modifier.size(if (isPrayer) 20.dp else 24.dp),
                                tint = when {
                                    isPrayer && selected -> c.onPrimary
                                    isPrayer -> c.primary
                                    else -> LocalContentColor.current
                                },
                            )
                        }
                    },
                    label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.primary, selectedTextColor = c.primary,
                        unselectedIconColor = c.inkFaint, unselectedTextColor = c.inkMuted,
                        indicatorColor = if (isPrayer) c.surface else c.surfaceSunken,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AgapeNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute>(deepLinks = listOf(navDeepLink { uriPattern = "$DEEP_LINK_SCHEME://home" })) {
            HomeScreen(
                openReader = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openPlans = { nav.navigate(PlansRoute) },
                openPlan = { nav.navigate(PlanRoute(it)) },
                openTab = { route -> nav.navigateToTab(route) },
                openSaved = { nav.navigate(SavedRoute) },
                openHour = { nav.navigate(HourRoute(it)) },
            )
        }
        composable<BibleRoute> {
            BibleScreen(
                openChapter = { b, c -> nav.navigate(ReaderRoute(b, c)) },
                openArticle = { nav.navigate(ArticleRoute(it)) },
                openCouncil = { nav.navigate(CouncilRoute(it)) },
                openPlans = { nav.navigate(PlansRoute) },
            )
        }
        composable<PrayerRoute> {
            PrayerScreen(openHour = { nav.navigate(HourRoute(it)) }, openPrayer = { nav.navigate(PrayerTextRoute(it)) })
        }
        composable<MeRoute> {
            MeScreen(
                openSettings = { nav.navigate(SettingsRoute) },
                openSaved = { nav.navigate(SavedRoute) },
                openActivity = { nav.navigate(ActivityRoute) },
                openAchievements = { nav.navigate(AchievementsRoute) },
                openHelp = { nav.navigate(HelpRoute) },
                openLegal = { nav.navigate(LegalRoute(it)) },
            )
        }
        composable<ReaderRoute>(deepLinks = listOf(navDeepLink<ReaderRoute>(basePath = "$DEEP_LINK_SCHEME://bible"))) {
            val r = it.toRoute<ReaderRoute>()
            ReaderScreen(
                book = r.book, chapter = r.chapter, focusVerse = r.verse, onBack = back,
                openChapter = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) { popUpTo<ReaderRoute> { inclusive = true } } },
                openReference = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openQuiz = { key, title -> nav.navigate(QuizRoute(key, title)) },
            )
        }
        composable<ArticleRoute> { ArticleScreen(it.toRoute<ArticleRoute>().id, onBack = back) }
        composable<CouncilRoute> {
            CouncilScreen(it.toRoute<CouncilRoute>().id, onBack = back, openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) })
        }
        composable<PlansRoute> { PlansScreen(onBack = back, openPlan = { nav.navigate(PlanRoute(it)) }) }
        composable<PlanRoute> {
            PlanScreen(it.toRoute<PlanRoute>().planId, onBack = back, openDay = { id, day -> nav.navigate(PlanDayRoute(id, day)) })
        }
        composable<PlanDayRoute> {
            val r = it.toRoute<PlanDayRoute>()
            PlanDayScreen(
                planId = r.planId, day = r.day, onBack = back,
                openPassage = { p -> nav.navigate(ReaderRoute(p.book, p.chapter, p.startVerse ?: 0)) },
                openQuiz = { key, title -> nav.navigate(QuizRoute(key, title, r.planId, r.day)) },
            )
        }
        composable<QuizRoute> {
            val r = it.toRoute<QuizRoute>()
            QuizScreen(quizKey = r.key, title = r.title, planId = r.planId, day = r.day, onDone = back)
        }
        composable<HourRoute>(deepLinks = listOf(navDeepLink<HourRoute>(basePath = "$DEEP_LINK_SCHEME://prayer"))) {
            HourScreen(it.toRoute<HourRoute>().hourId, onBack = back)
        }
        composable<PrayerTextRoute> { PrayerTextScreen(it.toRoute<PrayerTextRoute>().prayerId, onBack = back) }
        composable<SettingsRoute> { SettingsScreen(onBack = back, openLegal = { nav.navigate(LegalRoute(it)) }) }
        composable<SavedRoute> {
            SavedScreen(
                onBack = back,
                openVerse = { b, c, v -> nav.navigate(ReaderRoute(b, c, v)) },
                openPlan = { nav.navigate(PlanRoute(it)) },
                openHour = { nav.navigate(HourRoute(it)) },
                openPrayer = { nav.navigate(PrayerTextRoute(it)) },
            )
        }
        composable<ActivityRoute> { ActivityScreen(onBack = back) }
        composable<AchievementsRoute> { AchievementsScreen(onBack = back) }
        composable<HelpRoute> { HelpScreen(onBack = back, openLegal = { nav.navigate(LegalRoute(it)) }) }
        composable<LegalRoute> { LegalScreen(it.toRoute<LegalRoute>().doc, onBack = back) }
    }
}
