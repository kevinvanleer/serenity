package com.kvl.serenity

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import com.kvl.serenity.ui.theme.SerenityTheme

@Composable
fun PlayPauseButton(onClick: () -> Unit = {}, isPlaying: Boolean, enabled: Boolean = true) {
    Button(
        enabled = enabled, onClick = onClick, modifier = Modifier
            .aspectRatio(1f, false)
    ) {
        when (isPlaying) {
            false -> Icon(
                androidx.compose.material.icons.Icons.Rounded.PlayArrow,
                contentDescription = "Start playing sound",
                modifier = Modifier
                    .width(Dp(64f))
                    .height(Dp(64f))
            )

            else -> Icon(
                androidx.compose.material.icons.Icons.Rounded.Pause,
                contentDescription = "Pause sound",
                modifier = Modifier
                    .width(Dp(64f))
                    .height(Dp(64f))
            )
        }
    }
}

class BooleanParameterProvider : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
}

@Preview(showBackground = true)
@Composable
fun PlayPauseButtonPreview(
    @PreviewParameter(BooleanParameterProvider::class) isPlaying: Boolean, enabled: Boolean = true
) {
    SerenityTheme {
        PlayPauseButton(onClick = {}, isPlaying, enabled = enabled)
    }
}
