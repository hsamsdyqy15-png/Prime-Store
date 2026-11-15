package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppItem
import com.example.data.model.DownloadItem
import com.example.data.model.InstalledPackageSummary
import com.example.data.model.resolveAppActionState
import com.example.ui.HomeSection
import com.example.ui.theme.PrimeTheme

val POPULAR_SEARCH_TAGS = listOf(
    "Minecraft",
    "GTA V",
    "Roblox",
    "Free Fire",
    "Subway Surfers",
    "PUBG"
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    searchResults: List<AppItem>,
    searchHistory: List<String>,
    activeSection: HomeSection = HomeSection.GAMES,
    searchAllTabs: Boolean = false,
    downloads: List<DownloadItem> = emptyList(),
    installedPkgByAppId: Map<String, InstalledPackageSummary> = emptyMap(),
    downloadedReadyIds: Set<String> = emptySet(),
    installingAppIds: Set<String> = emptySet(),
    signatureConflictAppIds: Set<String> = emptySet(),
    isPullRefreshing: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    onQueryChange: (String) -> Unit,
    onToggleSearchAllTabs: (Boolean) -> Unit = {},
    onSearchTagClick: (String) -> Unit,
    onRemoveHistoryItem: (String) -> Unit,
    onClearHistory: () -> Unit,
    onAppClick: (String) -> Unit,
    onDownloadClick: (String) -> Unit = {},
    onInstallClick: (String) -> Unit = onDownloadClick,
    onOpenAppClick: (String) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PrimeTheme.colors
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        // Search Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCloseSearch,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.close),
                    tint = colors.textPrimary
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.search_hint),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(id = R.string.search),
                        tint = colors.accent
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(id = R.string.delete),
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.card,
                    unfocusedContainerColor = colors.card,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedIndicatorColor = colors.accent,
                    unfocusedIndicatorColor = colors.border
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_text_input")
            )
        }

        // Active Search Scope & Search All Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val scopeLabel = when {
                searchAllTabs -> stringResource(R.string.search_scope_all)
                activeSection == HomeSection.GAMES -> "🎮 ${stringResource(R.string.search_scope_games)}"
                else -> "📱 ${stringResource(R.string.search_scope_apps)}"
            }
            Text(
                text = scopeLabel,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (searchAllTabs) colors.accent else colors.cardAlt)
                    .border(1.dp, if (searchAllTabs) colors.accent else colors.border, RoundedCornerShape(12.dp))
                    .clickable { onToggleSearchAllTabs(!searchAllTabs) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("search_scope_all_chip"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_scope_all),
                    color = if (searchAllTabs) colors.textOnAccent else colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (query.isBlank()) {
                    // Popular Searches Section
                    item(key = "popular_searches", contentType = "popular_tags") {
                        Column(modifier = Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(id = R.string.popular_searches),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "🔥", fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                POPULAR_SEARCH_TAGS.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.card)
                                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                                            .clickable { onSearchTagClick(tag) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tag,
                                            color = colors.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Recent Search History
                    if (searchHistory.isNotEmpty()) {
                        item(key = "recent_header", contentType = "history_header") {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.History,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(id = R.string.recent_searches),
                                            color = colors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = stringResource(id = R.string.clear_all),
                                        color = colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .clickable { onClearHistory() }
                                            .padding(8.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        items(
                            items = searchHistory,
                            key = { "hist_$it" },
                            contentType = { "history_item" }
                        ) { historyItem ->
                            Row(
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSearchTagClick(historyItem) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = historyItem,
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onRemoveHistoryItem(historyItem) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(id = R.string.delete),
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Search Results Header
                    item(key = "results_header", contentType = "results_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.search_results),
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "(${searchResults.size})", color = colors.accent, fontSize = 14.sp)
                        }
                    }

                    if (searchResults.isEmpty()) {
                        item(key = "results_empty", contentType = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "🔍", fontSize = 42.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.search_no_results, query),
                                        color = colors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = searchResults,
                            key = { "res_${it.id}" },
                            contentType = { "search_result" }
                        ) { app ->
                            val dlItem = downloads.find { it.appId == app.id }
                            val installedInfo = installedPkgByAppId[app.id]
                            val actionState = resolveAppActionState(
                                app = app,
                                downloadItem = dlItem,
                                isApkReady = downloadedReadyIds.contains(app.id),
                                isInstalling = installingAppIds.contains(app.id),
                                hasSignatureConflict = signatureConflictAppIds.contains(app.id),
                                installedInfo = installedInfo
                            )
                            ApptekaCard(
                                app = app,
                                actionState = actionState,
                                installedPackageName = installedInfo?.packageName,
                                onAppClick = { onAppClick(app.id) },
                                onDownloadClick = { onDownloadClick(app.id) },
                                onInstallClick = { onInstallClick(app.id) },
                                onOpenAppClick = onOpenAppClick,
                                onPauseDownload = { onPauseDownload(app.id) },
                                onResumeDownload = { onResumeDownload(app.id) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                item(key = "search_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
