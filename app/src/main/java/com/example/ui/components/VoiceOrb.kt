package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.live.GeminiLiveClient

/**
 * A futuristic, reactive holographic voice orb that pulses, radiates energy rings,
 * and responds in real-time to both microphone input volume and Arushi's speech audio output.
 */
@Composable
fun VoiceOrb(
    state: GeminiLiveClient.LiveState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_animation")

    // Gentle breathing pulse
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Wave ripple phase 1
    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_1"
    )

    // Wave ripple phase 2 (offset)
    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase_2"
    )

    // Aurora rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic color palettes based on active state
    val (coreColors, waveColor) = when (state) {
        GeminiLiveClient.LiveState.IDLE -> Pair(
            listOf(Color(0xFF7928CA), Color(0xFF4FACFE), Color(0xFF00F2FE)),
            Color(0xFF4FACFE)
        )
        GeminiLiveClient.LiveState.CONNECTING -> Pair(
            listOf(Color(0xFFFF8008), Color(0xFFFFC837), Color(0xFF00E5FF)),
            Color(0xFFFFC837)
        )
        GeminiLiveClient.LiveState.LISTENING -> Pair(
            listOf(Color(0xFF00F2FE), Color(0xFF4FACFE), Color(0xFF00E676)),
            Color(0xFF00E5FF)
        )
        GeminiLiveClient.LiveState.SPEAKING -> Pair(
            listOf(Color(0xFFFF007F), Color(0xFF7928CA), Color(0xFFFF5E3A)),
            Color(0xFFFF007F)
        )
        GeminiLiveClient.LiveState.ERROR -> Pair(
            listOf(Color(0xFFFF416C), Color(0xFFFF4B2B), Color(0xFF8A2387)),
            Color(0xFFFF416C)
        )
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = this.size.minDimension / 4f

            // Factor in live audio amplitude
            val dynamicBoost = 1f + (amplitude * 0.7f)
            val effectiveRadius = baseRadius * breathingScale * dynamicBoost

            // Draw outer expanding energy waves (active during listening/speaking)
            if (state == GeminiLiveClient.LiveState.LISTENING || state == GeminiLiveClient.LiveState.SPEAKING) {
                // Wave ring 1
                val r1 = effectiveRadius + (wavePhase1 * baseRadius * 1.2f)
                val alpha1 = (1f - wavePhase1) * (0.3f + amplitude * 0.5f)
                drawCircle(
                    color = waveColor.copy(alpha = alpha1.coerceIn(0f, 1f)),
                    radius = r1,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )

                // Wave ring 2
                val r2 = effectiveRadius + (wavePhase2 * baseRadius * 1.5f)
                val alpha2 = (1f - wavePhase2) * (0.25f + amplitude * 0.4f)
                drawCircle(
                    color = waveColor.copy(alpha = alpha2.coerceIn(0f, 1f)),
                    radius = r2,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Outer soft ambient glow
            val glowRadius = effectiveRadius * 1.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        waveColor.copy(alpha = 0.35f + (amplitude * 0.4f)),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            // Inner core gradient sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colors = coreColors,
                    center = Offset(center.x - effectiveRadius * 0.3f, center.y - effectiveRadius * 0.3f),
                    radius = effectiveRadius * 1.2f
                ),
                radius = effectiveRadius,
                center = center
            )

            // Glassmorphic specular highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - effectiveRadius * 0.4f, center.y - effectiveRadius * 0.45f),
                    radius = effectiveRadius * 0.6f
                ),
                radius = effectiveRadius * 0.55f,
                center = Offset(center.x - effectiveRadius * 0.25f, center.y - effectiveRadius * 0.25f)
            )
        }
    }
}
