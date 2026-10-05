package com.alram.mail.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alram.mail.data.AppPrefs
import com.alram.mail.service.KeepAliveService
import com.alram.mail.ui.screens.AppDetailScreen
import com.alram.mail.ui.screens.AppsScreen
import com.alram.mail.ui.screens.GmailScreen
import com.alram.mail.ui.screens.HistoryScreen
import com.alram.mail.ui.screens.HomeScreen
import com.alram.mail.ui.screens.QuietScreen
import com.alram.mail.ui.screens.SettingsScreen
import com.alram.mail.ui.screens.SetupScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "홈", Icons.Outlined.Home),
    Tab("apps", "앱", Icons.Outlined.Apps),
    Tab("history", "기록", Icons.Outlined.History),
    Tab("settings", "설정", Icons.Outlined.Settings),
)

@Composable
fun AlramNav(prefs: AppPrefs) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val container = LocalContainer.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // 처음 실행이면 설정 점검 화면으로 보낸다.
    val firstRun = remember { !prefs.setupSeen }
    LaunchedEffect(Unit) {
        if (firstRun) nav.navigate("setup")
    }

    // 상시 알림 서비스는 앱이 화면에 있을 때만 시작할 수 있다.
    LaunchedEffect(prefs.keepAlive) {
        if (prefs.keepAlive) KeepAliveService.start(context) else KeepAliveService.stop(context)
        container.dispatcher.poke()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner)) {
            composable("home") {
                HomeScreen(
                    onOpenSetup = { nav.navigate("setup") },
                    onOpenHistory = { nav.navigate("history") },
                )
            }
            composable("apps") { AppsScreen(onOpenApp = { nav.navigate("app/$it") }) }
            composable("history") { HistoryScreen() }
            composable("settings") {
                SettingsScreen(
                    onOpenGmail = { nav.navigate("gmail") },
                    onOpenQuiet = { nav.navigate("quiet") },
                    onOpenSetup = { nav.navigate("setup") },
                )
            }
            composable("setup") {
                SetupScreen(
                    onOpenGmail = { nav.navigate("gmail") },
                    onDone = { nav.popBackStack() },
                )
            }
            composable("gmail") { GmailScreen(onBack = { nav.popBackStack() }) }
            composable("quiet") { QuietScreen(onBack = { nav.popBackStack() }) }
            composable(
                "app/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType }),
            ) { backStack ->
                AppDetailScreen(
                    packageName = backStack.arguments?.getString("pkg").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
