package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.GeminiLiveClient
import com.example.ui.components.DiagnosticLogSheet
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VoiceOrb

/**
 * Main voice-first screen for Arushi Crotrix AI Voice Assistant.
 * Provides real-time voice-to-voice interaction, dynamic glowing orb,
 * live transcripts, native Android action triggers, and diagnostics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArushiVoiceScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogsSheet by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Space cosmic gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090814),
            Color(0xFF110E26),
            Color(0xFF0D0A1C)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF7928CA))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "A",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Arushi",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Crotrix Voice Assistant",
                            fontSize = 11.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                }

                // Action icons: Speaker test, logs, settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.testSpeaker() },
                        modifier = Modifier.testTag("speaker_test_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Test Speaker",
                            tint = if (uiState.isSpeakerTesting) Color(0xFF00E5FF) else Color.LightGray
                        )
                    }
                    IconButton(
                        onClick = { showLogsSheet = true },
                        modifier = Modifier.testTag("logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Diagnostics Logs",
                            tint = Color.LightGray
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // State Pill
            StatusIndicatorPill(state = uiState.state)

            Spacer(modifier = Modifier.height(6.dp))

            // Multi-Language capability pill carousel
            LanguagePills()

            Spacer(modifier = Modifier.height(16.dp))

            // CENTER: Interactive Voice Orb
            VoiceOrb(
                state = uiState.state,
                amplitude = uiState.combinedAmplitude,
                onClick = { viewModel.toggleSession() },
                modifier = Modifier.testTag("voice_orb")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            Text(
                text = uiState.statusMessage,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (uiState.state) {
                    GeminiLiveClient.LiveState.ERROR -> Color(0xFFFF5252)
                    GeminiLiveClient.LiveState.SPEAKING -> Color(0xFFFF4081)
                    GeminiLiveClient.LiveState.LISTENING -> Color(0xFF00E5FF)
                    GeminiLiveClient.LiveState.CONNECTING -> Color(0xFFFFD740)
                    GeminiLiveClient.LiveState.IDLE -> Color.LightGray
                },
                textAlign = TextAlign.Center
            )

            // Active Device Action Badge
            AnimatedVisibility(
                visible = uiState.activeActionBadge != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                uiState.activeActionBadge?.let { badge ->
                    Surface(
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF1E1742),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ $badge",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LIVE TRANSCRIPT CARD
            ConversationTranscriptCard(
                userTranscript = uiState.transcriptUser,
                arushiTranscript = uiState.transcriptArushi,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick suggestion chips
            QuickSuggestions(
                onSelectSuggestion = { suggestion ->
                    // If session idle, start it
                    if (uiState.state == GeminiLiveClient.LiveState.IDLE) {
                        viewModel.toggleSession()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM PRIMARY MIC / CALL BUTTON
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainActionButton(
                    state = uiState.state,
                    onClick = { viewModel.toggleSession() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Diagnostic Bottom Sheet
        if (showLogsSheet) {
            DiagnosticLogSheet(
                logs = uiState.logs,
                onDismiss = { showLogsSheet = false },
                onClearLogs = { viewModel.clearLogs() },
                onTestSpeaker = { viewModel.testSpeaker() },
                isSpeakerTesting = uiState.isSpeakerTesting
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            SettingsDialog(
                currentApiKey = uiState.apiKey,
                currentModel = uiState.model,
                onSave = { key, model ->
                    viewModel.updateApiKey(key)
                    viewModel.updateModel(model)
                },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
private fun StatusIndicatorPill(state: GeminiLiveClient.LiveState) {
    val (label, dotColor) = when (state) {
        GeminiLiveClient.LiveState.IDLE -> Pair("IDLE", Color(0xFF7E7A9B))
        GeminiLiveClient.LiveState.CONNECTING -> Pair("CONNECTING", Color(0xFFFFD740))
        GeminiLiveClient.LiveState.LISTENING -> Pair("LISTENING", Color(0xFF00E676))
        GeminiLiveClient.LiveState.SPEAKING -> Pair("ARUSHI SPEAKING", Color(0xFFFF007F))
        GeminiLiveClient.LiveState.ERROR -> Pair("DISCONNECTED", Color(0xFFFF5252))
    }

    Surface(
        color = Color(0xFF1B1833),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = dotColor,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun LanguagePills() {
    val languages = listOf("English", "Hindi", "Hinglish", "Marathi", "Tamil", "Telugu", "+Auto")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        languages.forEach { lang ->
            Text(
                text = lang,
                fontSize = 10.sp,
                color = Color(0xFF8E8BAE),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            if (lang != languages.last()) {
                Text(text = "•", fontSize = 10.sp, color = Color(0xFF494668))
            }
        }
    }
}

@Composable
private fun ConversationTranscriptCard(
    userTranscript: String,
    arushiTranscript: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151228)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28234D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Live Conversation",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF00E5FF)
                )
                Text(
                    text = "Voice-to-Voice",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (userTranscript.isBlank() && arushiTranscript.isBlank()) {
                Text(
                    text = "Arushi is ready. Tap the microphone and talk naturally in Hindi, English, Hinglish, etc.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            } else {
                if (userTranscript.isNotBlank()) {
                    Text(
                        text = "You: $userTranscript",
                        fontSize = 13.sp,
                        color = Color(0xFFE0E0E0),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (arushiTranscript.isNotBlank()) {
                    Text(
                        text = "Arushi: $arushiTranscript",
                        fontSize = 13.sp,
                        color = Color(0xFFFF80AB),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSuggestions(
    onSelectSuggestion: (String) -> Unit
) {
    val suggestions = listOf(
        "Open WhatsApp",
        "Call Mom",
        "Open YouTube",
        "WhatsApp kholo",
        "Kya haal hai?",
        "Tell me a joke"
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        suggestions.forEach { suggestion ->
            Surface(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clickable { onSelectSuggestion(suggestion) },
                color = Color(0xFF1E1A38),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF332D5C))
            ) {
                Text(
                    text = suggestion,
                    fontSize = 11.sp,
                    color = Color(0xFFB39DDB),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun MainActionButton(
    state: GeminiLiveClient.LiveState,
    onClick: () -> Unit
) {
    val isLive = state == GeminiLiveClient.LiveState.LISTENING || state == GeminiLiveClient.LiveState.SPEAKING
    val isConnecting = state == GeminiLiveClient.LiveState.CONNECTING

    val buttonColor = when {
        state == GeminiLiveClient.LiveState.SPEAKING -> Color(0xFFFF007F)
        state == GeminiLiveClient.LiveState.LISTENING -> Color(0xFF00E5FF)
        isConnecting -> Color(0xFFFFD740)
        state == GeminiLiveClient.LiveState.ERROR -> Color(0xFFFF5252)
        else -> Color(0xFF7928CA)
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .shadow(16.dp, CircleShape, spotColor = buttonColor)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(buttonColor, buttonColor.copy(alpha = 0.85f))
                )
            )
            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
            .testTag("mic_toggle_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = when {
                isConnecting -> Icons.Default.Refresh
                isLive -> Icons.Default.Mic
                else -> Icons.Default.MicOff
            },
            contentDescription = if (isLive) "Stop session" else "Start session",
            tint = if (state == GeminiLiveClient.LiveState.LISTENING) Color.Black else Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}
