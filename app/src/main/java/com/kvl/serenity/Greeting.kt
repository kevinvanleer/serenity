package com.kvl.serenity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kvl.serenity.ui.theme.Serenity60
import com.kvl.serenity.ui.theme.SerenityTheme
import com.kvl.serenity.ui.theme.mooli


@Composable
fun Greeting() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "serenity",
            fontFamily = mooli,
            fontSize = 10.em,
            color = when (MaterialTheme.colorScheme.primary) {
                Serenity60 -> Color.DarkGray
                else -> Color.LightGray
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Roaring Fork",
            fontFamily = mooli,
            fontSize = 6.em
        )
        Text(
            text = "The Great Smoky Mountains",
            fontFamily = mooli,
            fontSize = 4.em
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SerenityTheme {
        Greeting()
    }
}
