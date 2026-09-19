package com.ttfx.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.ttfx.core.TickStatus
import com.ttfx.effects.EffectRegistry
import com.ttfx.ui.compose.TTFXAnimationController
import com.ttfx.ui.compose.TTFXCanvas
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onDismiss: () -> Unit
) {
    val splashText = "TTFX ANDROID"
    val columns = 30
    val rows = 10

    val controller = remember {
        val canvas = Canvas(columns, rows)
        val ingested = canvas.ingest(splashText, anchor = "c")
        val config = EffectConfiguration(text = splashText, seed = 77uL)

        TTFXAnimationController(columns, rows) {
            EffectRegistry.create("decrypt", config, canvas, ingested, 77uL)
                ?: error("Decrypt effect not found")
        }
    }

    val tickStatus by controller.tickStatus.collectAsState()
    var progress by remember { mutableFloatStateOf(0f) }
    val bootLogs = remember { mutableStateListOf<String>() }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            time += 0.033f
            delay(33L)
        }
    }

    // Sequence of boot steps and progress
    LaunchedEffect(Unit) {
        bootLogs.add("INIT SYSTEM...")
        delay(400L)
        progress = 0.25f
        bootLogs.add("LOADING 37 EFFECTS ENGINE... [OK]")
        delay(500L)
        progress = 0.60f
        bootLogs.add("COMPILING AGSL CRT SHADERS... [OK]")
        delay(600L)
        progress = 0.90f
        bootLogs.add("READY.")
        progress = 1.0f
        delay(500L)
        onDismiss()
    }

    // Auto-advance if effect completes
    LaunchedEffect(tickStatus) {
        if (tickStatus == TickStatus.Complete) {
            delay(600L)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E17))
            .statusBarsPadding()
            .navigationBarsPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Brand
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                // Cyber Icon Circle
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141829))
                        .border(2.dp, Color(0xFF00FFCC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "TTFX Logo",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "TTFX ANDROID",
                    color = Color(0xFF00FFCC),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "TERMINAL EFFECTS ENGINE • AGSL",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }

            // Central Animated Effect Canvas (Hero)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF252A42), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .ttfxShaderEffect(ShaderEffectMode.CRT_SCANLINES, time = time)
                ) {
                    TTFXCanvas(
                        controller = controller,
                        modifier = Modifier.fillMaxSize(),
                        fps = 30,
                        fontSizeSp = 20f
                    )
                }
            }

            // Bottom Boot Console & Action
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Boot log terminal box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF121524))
                        .border(1.dp, Color(0xFF1F243A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    bootLogs.takeLast(3).forEach { log ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = ">",
                                color = Color(0xFF00FFCC),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = log,
                                color = if (log.contains("[OK]")) Color(0xFF00FF66) else Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF00FFCC),
                    trackColor = Color(0xFF1D2238)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Skip / Enter Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00FFCC),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "START SHOWCASE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Enter",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
