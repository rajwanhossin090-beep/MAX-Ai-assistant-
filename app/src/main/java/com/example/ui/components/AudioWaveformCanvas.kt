package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.live.AssistantState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import kotlin.math.sin

@Composable
fun AudioWaveformCanvas(
    state: AssistantState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val phaseAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        phaseAnim.animateTo(
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    val barColors = when (state) {
        AssistantState.LISTENING -> listOf(NeonGreen, CyberCyan, NeonGreen)
        AssistantState.SPEAKING -> listOf(NeonPink, ElectricViolet, CyberCyan)
        AssistantState.PROCESSING -> listOf(ElectricViolet, NeonPink, ElectricViolet)
        else -> listOf(CyberCyan.copy(alpha = 0.5f), ElectricViolet.copy(alpha = 0.5f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.5f)
        val barWidth = spacing * 0.8f
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.85f

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount
            val sineWave = sin(phaseAnim.value + normalizedX * Math.PI * 3f).toFloat()

            val multiplier = when (state) {
                AssistantState.LISTENING, AssistantState.SPEAKING -> {
                    0.2f + (audioLevel * 0.8f) + (sineWave * 0.2f)
                }
                AssistantState.PROCESSING -> {
                    0.3f + (sineWave * 0.3f)
                }
                else -> {
                    0.1f + (sineWave * 0.05f)
                }
            }

            val height = (maxBarHeight * multiplier.coerceIn(0.08f, 1f))
            val x = i * (barWidth + spacing) + spacing / 2f
            val top = centerY - (height / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(barColors),
                topLeft = Offset(x, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
