package com.j2me.multibox.ui.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.j2me.multibox.data.db.GameEntity
import com.j2me.multibox.ipc.BoxStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onPickJarFile: () -> Unit,
    onOpenAccountVault: (BoxUiItem) -> Unit,
    onOpenMacroEditor: (GameEntity) -> Unit
) {
    val boxes by viewModel.boxItems.collectAsState()
    val games by viewModel.games.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateBoxDialog by remember { mutableStateOf(false) }
    var renamingBox by remember { mutableStateOf<BoxUiItem?>(null) }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("J2ME Multi-Box (${boxes.count { it.runtime?.status != null && it.runtime.status != BoxStatus.OFFLINE }} đang chạy)") },
                actions = {
                    IconButton(onClick = onPickJarFile) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Nhập file .jar")
                    }
                    if (games.isNotEmpty()) {
                        TextButton(onClick = { onOpenMacroEditor(games.first()) }) {
                            Text("Macro")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateBoxDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tạo Box mới")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (boxes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chưa có Box nào.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("1. Bấm biểu tượng Upload ở góc trên để thêm file .jar\n2. Bấm dấu + để tạo Box tài khoản mới")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(boxes, key = { it.box.boxId }) { item ->
                    BoxCard(
                        item = item,
                        onZoomIn = { viewModel.startAndZoomBox(item) },
                        onStop = { viewModel.stopBox(item.box.boxId) },
                        onRename = { renamingBox = item },
                        onDelete = { viewModel.deleteBox(item.box.boxId) },
                        onOpenAccount = { onOpenAccountVault(item) }
                    )
                }
            }
        }
    }

    if (showCreateBoxDialog) {
        var boxName by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var selectedGameId by remember { mutableStateOf(games.firstOrNull()?.gameId ?: "") }

        AlertDialog(
            onDismissRequest = { showCreateBoxDialog = false },
            title = { Text("Tạo Box Tài Khoản Mới") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (games.isEmpty()) {
                        Text("Vui lòng nhập ít nhất 1 file game .jar trước!", color = MaterialTheme.colorScheme.error)
                    } else {
                        OutlinedTextField(
                            value = boxName,
                            onValueChange = { boxName = it },
                            label = { Text("Tên Box (vd: Acc Chính, Buff 1)") }
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Ghi chú") }
                        )
                        Text("Game: ${games.find { it.gameId == selectedGameId }?.title ?: ""}", fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = games.isNotEmpty(),
                    onClick = {
                        viewModel.createBox(boxName, selectedGameId, note)
                        showCreateBoxDialog = false
                    }
                ) { Text("Tạo Box") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBoxDialog = false }) { Text("Hủy") }
            }
        )
    }

    renamingBox?.let { target ->
        var newName by remember { mutableStateOf(target.box.name) }
        AlertDialog(
            onDismissRequest = { renamingBox = null },
            title = { Text("Đổi tên Box") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Tên mới") })
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameBox(target.box.boxId, newName)
                    renamingBox = null
                }) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(onClick = { renamingBox = null }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun BoxCard(
    item: BoxUiItem,
    onZoomIn: () -> Unit,
    onStop: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val status = item.runtime?.status ?: BoxStatus.OFFLINE
    val statusColor = when (status) {
        BoxStatus.ONLINE_FOREGROUND, BoxStatus.ONLINE_HEADLESS -> Color(0xFF16A34A)
        BoxStatus.RECONNECTING, BoxStatus.STARTING -> Color(0xFFD97706)
        BoxStatus.AWAITING_CAPTCHA -> Color(0xFFDC2626)
        BoxStatus.OFFLINE -> Color.Gray
    }
    val isRunning = status != BoxStatus.OFFLINE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onZoomIn),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Static snapshot or placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                val snapBitmap = remember(item.snapshotPath) {
                    item.snapshotPath?.let { BitmapFactory.decodeFile(it)?.asImageBitmap() }
                }
                if (snapBitmap != null) {
                    Image(
                        bitmap = snapBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(item.gameTitle, color = Color.LightGray, fontSize = 12.sp)
                }

                // Status badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isRunning) "${status.name} (${item.runtime?.memoryMb ?: 0}MB)" else "OFFLINE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(item.box.name, fontWeight = FontWeight.Bold, maxLines = 1)
            if (item.box.note.isNotBlank()) {
                Text(item.box.note, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = if (isRunning) onStop else onZoomIn, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Tắt Box" else "Mở Box",
                        tint = if (isRunning) Color(0xFFDC2626) else Color(0xFF16A34A)
                    )
                }
                IconButton(onClick = onOpenAccount, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = "Tài khoản",
                        tint = if (item.hasAccount) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Đổi tên")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Xóa Box")
                }
            }
        }
    }
}
