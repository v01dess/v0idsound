package com.example.v0idsound

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    PLAYER,
    STATISTICS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    currentScreen: AppScreen,
    onScreenChange: (AppScreen) -> Unit,
    showRamUsage: Boolean = false,
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
                    label = {
                        Text("Library")
                    },
                    selected = currentScreen == AppScreen.PLAYER,
                    onClick = {
                        onScreenChange(AppScreen.PLAYER)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text("Statistics")
                    },
                    selected = currentScreen == AppScreen.STATISTICS,
                    onClick = {
                        onScreenChange(AppScreen.STATISTICS)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = {
                        Text("Settings")
                    },
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = {
                        onScreenChange(AppScreen.SETTINGS)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
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
                                AppScreen.STATISTICS -> "Statistics"
                                AppScreen.SETTINGS -> "Settings"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open menu"
                            )
                        }
                    },
                    actions = {
                        if (showRamUsage) {
                            ProcessRamCounter()
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

@Composable
private fun ProcessRamCounter() {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    val activityManager = remember(context) {
        context.getSystemService(
            Context.ACTIVITY_SERVICE
        ) as ActivityManager
    }

    var sampledPssKb by remember {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(activityManager, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            Lifecycle.State.STARTED
        ) {
            while (currentCoroutineContext().isActive) {
                val sample = withContext(Dispatchers.IO) {
                    try {
                        activityManager.getProcessMemoryInfo(
                            intArrayOf(Process.myPid())
                        )
                            .firstOrNull()
                            ?.totalPss
                            ?.takeIf { it > 0 }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        null
                    }
                }

                sampledPssKb = sample

                delay(RAM_SAMPLE_INTERVAL_MS)
            }
        }
    }

    val pssKb = sampledPssKb

    val counterText = if (pssKb != null) {
        val roundedMiB = (pssKb.toLong() + 512L) / 1024L
        "≈$roundedMiB MiB"
    } else {
        "RAM —"
    }

    val accessibilityDescription = if (pssKb != null) {
        val roundedMiB = (pssKb.toLong() + 512L) / 1024L
        "Estimated process memory: $roundedMiB mebibytes PSS"
    } else {
        "Process memory reading unavailable"
    }

    Text(
        text = counterText,
        modifier = Modifier
            .padding(
                start = 8.dp,
                end = 16.dp
            )
            .semantics {
                contentDescription = accessibilityDescription
            },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

private const val RAM_SAMPLE_INTERVAL_MS = 10_000L