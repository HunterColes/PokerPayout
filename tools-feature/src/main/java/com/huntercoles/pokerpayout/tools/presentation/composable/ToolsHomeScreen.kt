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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huntercoles.pokerpayout.core.design.PokerColors
import com.huntercoles.pokerpayout.core.design.PokerDimens
import com.huntercoles.pokerpayout.core.navigation.NavigationCommand
import com.huntercoles.pokerpayout.core.navigation.NavigationDestination
import com.huntercoles.pokerpayout.core.navigation.NavigationManager

data class ToolItem(
    val title: String,
    val icon: ImageVector,
    val destination: NavigationDestination
)

@Composable
fun ToolsHomeScreen(
    navigationManager: NavigationManager
) {
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
                        navigationManager.navigate(object : NavigationCommand {
                            override val destination = tool.destination
                        })
                    }
                )
            }
        }
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
