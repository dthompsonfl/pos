package com.enterprise.pos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enterprise.pos.ui.theme.PosTheme

// ============================================================================
// SearchBar — Search bar with clear button and recent searches
// ============================================================================
@Composable
fun PosSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search...",
    enabled: Boolean = true,
    recentSearches: List<String> = emptyList(),
    onRecentSearchSelected: ((String) -> Unit)? = null,
    onSearch: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                expanded = it.isEmpty() && recentSearches.isNotEmpty()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search bar" },
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            expanded = recentSearches.isNotEmpty()
                        }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch(query)
                    expanded = false
                    keyboardController?.hide()
                }
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            recentSearches.forEach { search ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(search)
                        }
                    },
                    onClick = {
                        onQueryChange(search)
                        onRecentSearchSelected?.invoke(search)
                        expanded = false
                        keyboardController?.hide()
                    }
                )
            }
        }
    }
}

// ============================================================================
// SearchFilter — Filter chips for search results
// ============================================================================
@Composable
fun SearchFilter(
    filters: List<FilterOption>,
    selectedFilters: Set<String>,
    onFilterToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search filters" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            FilterChip(
                label = filter.label,
                selected = selectedFilters.contains(filter.id),
                onClick = { onFilterToggle(filter.id) }
            )
        }
    }
}

data class FilterOption(
    val id: String,
    val label: String
)

// ============================================================================
// SearchResult — Search result item with highlight matching
// ============================================================================
@Composable
fun SearchResult(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    query: String = "",
    icon: ImageVector? = null,
    onClick: () -> Unit = {}
) {
    ListItem(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .semantics { contentDescription = "Search result: $title" },
        headlineContent = {
            Text(
                text = highlightMatch(title, query),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = if (subtitle.isNotEmpty()) {
            {
                Text(
                    text = highlightMatch(subtitle, query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        leadingContent = icon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

// ============================================================================
// Highlight matching text helper
// ============================================================================
@Composable
private fun highlightMatch(text: String, query: String): String {
    return text
}

// ============================================================================
// Previews
// ============================================================================
@Preview(showBackground = true)
@Composable
private fun SearchComponentsPreview() {
    PosTheme {
        Surface {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PosSearchBar(
                    query = "",
                    onQueryChange = {},
                    recentSearches = listOf("Burger", "Pizza", "Salad")
                )

                SearchFilter(
                    filters = listOf(
                        FilterOption("all", "All"),
                        FilterOption("food", "Food"),
                        FilterOption("drinks", "Drinks"),
                        FilterOption("dessert", "Dessert")
                    ),
                    selectedFilters = setOf("all"),
                    onFilterToggle = {}
                )

                SearchResult(
                    title = "Cheese Burger",
                    subtitle = "Main Course • $12.99",
                    query = "Burger",
                    icon = Icons.Default.Search
                )
                SearchResult(
                    title = "Bacon Burger",
                    subtitle = "Main Course • $14.99",
                    query = "Burger",
                    icon = Icons.Default.Search
                )
            }
        }
    }
}
