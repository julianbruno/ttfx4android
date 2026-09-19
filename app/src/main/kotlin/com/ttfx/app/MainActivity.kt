package com.ttfx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = Color(0xFF0C0E17)
                ) {
                    ShowcaseScreen()
                }
            }
        }
    }
}

@Composable
fun ShowcaseScreen() {
    var selectedEffect by rememberSaveable { mutableStateOf("fireworks") }
    var inputText by rememberSaveable { mutableStateOf("ANDROID TTFX") }
    var shaderMode by rememberSaveable { mutableStateOf(ShaderEffectMode.CRT_SCANLINES) }
    var seed by rememberSaveable { mutableStateOf(42uL) }

    var isSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var speedMultiplier by rememberSaveable { mutableFloatStateOf(1f) }
    var fontSizeSp by rememberSaveable { mutableFloatStateOf(14f) }
    var selectedSpeed by rememberSaveable { mutableStateOf("1x") }

    val speedOptions = remember {
        listOf("0.5x" to 0.5f, "1x" to 1.0f, "1.5x" to 1.5f, "2x" to 2.0f, "3x" to 3.0f)
    }
    val effectiveFps = (30f * speedMultiplier).toInt().coerceAtLeast(1)

    val columns = 40
    val rows = 15

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
    LaunchedEffect(speedMultiplier) {
        while (true) {
            time += 0.033f * speedMultiplier
            delay(33L)
        }
    }

    val frameCount by controller.frameCount.collectAsState()
    val tickStatus by controller.tickStatus.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        val totalHeight = maxHeight
        Column(modifier = Modifier.fillMaxSize()) {
            // App Header & Top Bar with centered title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Frame status badge (Left / Start)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF161928))
                        .border(1.dp, Color(0xFF252A42), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "F: %4d [%s]".format(frameCount, tickStatus.name.uppercase()),
                        color = if (tickStatus.name == "Complete") Color(0xFF00FF66) else Color(0xFFFFCC00),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                // Centered App Title
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TTFX ANDROID",
                        color = Color(0xFF00FFCC),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "37 Terminal Effects • AGSL",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }

                // Settings Visibility Toggle Button (Right / End)
                IconButton(
                    onClick = { isSettingsVisible = !isSettingsVisible },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSettingsVisible) Color(0xFF00FFCC).copy(alpha = 0.2f) else Color(0xFF161928))
                        .border(1.dp, if (isSettingsVisible) Color(0xFF00FFCC) else Color(0xFF252A42), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSettingsVisible) Icons.Default.Close else Icons.Default.Tune,
                        contentDescription = if (isSettingsVisible) "Hide Controls" else "Show Controls",
                        tint = if (isSettingsVisible) Color(0xFF00FFCC) else Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Terminal Display Box (occupies flexible space, guaranteed minimum height)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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

            // Quick Status Bottom Bar when settings are collapsed
            AnimatedVisibility(
                visible = !isSettingsVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Active Effect Pill (Clicking opens controls)
                    Surface(
                        color = Color(0xFF141726),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF252A42)),
                        modifier = Modifier.clickable { isSettingsVisible = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("✨", fontSize = 11.sp)
                            Text(
                                text = selectedEffect.uppercase(),
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• ${fontSizeSp.toInt()}SP",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Replay
                        FilledTonalButton(
                            onClick = { controller.reset() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF161928),
                                contentColor = Color(0xFF00FFCC)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Replay",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REPLAY",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Open Controls Button
                        Button(
                            onClick = { isSettingsVisible = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00FFCC),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Controls",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SETTINGS",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Collapsible Controls Panel (Scrollable, bounded height so canvas is always visible)
            AnimatedVisibility(
                visible = isSettingsVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (totalHeight * 0.62f).coerceAtLeast(200.dp))
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141726))
                        .border(1.dp, Color(0xFF252A42), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    // Header with title and explicit Close/Hide button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = Color(0xFF00FFCC),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "CONTROLS & SETTINGS",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        TextButton(
                            onClick = { isSettingsVisible = false },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hide",
                                tint = Color.LightGray,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "HIDE",
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed & Font Size Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1A1E30))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Top Line: Speed selector & Font Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "SPEED",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    speedOptions.forEach { (label, mult) ->
                                        val isSpeedSelected = label == selectedSpeed
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSpeedSelected) Color(0xFF00E5FF) else Color(0xFF252A42))
                                                .clickable {
                                                    selectedSpeed = label
                                                    speedMultiplier = mult
                                                }
                                                .padding(horizontal = 7.dp, vertical = 3.dp),
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

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF00FFCC).copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "FONT: ${fontSizeSp.toInt()} SP",
                                        color = Color(0xFF00FFCC),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Font slider (8sp - 120sp)
                            Slider(
                                value = fontSizeSp,
                                onValueChange = { fontSizeSp = it },
                                valueRange = 8f..120f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00FFCC),
                                    activeTrackColor = Color(0xFF00FFCC),
                                    inactiveTrackColor = Color(0xFF2A2E43)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Preset chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PRESETS:",
                                    color = Color.Gray,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                listOf(10f, 14f, 20f, 32f, 48f, 72f, 120f).forEach { preset ->
                                    val isPresetActive = (fontSizeSp.toInt() == preset.toInt())
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isPresetActive) Color(0xFF00FFCC) else Color(0xFF252A42))
                                            .clickable { fontSizeSp = preset }
                                            .padding(vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${preset.toInt()}",
                                            color = if (isPresetActive) Color.Black else Color.LightGray,
                                            fontSize = 9.sp,
                                            fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Normal,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Effect Selector Chips
                    Text(
                        text = "SELECT EFFECT (${EffectRegistry.allEffects.size}):",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(EffectRegistry.allEffects) { effectName ->
                            val isSelected = effectName == selectedEffect
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFF00FFCC) else Color(0xFF1A1E30))
                                    .clickable { selectedEffect = effectName }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = effectName,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // AGSL Shader Selector
                    Text(
                        text = "AGSL POST-PROCESSING:",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        ShaderEffectMode.entries.forEach { mode ->
                            val isSelected = mode == shaderMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFFFF007F) else Color(0xFF1A1E30))
                                    .clickable { shaderMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mode.name,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Input Text & Replay Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Display Text", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = Color(0xFF00FFCC),
                                unfocusedBorderColor = Color(0xFF2A2E43)
                            )
                        )

                        Button(
                            onClick = { controller.reset() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Replay", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
