package com.ttfx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttfx.app.shaders.ShaderEffectMode
import com.ttfx.app.shaders.ttfxShaderEffect
import com.ttfx.core.Canvas
import com.ttfx.core.EffectConfiguration
import com.ttfx.effects.EffectRegistry
import com.ttfx.ui.compose.TTFXAnimationController
import com.ttfx.ui.compose.TTFXCanvas
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F111A)
                ) {
                    ShowcaseScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseScreen() {
    var selectedEffect by remember { mutableStateOf("fireworks") }
    var inputText by remember { mutableStateOf("ANDROID TTFX") }
    var shaderMode by remember { mutableStateOf(ShaderEffectMode.CRT_SCANLINES) }
    var fps by remember { mutableFloatStateOf(30f) }
    var seed by remember { mutableStateOf(42uL) }

    var speedMultiplier by remember { mutableFloatStateOf(1f) }
    var fontSizeSp by remember { mutableFloatStateOf(13f) }
    var selectedSpeed by remember { mutableStateOf("1x") }

    val speedOptions = listOf("0.5x" to 0.5f, "1x" to 1.0f, "1.5x" to 1.5f, "2x" to 2.0f, "3x" to 3.0f)
    val effectiveFps = (fps * speedMultiplier).toInt().coerceAtLeast(1)

    val columns = 40
    val rows = 15

    // Build animation controller for selected effect
    val controller = remember(selectedEffect, inputText, seed) {
        val canvas = Canvas(columns, rows)
        val ingested = canvas.ingest(inputText, anchor = "c")
        val config = EffectConfiguration(text = inputText, seed = seed)

        TTFXAnimationController(columns, rows) {
            EffectRegistry.create(selectedEffect, config, canvas, ingested, seed)
                ?: error("Effect $selectedEffect not registered")
        }
    }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.033f * speedMultiplier
            delay(33L)
        }
    }

    val frameCount by controller.frameCount.collectAsState()
    val tickStatus by controller.tickStatus.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TTFX ANDROID",
                    color = Color(0xFF00FFCC),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "37 Terminal Text Effects • Compose & AGSL",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "Frame: $frameCount [${tickStatus.name}]",
                color = if (tickStatus.name == "Complete") Color(0xFF00FF66) else Color(0xFFFFCC00),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Display Box with TTFXCanvas + AGSL Shader Modifier
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .ttfxShaderEffect(shaderMode, time = time)
            ) {
                TTFXCanvas(
                    controller = controller,
                    modifier = Modifier.fillMaxSize(),
                    fps = effectiveFps,
                    fontSizeSp = fontSizeSp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Font Size & Speed Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Speed options
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = "SPEED: $selectedSpeed",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    speedOptions.forEach { (label, mult) ->
                        val isSpeedSelected = label == selectedSpeed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSpeedSelected) Color(0xFF00E5FF) else Color(0xFF1E2235))
                                .clickable {
                                    selectedSpeed = label
                                    speedMultiplier = mult
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSpeedSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSpeedSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // Font Size Slider
            Column(modifier = Modifier.weight(0.9f)) {
                Text(
                    text = "FONT: ${fontSizeSp.toInt()} SP",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 8f..24f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00FFCC),
                        activeTrackColor = Color(0xFF00FFCC),
                        inactiveTrackColor = Color(0xFF2A2E43)
                    ),
                    modifier = Modifier.height(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Effect Selector (Scrollable horizontally)
        Text(
            text = "SELECT EFFECT (${EffectRegistry.allEffects.size} AVAILABLE):",
            color = Color.LightGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(EffectRegistry.allEffects) { effectName ->
                val isSelected = effectName == selectedEffect
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF00FFCC) else Color(0xFF1E2235))
                        .clickable { selectedEffect = effectName }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = effectName,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // AGSL Shader Selector
        Text(
            text = "AGSL SHADER POST-PROCESSING:",
            color = Color.LightGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShaderEffectMode.values().forEach { mode ->
                val isSelected = mode == shaderMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFFFF007F) else Color(0xFF1E2235))
                        .clickable { shaderMode = mode }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = mode.name,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Text & Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                label = { Text("Display Text", fontSize = 11.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = Color(0xFF00FFCC),
                    unfocusedBorderColor = Color.Gray
                )
            )

            Button(
                onClick = { controller.reset() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Replay", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
