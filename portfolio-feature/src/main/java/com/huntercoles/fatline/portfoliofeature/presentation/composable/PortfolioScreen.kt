package com.huntercoles.fatline.portfoliofeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.portfoliofeature.R
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioIntent
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioIntent.RefreshPortfolio
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioIntent.StockClicked
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioUiState
import com.huntercoles.fatline.portfoliofeature.presentation.PortfolioViewModel
import com.huntercoles.fatline.portfoliofeature.presentation.StockHolding
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PortfolioRoute(viewModel: PortfolioViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PortfolioScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun PortfolioScreen(
    uiState: PortfolioUiState,
    onIntent: (PortfolioIntent) -> Unit,
) {
    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = uiState.isLoading,
        onRefresh = { onIntent(RefreshPortfolio) },
    ) {
        if (uiState.holdings.isNotEmpty()) {
            PortfolioContent(
                uiState = uiState,
                onStockClick = { onIntent(StockClicked(it)) },
            )
        } else {
            EmptyPortfolioContent(uiState = uiState)
        }
    }
}

@Composable
private fun PortfolioContent(
    uiState: PortfolioUiState,
    onStockClick: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PortfolioSummaryCard(uiState = uiState)
        }
        
        items(uiState.holdings) { holding ->
            StockHoldingCard(
                holding = holding,
                onClick = { onStockClick(holding.symbol) }
            )
        }
    }
}

@Composable
private fun EmptyPortfolioContent(uiState: PortfolioUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.isError -> Text(
                text = stringResource(R.string.portfolio_error_loading),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            else -> Text(
                text = stringResource(R.string.portfolio_empty_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PortfolioSummaryCard(uiState: PortfolioUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.portfolio_total_value),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatCurrency(uiState.portfolioValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.portfolio_total_change)}: ${formatCurrency(uiState.totalChange)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.totalChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${formatPercent(uiState.totalChangePercent)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.totalChangePercent >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StockHoldingCard(
    holding: StockHolding,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = holding.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = holding.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${holding.shares} ${stringResource(R.string.portfolio_shares)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = formatCurrency(holding.totalValue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = formatCurrency(holding.totalReturn),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (holding.totalReturn >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${formatPercent(holding.returnPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (holding.returnPercent >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatCurrency(amount: Double): String {
    return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
}

private fun formatPercent(percent: Double): String {
    return String.format("%.2f", percent)
}
