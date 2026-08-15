package com.agon.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.components.MiniPlayer
import com.agon.app.ui.screens.EqualizerScreen
import com.agon.app.ui.screens.HomeScreen
import com.agon.app.ui.screens.LibraryScreen
import com.agon.app.ui.screens.NowPlayingScreen
import com.agon.app.ui.screens.PermissionScreen
import com.agon.app.ui.screens.PlaylistDetailScreen
import com.agon.app.ui.screens.PlaylistsScreen
import com.agon.app.ui.screens.SearchScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.viewmodel.PlayerViewModel

fun hasAudioPermission(context: Context): Boolean {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PlayerViewModel = viewModel()
            AgonAppTheme(accentName = vm.accentName, bgName = vm.bgName) {
                RedlineApp(vm)
            }
        }
    }
}

@Composable
fun RedlineApp(vm: PlayerViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(targetState = granted, label = "gate") { ok ->
            if (ok) MainScaffold(vm) else PermissionScreen(onGranted = { granted = true })
        }
    }
}

private data class NavDest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    NavDest("home", "Home", Icons.Default.Home),
    NavDest("library", "Library", Icons.Default.LibraryMusic),
    NavDest("playlists", "Playlists", Icons.AutoMirrored.Filled.QueueMusic),
    NavDest("search", "Search", Icons.Default.Search),
    NavDest("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun MainScaffold(vm: PlayerViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val msg = vm.message
        if (msg != null) {
            vm.consumeMessage()
            snackbar.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = vm.currentSong != null && !vm.showNowPlaying,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MiniPlayer(vm, onOpen = { vm.showNowPlaying = true })
                    }
                    BottomNav(nav)
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(180)) },
            ) {
                composable("home") {
                    HomeScreen(
                        vm,
                        onOpenSearch = { navTo(nav, "search") },
                        onOpenPlaylist = { nav.navigate("playlist/$it") },
                    )
                }
                composable("library") { LibraryScreen(vm) }
                composable("playlists") {
                    PlaylistsScreen(vm, onOpenPlaylist = { nav.navigate("playlist/$it") })
                }
                composable("playlist/{id}") { entry ->
                    PlaylistDetailScreen(
                        vm,
                        entry.arguments?.getString("id")?.toLongOrNull() ?: -1L,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("search") {
                    SearchScreen(vm, onOpenPlaylist = { nav.navigate("playlist/$it") })
                }
                composable("settings") {
                    SettingsScreen(vm, onOpenEqualizer = { nav.navigate("equalizer") })
                }
                composable("equalizer") { EqualizerScreen(vm, onBack = { nav.popBackStack() }) }
            }
        }

        AnimatedVisibility(
            visible = vm.showNowPlaying,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            NowPlayingScreen(
                vm,
                onClose = { vm.showNowPlaying = false },
                onOpenEqualizer = {
                    vm.showNowPlaying = false
                    nav.navigate("equalizer")
                },
            )
        }
    }
    BackHandler(enabled = vm.showNowPlaying) { vm.showNowPlaying = false }
}

private fun navTo(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        destinations.forEach { dest ->
            NavigationBarItem(
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                selected = currentRoute == dest.route,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                onClick = { navTo(navController, dest.route) },
            )
        }
    }
}
