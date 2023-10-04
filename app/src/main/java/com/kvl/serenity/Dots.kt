package com.kvl.serenity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Dot(selected: Boolean) {
    Icon(
        androidx.compose.material.icons.Icons.Rounded.Circle,
        contentDescription = "Start playing sound",
        modifier = Modifier
            .width(Dp(8f))
            .height(Dp(8f)),
        tint = when(selected) {
            true -> MaterialTheme.colorScheme.onSurface
            else -> Color(0x77777777)
        }
    )
}

@Composable
fun Dots(dotCount: Int, selectedDot: Int) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    IntRange(0, dotCount - 1).forEach { idx ->
        Dot(selected = selectedDot == idx)
    }
}

@Preview
@Composable
fun dotsPreviewThree() {
    Dots(3, 0)
}
