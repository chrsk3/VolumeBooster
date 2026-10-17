package com.chrsk.volumebooster.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrsk.volumebooster.EngineType
import com.chrsk.volumebooster.ui.theme.AccentAmber
import com.chrsk.volumebooster.ui.theme.AccentCyan
import com.chrsk.volumebooster.ui.theme.AccentCyanDim
import com.chrsk.volumebooster.ui.theme.AccentGreen
import com.chrsk.volumebooster.ui.theme.AccentRed
import com.chrsk.volumebooster.ui.theme.AmoledBlack
import com.chrsk.volumebooster.ui.theme.DarkBorder
import com.chrsk.volumebooster.ui.theme.DarkCard
import com.chrsk.volumebooster.ui.theme.DarkCardHighlight
import com.chrsk.volumebooster.ui.theme.TextPrimary
import com.chrsk.volumebooster.ui.theme.TextSecondary
import com.chrsk.volumebooster.ui.theme.TextTertiary
import java.util.Locale

@Composable
fun VolumeBoosterScreen(
    viewModel: VolumeBoosterViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = AmoledBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar / Title
            AppHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // Status Indicator (Active / Inactive / Audio Output Route)
            StatusIndicatorCard(
                isEnabled = state.isEnabled,
                engineType = state.engineType,
                connectedDevice = state.connectedDevice,
                sessionId = state.sessionId
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Master Switch Card (Large toggle at top)
            MasterToggleCard(
                isEnabled = state.isEnabled,
                onToggle = { viewModel.toggleMaster() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Gain Preamp Card (Slider 0.0 to +12.0 dB with real-time numeric readout)
            PreampGainCard(
                gainDb = state.gainDb,
                isEnabled = state.isEnabled,
                onGainChange = { viewModel.onGainChange(it) },
                onPresetClick = { viewModel.setPreset(it) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Dedicated Peak Limiter Card (Poweramp style)
            PeakLimiterCard(
                isLimiterEnabled = state.isLimiterEnabled,
                isMasterEnabled = state.isEnabled,
                currentGain = state.gainDb,
                onToggle = { viewModel.toggleLimiter() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Technical Audio Pipeline Info Footer
            AudioEngineSpecsFooter(
                engineType = state.engineType,
                sessionId = state.sessionId,
                lastError = state.lastError
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "VolumeBooster",
                style = MaterialTheme.typography.headlineMedium.copy(
                    letterSpacing = (-0.5).sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = TextPrimary
            )
            Text(
                text = "Hi-Fi Clean Preamp & Peak Limiter",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(DarkCard)
                .border(1.dp, DarkBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StatusIndicatorCard(
    isEnabled: Boolean,
    engineType: EngineType,
    connectedDevice: String?,
    sessionId: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Status row 1: Processing Engine State
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dotColor = if (isEnabled) AccentGreen else TextTertiary
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Text(
                        text = if (isEnabled) "STATUS: ATIVO" else "STATUS: INATIVO (BYPASS)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isEnabled) AccentGreen else TextSecondary
                    )
                }

                val engineLabel = when (engineType) {
                    EngineType.DYNAMICS_PROCESSING -> "DynamicsProcessing (HAL)"
                    EngineType.LOUDNESS_ENHANCER -> "LoudnessEnhancer"
                    EngineType.NONE -> if (isEnabled) "Carregando..." else "Desconectado"
                }

                Text(
                    text = engineLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan
                )
            }

            // Status row 2: Audio Routing / Bluetooth Destination
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (connectedDevice != null) Icons.Default.Bluetooth else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (connectedDevice != null) AccentCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = connectedDevice?.let { "Conectado: $it" } ?: "Saída de Áudio Global (Sessão $sessionId)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun MasterToggleCard(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isEnabled) AccentCyan else DarkBorder,
        animationSpec = tween(300),
        label = "masterBorder"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) DarkCardHighlight else DarkCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) AccentCyan.copy(alpha = 0.15f) else AmoledBlack)
                        .border(
                            width = 1.5.dp,
                            color = if (isEnabled) AccentCyan else TextTertiary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (isEnabled) AccentCyan else TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text = "Ganho Master",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isEnabled) "Pré-amplificação operando" else "Efeito desligado (Pass-through)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isEnabled) AccentGreen else TextSecondary
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AmoledBlack,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkCardHighlight
                )
            )
        }
    }
}

@Composable
private fun PreampGainCard(
    gainDb: Float,
    isEnabled: Boolean,
    onGainChange: (Float) -> Unit,
    onPresetClick: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "PRÉ-AMPLIFICADOR",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentCyan
                    )
                    Text(
                        text = "Ganho Limpo de Áudio",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }

                // Digital Numeric Readout in Real-Time (+X.X dB)
                Text(
                    text = String.format(Locale.US, "+%.1f dB", gainDb),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        color = if (isEnabled) AccentCyan else TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continuous Gain Slider (0.0 dB to +12.0 dB)
            Slider(
                value = gainDb,
                onValueChange = onGainChange,
                valueRange = 0.0f..12.0f,
                steps = 119, // 0.1 dB increments over 12.0 dB span
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = if (isEnabled) AccentCyan else TextTertiary,
                    activeTrackColor = if (isEnabled) AccentCyan else TextTertiary,
                    inactiveTrackColor = DarkBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // dB Scale Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0.0 dB", "+3.0", "+6.0", "+9.0", "+12.0 dB").forEach { mark ->
                    Text(
                        text = mark,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Preset Buttons
            Text(
                text = "PRESETS RÁPIDOS",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.0f to "0 dB", 3.0f to "+3 dB", 6.0f to "+6 dB", 9.0f to "+9 dB", 12.0f to "+12 dB").forEach { (valDb, label) ->
                    val isSelected = kotlin.math.abs(gainDb - valDb) < 0.05f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected && isEnabled) AccentCyan else DarkCardHighlight)
                            .clickable(enabled = isEnabled) { onPresetClick(valDb) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected && isEnabled) AmoledBlack else TextPrimary
                        )
                    }
                }
            }

            // High Gain Notice
            if (gainDb > 6.0f && isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Ganho elevado: o Limitador (Limit) protegerá os graves contra clipping.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = AccentAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun PeakLimiterCard(
    isLimiterEnabled: Boolean,
    isMasterEnabled: Boolean,
    currentGain: Float,
    onToggle: () -> Unit
) {
    val isLimiterActiveAndWorking = isMasterEnabled && isLimiterEnabled

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isLimiterActiveAndWorking) AccentGreen.copy(alpha = 0.4f) else DarkBorder
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLimiterActiveAndWorking) AccentGreen.copy(alpha = 0.15f) else DarkCardHighlight
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (isLimiterActiveAndWorking) AccentGreen else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Limit (Peak Limiter)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Anti-Clipping estilo Poweramp",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = if (isLimiterActiveAndWorking) AccentGreen else TextSecondary
                        )
                    }
                }

                Switch(
                    checked = isLimiterEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmoledBlack,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkCardHighlight
                    )
                )
            }

            // Technical specs badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCardHighlight)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Threshold: -0.5 dBFS • Attack: 1.0 ms • Release: 60 ms • Slope: 20:1",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan
                )
            }

            Text(
                text = "Configuração calibrada para estancar picos transitórios e suprimir estalos em frequências subgraves ao tocar faixas masterizadas em 0 dBFS.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun AudioEngineSpecsFooter(
    engineType: EngineType,
    sessionId: Int,
    lastError: String?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Android Audio HAL Pipeline • Stereo (L/R) • Sessão Global $sessionId",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )

        lastError?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.labelSmall,
                color = AccentAmber
            )
        }
    }
}
