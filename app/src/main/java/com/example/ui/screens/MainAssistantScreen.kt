package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.AssistantState
import com.example.ui.components.AnimatedMaxOrb
import com.example.ui.components.AudioWaveformCanvas
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.MaxViewModel

@Composable
fun MainAssistantScreen(
    viewModel: MaxViewModel,
    onOpenPermissionsHub: () -> Unit
) {
    val context = LocalContext.current
    val assistantState by viewModel.assistantState.collectAsState()
    val sassyStatus by viewModel.sassyStatus.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val logHistory by viewModel.logHistory.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()

    var showLogsDrawer by remember { mutableStateOf(false) }
    var userTextInput by remember { mutableStateOf("") }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Futuristic Starfield & Grid Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Radial Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CyberCyan.copy(alpha = 0.12f),
                        ElectricViolet.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(width / 2f, height / 2.5f),
                    radius = width * 0.8f
                ),
                radius = width * 0.8f,
                center = Offset(width / 2f, height / 2.5f)
            )

            // Grid lines
            val gridStep = 80f
            var x = 0f
            while (x < width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.03f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }
        }

        // Main Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding + 12.dp, bottom = bottomPadding + 12.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAX",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isServiceRunning) NeonGreen else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "BG SERVICE" else "STANDBY",
                        fontSize = 10.sp,
                        color = if (isServiceRunning) NeonGreen else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            if (isServiceRunning) {
                                viewModel.stopService(context)
                            } else {
                                viewModel.startService(context)
                            }
                        },
                        modifier = Modifier.testTag("service_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Toggle BG Service",
                            tint = if (isServiceRunning) NeonPink else NeonGreen
                        )
                    }

                    IconButton(
                        onClick = onOpenPermissionsHub,
                        modifier = Modifier.testTag("permissions_hub_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Permissions",
                            tint = CyberCyan
                        )
                    }

                    IconButton(
                        onClick = { showLogsDrawer = !showLogsDrawer },
                        modifier = Modifier.testTag("transcript_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Logs",
                            tint = ElectricViolet
                        )
                    }
                }
            }

            // Wake-Word pill badge
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Wake Word",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wake-Word: 'MAX'",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // Central Animated Orb
            AnimatedMaxOrb(
                state = assistantState,
                audioLevel = audioLevel,
                onClick = { viewModel.toggleOrbListening(context) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Sassy Banner Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MAX",
                        fontSize = 12.sp,
                        color = NeonPink,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$sassyStatus\"",
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("max_sassy_status_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Command Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    listOf(
                        "Open YouTube",
                        "Call Mom",
                        "WhatsApp Alex",
                        "Open Calculator",
                        "Email boss"
                    )
                ) { chip ->
                    Card(
                        modifier = Modifier
                            .clickable { viewModel.sendTextCommand(chip, context) }
                            .testTag("quick_chip_$chip"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = chip,
                            fontSize = 12.sp,
                            color = CyberCyan,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Input Command Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = userTextInput,
                    onValueChange = { userTextInput = it },
                    placeholder = { Text("Ask MAX anything or command app...", color = TextSecondary, fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = DarkSurfaceVariant,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (userTextInput.isNotBlank()) {
                            viewModel.sendTextCommand(userTextInput, context)
                            userTextInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(CyberCyan, RoundedCornerShape(14.dp))
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = DarkBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Waveform Visualizer
            AudioWaveformCanvas(
                state = assistantState,
                audioLevel = audioLevel,
                modifier = Modifier.testTag("audio_waveform_canvas")
            )

            // Transcript Drawer View
            AnimatedVisibility(visible = showLogsDrawer) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Live Activity & Tool Log",
                            fontSize = 12.sp,
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(logHistory) { log ->
                                val tagColor = when (log.sender) {
                                    "MAX" -> NeonPink
                                    "USER" -> CyberCyan
                                    "TOOL" -> NeonGreen
                                    else -> TextSecondary
                                }
                                Text(
                                    text = "[${log.sender}] ${log.text}",
                                    fontSize = 12.sp,
                                    color = tagColor,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
