package com.j2me.multibox.ui.box

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.j2me.multibox.macro.J2meKeyCode

@Composable
fun VirtualKeypadOverlay(
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Soft keys row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            KeypadButton("L-Soft", J2meKeyCode.SOFT_LEFT, onKeyDown, onKeyUp, widthDp = 76)
            KeypadButton("R-Soft", J2meKeyCode.SOFT_RIGHT, onKeyDown, onKeyUp, widthDp = 76)
        }

        // D-Pad + NumPad row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // D-Pad
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KeypadButton("▲", J2meKeyCode.UP, onKeyDown, onKeyUp, sizeDp = 44)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeypadButton("◀", J2meKeyCode.LEFT, onKeyDown, onKeyUp, sizeDp = 44)
                    KeypadButton("OK", J2meKeyCode.FIRE, onKeyDown, onKeyUp, sizeDp = 44, isCircle = true)
                    KeypadButton("▶", J2meKeyCode.RIGHT, onKeyDown, onKeyUp, sizeDp = 44)
                }
                KeypadButton("▼", J2meKeyCode.DOWN, onKeyDown, onKeyUp, sizeDp = 44)
            }

            // NumPad
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeypadButton("1", J2meKeyCode.KEY_1, onKeyDown, onKeyUp)
                    KeypadButton("2", J2meKeyCode.KEY_2, onKeyDown, onKeyUp)
                    KeypadButton("3", J2meKeyCode.KEY_3, onKeyDown, onKeyUp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeypadButton("4", J2meKeyCode.KEY_4, onKeyDown, onKeyUp)
                    KeypadButton("5", J2meKeyCode.KEY_5, onKeyDown, onKeyUp)
                    KeypadButton("6", J2meKeyCode.KEY_6, onKeyDown, onKeyUp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeypadButton("7", J2meKeyCode.KEY_7, onKeyDown, onKeyUp)
                    KeypadButton("8", J2meKeyCode.KEY_8, onKeyDown, onKeyUp)
                    KeypadButton("9", J2meKeyCode.KEY_9, onKeyDown, onKeyUp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeypadButton("*", J2meKeyCode.STAR, onKeyDown, onKeyUp)
                    KeypadButton("0", J2meKeyCode.KEY_0, onKeyDown, onKeyUp)
                    KeypadButton("#", J2meKeyCode.POUND, onKeyDown, onKeyUp)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun KeypadButton(
    label: String,
    keyCode: Int,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    sizeDp: Int = 40,
    widthDp: Int = sizeDp,
    isCircle: Boolean = false
) {
    val shape = if (isCircle) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(width = widthDp.dp, height = sizeDp.dp)
            .clip(shape)
            .background(Color.DarkGray.copy(alpha = 0.85f))
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        onKeyDown(keyCode)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        onKeyUp(keyCode)
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
