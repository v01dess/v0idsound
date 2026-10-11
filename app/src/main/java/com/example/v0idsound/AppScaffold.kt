package com.example.v0idsound

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

enum class AppScreen {
    PLAYER,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "v0idsound",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Player") },
                    selected = currentScreen == AppScreen.PLAYER,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onScreenChange(AppScreen.PLAYER)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null
                        )
                    }
                )

                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onScreenChange(AppScreen.SETTINGS)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentScreen) {
                                AppScreen.PLAYER -> "v0idsound"
                                AppScreen.SETTINGS -> "Settings"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                content()
            }
        }
    }
}