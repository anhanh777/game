package com.j2me.multibox.ui.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onOpenMacroEditor: (GameEntity) -> Unit,
    onExitApp: () -> Unit
) {
    val boxes by viewModel.boxItems.collectAsState()
    val games by viewModel.games.collectAsState()
    val errorMsg by viewModel.errorMessage.collectAsState()
    val loadingMsg by viewModel.loadingMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCreateBoxDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var renamingBox by remember { mutableStateOf<BoxUiItem?>(null) }

    val activeCount = boxes.count { it.runtime?.status != null && it.runtime.status != BoxStatus.OFFLINE }

    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("J2ME Multi-Box", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = "$activeCount/8 Box đang treo • ${games.size} game",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onPickJarFile,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Thêm .jar", fontSize = 12.sp)
                    }
                    if (games.isNotEmpty()) {
                        IconButton(onClick = { onOpenMacroEditor(games.first()) }) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Macro", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { showExitConfirmDialog = true }) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Thoát app",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateBoxDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Tạo Box", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (boxes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Chưa có Box tài khoản nào",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Mỗi Box là 1 tài khoản độc lập, có thể treo ngầm cùng lúc và tự đăng nhập.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Button(onClick = onPickJarFile, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bước 1: Chọn file game .jar")
                        }
                        OutlinedButton(
                            onClick = { showCreateBoxDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Bước 2: Tạo Box tài khoản")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(172.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 84.dp),
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
            title = { Text("Tạo Box Tài Khoản Mới", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (games.isEmpty()) {
                        Text(
                            "Vui lòng bấm 'Thêm .jar' để chọn file game Java trước!",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        OutlinedTextField(
                            value = boxName,
                            onValueChange = { boxName = it },
                            label = { Text("Tên Box (vd: Acc Chính, Buff 1)") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Ghi chú (Server, cấp độ...)") },
                            singleLine = true
                        )
                        Text("Chọn game:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        games.forEach { game ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGameId = game.gameId }
                            ) {
                                RadioButton(
                                    selected = selectedGameId == game.gameId,
                                    onClick = { selectedGameId = game.gameId }
                                )
                                Text(game.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
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
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Tên mới") },
                    singleLine = true
                )
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

    loadingMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(msg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Lần đầu chạy game có thể mất 2-3 giây...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Tắt hoàn toàn ứng dụng?", fontWeight = FontWeight.Bold) },
            text = { Text("Toàn bộ các Box đang treo ngầm sẽ dừng hoạt động và thoát app hoàn toàn.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    onClick = {
                        showExitConfirmDialog = false
                        viewModel.stopAllBoxes()
                        onExitApp()
                    }
                ) { Text("Tắt luôn", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) { Text("Hủy") }
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
        BoxStatus.ONLINE_FOREGROUND, BoxStatus.ONLINE_HEADLESS -> Color(0xFF22C55E)
        BoxStatus.RECONNECTING, BoxStatus.STARTING -> Color(0xFFF59E0B)
        BoxStatus.AWAITING_CAPTCHA -> Color(0xFFEF4444)
        BoxStatus.OFFLINE -> Color(0xFF64748B)
    }
    val statusLabel = when (status) {
        BoxStatus.ONLINE_FOREGROUND -> "ĐANG MỞ"
        BoxStatus.ONLINE_HEADLESS -> "TREO NGẦM"
        BoxStatus.STARTING -> "ĐANG BẬT"
        BoxStatus.RECONNECTING -> "KẾT NỐI LẠI"
        BoxStatus.AWAITING_CAPTCHA -> "CẦN CAPTCHA"
        BoxStatus.OFFLINE -> "TẮT"
    }
    val isRunning = status != BoxStatus.OFFLINE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onZoomIn),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isRunning) 1.5.dp else 1.dp,
            color = if (isRunning) statusColor.copy(alpha = 0.7f) else Color(0xFF334155)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                        )
                    ),
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            item.gameTitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }

                // Status badge pill
                Surface(
                    color = statusColor.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isRunning) "$statusLabel • ${item.runtime?.memoryMb ?: 0}MB" else statusLabel,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                item.box.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.box.note.ifBlank {
                    if (item.hasAccount) "Đã lưu tài khoản mã hóa" else "Chưa gắn tài khoản"
                },
                fontSize = 11.sp,
                color = if (item.hasAccount) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = if (isRunning) onStop else onZoomIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF16A34A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(if (isRunning) "Tắt" else "Vào", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row {
                    IconButton(onClick = onOpenAccount, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Tài khoản",
                            tint = if (item.hasAccount) MaterialTheme.colorScheme.primary else Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Đổi tên", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa Box", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
