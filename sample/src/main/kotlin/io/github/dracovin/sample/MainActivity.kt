package io.github.dracovin.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.dracovin.composeraven.features.ravenInspectable
import io.github.dracovin.composeraven.features.recompositionHeatmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { SampleScreen() }
            }
        }
    }
}

@Composable
private fun SampleScreen() {
    var counter by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        // Recomposes on every counter change — heatmap will flash it
        Text(
            text     = "Count: $counter",
            modifier = Modifier
                .ravenInspectable("counter-text")
                .recompositionHeatmap(),
        )
        Button(
            onClick  = { counter++ },
            modifier = Modifier
                .ravenInspectable("increment-button")
                .recompositionHeatmap(),
        ) {
            Text("Increment")
        }
        // Static amber box — inspect it to see #c18a35
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color(0xFFc18a35))
                .ravenInspectable("amber-box"),
        )
    }
}
