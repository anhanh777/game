package com.j2me.multibox.ui.macro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.j2me.multibox.macro.J2meKeyCode
import com.j2me.multibox.macro.MacroStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditorScreen(
    gameTitle: String,
    initialJson: String,
    onSave: (List<MacroStep>) -> Unit,
    onBack: () -> Unit
) {
    val steps = remember {
        mutableStateListOf<MacroStep>().apply {
            addAll(MacroStep.listFromJson(initialJson))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Macro Đăng Nhập: $gameTitle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onSave(steps.toList())
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu Macro")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = { steps.add(MacroStep.Wait(1500)) }, modifier = Modifier.weight(1f)) {
                    Text("+ Chờ 1.5s")
                }
                OutlinedButton(onClick = { steps.add(MacroStep.KeyPress(J2meKeyCode.FIRE)) }, modifier = Modifier.weight(1f)) {
                    Text("+ Bấm OK")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = { steps.add(MacroStep.TypeText("\${USERNAME}")) }, modifier = Modifier.weight(1f)) {
                    Text("+ Nhập User")
                }
                OutlinedButton(onClick = { steps.add(MacroStep.TypeText("\${PASSWORD}")) }, modifier = Modifier.weight(1f)) {
                    Text("+ Nhập Pass")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(onClick = { steps.add(MacroStep.KeyPress(J2meKeyCode.DOWN)) }, modifier = Modifier.weight(1f)) {
                    Text("+ Xuống")
                }
                OutlinedButton(onClick = { steps.add(MacroStep.AwaitManualInput("Nhập Captcha")) }, modifier = Modifier.weight(1f)) {
                    Text("+ Dừng Captcha")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(steps) { index, step ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val label = when (step) {
                                is MacroStep.Wait -> "${index + 1}. Chờ ${step.durationMs}ms"
                                is MacroStep.KeyPress -> "${index + 1}. Nhấn phím (code=${step.keyCode})"
                                is MacroStep.TypeText -> "${index + 1}. Gõ chuỗi: ${step.template}"
                                is MacroStep.AwaitManualInput -> "${index + 1}. Tạm dừng chờ nhập tay (${step.prompt})"
                            }
                            Text(label)
                            IconButton(onClick = { steps.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa bước")
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSave(steps.toList())
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lưu Kịch Bản Macro")
            }
        }
    }
}
