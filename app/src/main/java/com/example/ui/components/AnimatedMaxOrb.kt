package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.AssistantState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedMaxOrb(
    state: AssistantState,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotationAnim = remember { Animatable(0f) }
    val pulseAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        rotationAnim.animateTo(
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    LaunchedEffect(state) {
        when (state) {
            AssistantState.IDLE -> {
                pulseAnim.animateTo(
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            AssistantState.PROCESSING -> {
                pulseAnim.animateTo(
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            else -> {
                pulseAnim.snapTo(1f)
            }
        }
    }

    val primaryColor = when (state) {
        AssistantState.IDLE -> CyberCyan
        AssistantState.LISTENING -> NeonGreen
        AssistantState.PROCESSING -> ElectricViolet
        AssistantState.SPEAKING -> NeonPink
        AssistantState.ERROR -> Color.Red
    }

    val secondaryColor = when (state) {
        AssistantState.IDLE -> ElectricViolet
        AssistantState.LISTENING -> CyberCyan
        AssistantState.PROCESSING -> NeonPink
        AssistantState.SPEAKING -> CyberCyan
        AssistantState.ERROR -> Color(0xFFFF5555)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("max_orb_button")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 120.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 3.2f

            // Dynamic expansion based on audio level
            val dynamicScale = if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                1f + (audioLevel * 0.35f)
            } else {
                pulseAnim.value
            }

            val currentRadius = baseRadius * dynamicScale

            // Outer multi-ring orbits
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = currentRadius + 30.dp.toPx(),
                style = Stroke(width = 3.dp.toPx(), pathEffect = dashEffect)
            )

            drawCircle(
                color = secondaryColor.copy(alpha = 0.25f),
                radius = currentRadius + 50.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )

            // Outer glowing aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.5f),
                        secondaryColor.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius + 70.dp.toPx()
                ),
                radius = currentRadius + 70.dp.toPx()
            )

            // Central Glowing Sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor,
                        secondaryColor,
                        primaryColor.copy(alpha = 0.6f)
                    ),
                    center = center,
                    radius = currentRadius
                ),
                radius = currentRadius
            )

            // Orbiting particle dots
            val particleCount = 8
            val angleStep = 360f / particleCount
            for (i in 0 until particleCount) {
                val angleRad = Math.toRadians((rotationAnim.value + i * angleStep).toDouble())
                val orbitRadius = currentRadius + 32.dp.toPx()
                val px = center.x + orbitRadius * cos(angleRad).toFloat()
                val py = center.y + orbitRadius * sin(angleRad).toFloat()

                drawCircle(
                    color = if (i % 2 == 0) primaryColor else secondaryColor,
                    radius = 4.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Center Icon Overlay
        val icon = when (state) {
            AssistantState.IDLE -> Icons.Default.Mic
            AssistantState.LISTENING -> Icons.Default.GraphicEq
            AssistantState.PROCESSING -> Icons.Default.Psychology
            AssistantState.SPEAKING -> Icons.Default.VolumeUp
            AssistantState.ERROR -> Icons.Default.Mic
        }

        Icon(
            imageVector = icon,
            contentDescription = "MAX Presence Orb",
            tint = Color.White,
            modifier = Modifier.size(56.dp)
        )
    }
}
