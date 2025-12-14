package com.huntercoles.pokerpayout.tools.presentation.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDialog
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationManager
import com.huntercoles.pokerpayout.core.preferences.AudioPreferences
import com.huntercoles.pokerpayout.tools.presentation.ToolsViewModel

data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val destination: NavigationDestination?,
    val isVolumeControl: Boolean = false
)

@Composable
fun ToolsHomeScreen(
    navigationManager: NavigationManager
) {
    val toolsViewModel: ToolsViewModel = hiltViewModel()
    val audioPreferences = toolsViewModel.audioPreferences
    var showVolumeDialog by remember { mutableStateOf(false) }
    
    val tools = listOf(
        ToolItem(
            title = "Odds",
            icon = Icons.Default.Casino,
            destination = NavigationDestination.OddsCalculator
        ),
        ToolItem(
            title = "Ranks",
            icon = Icons.Default.List,
            destination = NavigationDestination.HandRanks
        ),
        ToolItem(
            title = "Chip Calculator",
            icon = Icons.Default.MonetizationOn,
            destination = NavigationDestination.ChipCalculator
        ),
        ToolItem(
            title = "Settings",
            icon = Icons.Default.Settings,
            destination = null,
            isVolumeControl = true
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PokerDimens.SpacingDefault),
        verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingDefault)
    ) {
        Text(
            text = "🛠️ Tools",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = PokerColors.PokerGold
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(PokerDimens.SpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(PokerDimens.SpacingDefault),
            verticalArrangement = Arrangement.spacedBy(PokerDimens.SpacingDefault)
        ) {
            items(tools) { tool ->
                ToolCard(
                    tool = tool,
                    onClick = {
                        if (tool.isVolumeControl) {
                            showVolumeDialog = true
                        } else {
                            tool.destination?.let { destination ->
                                navigationManager.navigate(object : NavigationCommand {
                                    override val destination = destination
                                })
                            }
                        }
                    }
                )
            }
        }
    }
    
    // Volume Control Dialog
    if (showVolumeDialog) {
        VolumeControlDialog(
            audioPreferences = audioPreferences,
            onDismiss = { showVolumeDialog = false }
        )
    }
}

@Composable
fun ToolCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = PokerColors.DarkGreen
        ),
        border = BorderStroke(1.dp, PokerColors.AccentGreen),
        elevation = CardDefaults.cardElevation(
            defaultElevation = PokerDimens.ElevationDefault
        ),
        shape = RoundedCornerShape(PokerDimens.CornerMedium)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PokerDimens.SpacingDefault),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.title,
                tint = PokerColors.PokerGold,
                modifier = Modifier.size(PokerDimens.IconXLarge)
            )
            
            Spacer(modifier = Modifier.height(PokerDimens.SpacingSmall))
            
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = PokerColors.CardWhite,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VolumeControlDialog(
    audioPreferences: AudioPreferences,
    onDismiss: () -> Unit
) {
    val volume by audioPreferences.volume.collectAsStateWithLifecycle(initialValue = 1f)
    val isMuted by audioPreferences.isMuted.collectAsStateWithLifecycle(initialValue = false)
    
    PokerDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PokerColors.FeltGreen,
            border = BorderStroke(1.dp, PokerColors.PokerGold.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume icon button
                IconButton(
                    onClick = { audioPreferences.toggleMute() }
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) PokerColors.CardWhite.copy(alpha = 0.5f) else PokerColors.PokerGold,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Volume slider with dotted line behind it
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Dotted line behind slider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .drawBehind {
                                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                drawLine(
                                    color = PokerColors.PokerGold.copy(alpha = 0.3f),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2),
                                    strokeWidth = 2f,
                                    pathEffect = pathEffect
                                )
                            }
                    )
                    
                    // Slider
                    Slider(
                        value = volume,
                        onValueChange = { audioPreferences.setVolume(it) },
                        valueRange = 0f..1f,
                        enabled = !isMuted,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isMuted) PokerColors.CardWhite.copy(alpha = 0.3f) else PokerColors.PokerGold,
                            activeTrackColor = if (isMuted) PokerColors.CardWhite.copy(alpha = 0.3f) else PokerColors.PokerGold,
                            inactiveTrackColor = PokerColors.DarkGreen,
                            disabledThumbColor = PokerColors.CardWhite.copy(alpha = 0.3f),
                            disabledActiveTrackColor = PokerColors.CardWhite.copy(alpha = 0.3f),
                            disabledInactiveTrackColor = PokerColors.DarkGreen
                        )
                    )
                }
            }
        }
    }
}
