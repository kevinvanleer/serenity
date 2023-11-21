package com.kvl.serenity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.kvl.serenity.ui.theme.mooli


@Composable
fun SoundInfo(name: String, location: String) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            fontFamily = mooli,
            fontSize = 6.em,
            textAlign = TextAlign.Center
        )
        Text(
            text = location,
            fontFamily = mooli,
            fontSize = 4.em,
            textAlign = TextAlign.Center
        )
    }
}
