package com.huntercoles.fatline.basicfeature.presentation.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huntercoles.fatline.basicfeature.R
import com.huntercoles.fatline.basicfeature.presentation.StockSearchIntent
import com.huntercoles.fatline.basicfeature.presentation.StockSearchUiState
import com.huntercoles.fatline.basicfeature.presentation.StockSearchViewModel
import com.huntercoles.fatline.basicfeature.presentation.model.StockDisplayable

@Composable
fun StockSearchRoute(viewModel: StockSearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StockSearchScreen(
        uiState = uiState,
        onIntent = viewModel::acceptIntent,
    )
}

@Composable
internal fun StockSearchScreen(
    uiState: StockSearchUiState,
    onIntent: (StockSearchIntent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Search Bar
        SearchBar(
            query = uiState.searchQuery,
            onQueryChange = { onIntent(StockSearchIntent.SearchQueryChanged(it)) },
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Content
        when {
            uiState.isLoading -> LoadingContent()
            uiState.isError -> ErrorContent()
            uiState.stocks.isNotEmpty() -> StockListContent(
                stocks = uiState.stocks,
                onStockClick = { onIntent(StockSearchIntent.StockClicked(it)) },
                onAddToPortfolio = { onIntent(StockSearchIntent.AddToPortfolio(it)) }
            )
            uiState.searchQuery.isEmpty() -> EmptySearchContent()
            else -> NoResultsContent()
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search stocks (e.g., AAPL, Tesla)") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        singleLine = true,
    )
}

@Composable
private fun StockListContent(
    stocks: List<StockDisplayable>,
    onStockClick: (String) -> Unit,
    onAddToPortfolio: (String) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(stocks) { stock ->
            StockCard(
                stock = stock,
                onClick = { onStockClick(stock.symbol) },
                onAddToPortfolio = { onAddToPortfolio(stock.symbol) }
            )
        }
    }
}

@Composable
private fun StockCard(
    stock: StockDisplayable,
    onClick: () -> Unit,
    onAddToPortfolio: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stock.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stock.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stock.exchange,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = stock.price,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${stock.change} (${stock.changePercent})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stock.isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            
            IconButton(onClick = onAddToPortfolio) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to Portfolio",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Error loading stocks. Please try again.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptySearchContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Search for stocks",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Enter a stock symbol or company name",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoResultsContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No stocks found.\nTry a different search term.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
