package io.github.dracovin.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dracovin.composeraven.InspectorConfig
import io.github.dracovin.composeraven.features.ravenInspectable
import io.github.dracovin.composeraven.features.recompositionHeatmap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SampleScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun SampleScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        SectionLabel("Heatmap + Counter")
        HeatmapSection()

        SectionLabel("Inspector — per-composable config")
        InspectorSection()

        SectionLabel("Ruler — Gap (tap two separate elements)")
        RulerGapSection()

        SectionLabel("Ruler — Inset (tap outer, then inner)")
        RulerInsetSection()

        SectionLabel("Group highlight")
        GroupSection()
    }
}

// ── Sections ─────────────────────────────────────────────────────────────────

@Composable
private fun HeatmapSection() {
    var counter by remember { mutableIntStateOf(0) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Count: $counter",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .ravenInspectable(tag = "counter-label", label = "Counter Text")
                .recompositionHeatmap(showCount = true),
        )
        Button(
            onClick = { counter++ },
            modifier = Modifier
                .ravenInspectable(
                    tag = "increment-btn",
                    label = "Increment Button",
                    config = InspectorConfig(showTap = false, crosshairColor = Color(0xFF1565C0)),
                )
                .recompositionHeatmap(color = Color(0xFF1565C0), peakAlpha = 0.45f),
        ) {
            Text("+ Increment")
        }
    }
}

@Composable
private fun InspectorSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ColorBox(color = Color(0xFFc18a35), label = "Amber", tag = "amber-box", modifier = Modifier.weight(1f))
        ColorBox(
            color = Color(0xFF4CAF50), label = "Green", tag = "green-box",
            config = InspectorConfig(highlightColor = Color(0xFF4CAF50), crosshairColor = Color(0xFF2E7D32), cornerRadius = 16f),
            modifier = Modifier.weight(1f),
        )
        ColorBox(
            color = Color(0xFF5C6BC0), label = "Indigo", tag = "indigo-box",
            config = InspectorConfig(highlightColor = Color(0xFF5C6BC0), crosshairColor = Color(0xFF283593), highlightFillAlpha = 0.35f, strokeWidth = 3f),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RulerGapSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        DemoHint("Tap any two cards — ruler shows H: gap between them")
        Spacer(Modifier.height(12.dp))
        // Row of 3 cards with 24dp gaps
        Row(modifier = Modifier.fillMaxWidth()) {
            RulerCard(Color(0xFFE91E63), "Card A", "ruler-card-a", Modifier.weight(1f))
            Spacer(Modifier.width(24.dp))
            RulerCard(Color(0xFF9C27B0), "Card B", "ruler-card-b", Modifier.weight(1f))
            Spacer(Modifier.width(24.dp))
            RulerCard(Color(0xFF3F51B5), "Card C", "ruler-card-c", Modifier.weight(1f))
        }
        // 32dp gap below
        Spacer(Modifier.height(32.dp))
        RulerCard(
            color = Color(0xFF009688),
            title = "Card D  (32dp below A/B/C)",
            tag = "ruler-card-d",
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
    }
}

@Composable
private fun RulerInsetSection() {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        DemoHint("Tap outer → tap inner. Ruler shows all 4 insets.")

        // Concentric circles
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Circles", fontSize = 11.sp, color = Color.Gray)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color(0xFF37474F), CircleShape)
                        .ravenInspectable(tag = "outer-circle", label = "Outer ⌀140dp"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFF80CBC4), CircleShape)
                            .ravenInspectable(tag = "inner-circle", label = "Inner ⌀80dp"),
                    )
                }
            }

            // Nested rounded cards — real-world: icon inside a card
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Card + Icon", fontSize = 11.sp, color = Color.Gray)
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color(0xFF1A237E), RoundedCornerShape(20.dp))
                        .ravenInspectable(tag = "card-container", label = "Card 140dp"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF7986CB), CircleShape)
                            .ravenInspectable(tag = "icon-circle", label = "Icon 72dp"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("★", fontSize = 28.sp, color = Color.White)
                    }
                }
            }
        }

        // Asymmetric padding example
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Asymmetric padding", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                    .ravenInspectable(tag = "outer-rect", label = "Outer box"),
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 40.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                        .fillMaxSize()
                        .background(Color(0xFFFF6F00), RoundedCornerShape(8.dp))
                        .ravenInspectable(tag = "inner-rect", label = "Inner box"),
                )
            }
            Text(
                "left=40dp  top/right/bottom=16dp",
                fontSize = 10.sp,
                color = Color(0xFFFF6F00),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupSection() {
    DemoHint("All three share group=\"nav-tabs\". Inspector highlights them together.")
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("Home", "Search", "Profile").forEachIndexed { i, name ->
            Box(
                modifier = Modifier
                    .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .ravenInspectable(
                        tag = "nav-tab-$i",
                        label = name,
                        group = "nav-tabs",
                        config = InspectorConfig(highlightColor = Color(0xFF1565C0), cornerRadius = 8f),
                    ),
                contentAlignment = Alignment.Center,
            ) { Text(name, fontWeight = FontWeight.Medium) }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun ColorBox(
    color: Color,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
    config: InspectorConfig = InspectorConfig(),
) {
    Box(
        modifier = modifier
            .height(80.dp)
            .background(color, RoundedCornerShape(8.dp))
            .ravenInspectable(tag = tag, label = label, config = config),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun RulerCard(color: Color, title: String, tag: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .height(72.dp)
            .ravenInspectable(tag = tag, label = title, config = InspectorConfig(highlightColor = Color(0xFFE91E63), strokeWidth = 2f)),
        colors    = CardDefaults.cardColors(containerColor = color),
        shape     = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
}

@Composable
private fun DemoHint(text: String) {
    Text(text, fontSize = 12.sp, color = Color.Gray)
}
