package com.j2me.multibox.ui.dashboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.j2me.multibox.data.db.AppDatabase
import com.j2me.multibox.data.db.BoxEntity
import com.j2me.multibox.data.db.GameEntity
import com.j2me.multibox.ipc.BoxRuntimeInfo
import com.j2me.multibox.ipc.BoxSlotManager
import com.j2me.multibox.ipc.BoxStatus
import com.j2me.multibox.macro.MacroStep
import com.j2me.multibox.security.AccountVault
import com.j2me.multibox.security.KeystoreCrypto
import com.j2me.multibox.service.BoxRunnerService
import com.j2me.multibox.storage.BoxStorageManager
import com.j2me.multibox.ui.box.BoxActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class BoxUiItem(
    val box: BoxEntity,
    val gameTitle: String,
    val jarPath: String,
    val runtime: BoxRuntimeInfo?,
    val hasAccount: Boolean,
    val snapshotPath: String?
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val storage = BoxStorageManager(application.filesDir)
    private val slotManager = BoxSlotManager(maxSlots = 8)
    private val vault = AccountVault(db.accountDao(), KeystoreCrypto())

    private val runtimeMap = MutableStateFlow<Map<String, BoxRuntimeInfo>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val games: StateFlow<List<GameEntity>> = db.gameDao().observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val boxItems: StateFlow<List<BoxUiItem>> = combine(
        db.boxDao().observeBoxes(),
        db.gameDao().observeGames(),
        db.accountDao().observeAllAccounts(),
        runtimeMap
    ) { boxes, gamesList, accounts, runtimes ->
        val gameById = gamesList.associateBy { it.gameId }
        val accountBoxIds = accounts.map { it.boxId }.toSet()
        boxes.map { box ->
            val game = gameById[box.gameId]
            val snapFile = storage.getBoxPaths(box.boxId).snapshotFile
            BoxUiItem(
                box = box,
                gameTitle = game?.title ?: "Unknown Game",
                jarPath = game?.jarPath ?: "",
                runtime = runtimes[box.boxId],
                hasAccount = box.boxId in accountBoxIds,
                snapshotPath = if (snapFile.exists()) snapFile.absolutePath else null
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BoxRunnerService.ACTION_STATUS_BROADCAST) return
            val boxId = intent.getStringExtra(BoxRunnerService.EXTRA_BOX_ID) ?: return
            val slot = intent.getIntExtra(BoxRunnerService.EXTRA_SLOT, 0)
            val statusName = intent.getStringExtra(BoxRunnerService.EXTRA_STATUS) ?: return
            val memMb = intent.getIntExtra(BoxRunnerService.EXTRA_MEMORY_MB, 0)
            val status = runCatching { BoxStatus.valueOf(statusName) }.getOrDefault(BoxStatus.OFFLINE)

            if (status == BoxStatus.OFFLINE) {
                slotManager.releaseSlot(boxId)
            }

            runtimeMap.value = runtimeMap.value.toMutableMap().apply {
                put(boxId, BoxRuntimeInfo(boxId, slot, status, memMb))
            }
        }
    }

    init {
        val filter = IntentFilter(BoxRunnerService.ACTION_STATUS_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(statusReceiver, filter)
        }
    }

    fun importJar(uri: Uri, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val gameId = "game_${UUID.randomUUID().toString().take(8)}"
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open selected file")
                val savedFile = stream.use { storage.importJarFile(gameId, it) }

                db.gameDao().upsertGame(
                    GameEntity(
                        gameId = gameId,
                        title = title.ifBlank { savedFile.nameWithoutExtension },
                        jarPath = savedFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = "Lỗi nhập file .jar: ${e.message}"
            }
        }
    }

    fun createBox(name: String, gameId: String, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val boxId = "box_${UUID.randomUUID().toString().take(8)}"
            storage.createBoxSandbox(boxId)
            db.boxDao().upsertBox(
                BoxEntity(
                    boxId = boxId,
                    name = name.ifBlank { "Box ${boxItems.value.size + 1}" },
                    gameId = gameId,
                    note = note
                )
            )
        }
    }

    fun renameBox(boxId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (newName.isNotBlank()) {
                db.boxDao().renameBox(boxId, newName.trim())
            }
        }
    }

    fun deleteBox(boxId: String) {
        stopBox(boxId)
        viewModelScope.launch(Dispatchers.IO) {
            db.boxDao().deleteBox(boxId)
            storage.deleteBoxSandbox(boxId)
        }
    }

    fun startAndZoomBox(item: BoxUiItem) {
        val ctx = getApplication<Application>()
        val existingSlot = slotManager.getSlotForBox(item.box.boxId)
        val slot = existingSlot ?: slotManager.allocateSlot(item.box.boxId)
        if (slot == null) {
            _errorMessage.value = "Đã đạt giới hạn tối đa 8 Box chạy cùng lúc."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure .jar -> .dex conversion completes before launching BoxActivity
                val rmsDir = storage.getRmsDir(item.box.boxId)
                val convertedDir = java.io.File(storage.getBoxPaths(item.box.boxId).boxRoot, "converted/${item.box.boxId}")
                com.j2me.multibox.emulator.J2meEngineAdapter(
                    boxId = item.box.boxId,
                    jarPath = item.jarPath,
                    rmsDir = rmsDir,
                    config = storage.loadConfig(item.box.boxId)
                ).prepareBoxEnvironment(ctx, convertedDir)

                if (existingSlot == null || item.runtime?.status == null || item.runtime.status == BoxStatus.OFFLINE) {
                    val serviceIntent = Intent(ctx, BoxRunnerService.serviceClassForSlot(slot)).apply {
                        action = BoxRunnerService.ACTION_START_BOX
                        putExtra(BoxRunnerService.EXTRA_BOX_ID, item.box.boxId)
                        putExtra(BoxRunnerService.EXTRA_BOX_NAME, item.box.name)
                        putExtra(BoxRunnerService.EXTRA_JAR_PATH, item.jarPath)
                        putExtra(BoxRunnerService.EXTRA_SLOT, slot)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ctx.startForegroundService(serviceIntent)
                    } else {
                        ctx.startService(serviceIntent)
                    }
                }

                val activityIntent = Intent(ctx, BoxActivity.activityClassForSlot(slot)).apply {
                    putExtra(BoxRunnerService.EXTRA_BOX_ID, item.box.boxId)
                    putExtra(BoxRunnerService.EXTRA_BOX_NAME, item.box.name)
                    putExtra(BoxRunnerService.EXTRA_JAR_PATH, item.jarPath)
                    putExtra(BoxRunnerService.EXTRA_SLOT, slot)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(activityIntent)
            } catch (e: Exception) {
                slotManager.releaseSlot(item.box.boxId)
                _errorMessage.value = "Lỗi khởi chạy game: ${e.message}"
            }
        }
    }

    fun stopBox(boxId: String) {
        val slot = slotManager.getSlotForBox(boxId) ?: return
        val ctx = getApplication<Application>()
        val stopIntent = Intent(ctx, BoxRunnerService.serviceClassForSlot(slot)).apply {
            action = BoxRunnerService.ACTION_STOP_BOX
        }
        ctx.startService(stopIntent)
        slotManager.releaseSlot(boxId)
    }

    fun saveAccount(boxId: String, username: CharArray, password: CharArray, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            vault.saveCredentials(boxId, username, password, note)
        }
    }

    fun saveGameMacro(gameId: String, steps: List<MacroStep>) {
        viewModelScope.launch(Dispatchers.IO) {
            val game = db.gameDao().getGame(gameId) ?: return@launch
            db.gameDao().upsertGame(game.copy(loginMacroJson = MacroStep.listToJson(steps)))
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { getApplication<Application>().unregisterReceiver(statusReceiver) }
    }
}
