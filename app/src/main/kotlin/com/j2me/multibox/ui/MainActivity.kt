package com.j2me.multibox.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.j2me.multibox.data.db.GameEntity
import com.j2me.multibox.ui.accounts.AccountVaultScreen
import com.j2me.multibox.ui.dashboard.BoxUiItem
import com.j2me.multibox.ui.dashboard.DashboardScreen
import com.j2me.multibox.ui.dashboard.DashboardViewModel
import com.j2me.multibox.ui.macro.MacroEditorScreen

private val GamingDarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF082F49),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF052E16),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF162032),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444)
)

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = GamingDarkColorScheme) {
                var selectedAccountBox by remember { mutableStateOf<BoxUiItem?>(null) }
                var selectedMacroGame by remember { mutableStateOf<GameEntity?>(null) }

                val jarPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let { viewModel.importJar(it, "") }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) {}

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                when {
                    selectedAccountBox != null -> {
                        val target = selectedAccountBox!!
                        AccountVaultScreen(
                            boxName = target.box.name,
                            initialNote = target.box.note,
                            onSave = { user, pass, note ->
                                viewModel.saveAccount(target.box.boxId, user, pass, note)
                            },
                            onBack = { selectedAccountBox = null }
                        )
                    }
                    selectedMacroGame != null -> {
                        val game = selectedMacroGame!!
                        MacroEditorScreen(
                            gameTitle = game.title,
                            initialJson = game.loginMacroJson,
                            onSave = { steps -> viewModel.saveGameMacro(game.gameId, steps) },
                            onBack = { selectedMacroGame = null }
                        )
                    }
                    else -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onPickJarFile = {
                                jarPickerLauncher.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                            },
                            onOpenAccountVault = { item -> selectedAccountBox = item },
                            onOpenMacroEditor = { game -> selectedMacroGame = game }
                        )
                    }
                }
            }
        }
    }
}
