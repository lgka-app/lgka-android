package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import lgka.api.NewsArticle
import lgka.api.Resource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val haptics = rememberHaptics()
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    val list = vm.newsList
    LaunchedEffect(Unit) { if (list == null) vm.refresh(setOf(Resource.News)) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.news)) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            })
    }) { padding ->
        when {
            list != null && list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.no_news_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            list != null -> {
                var refreshing by remember { mutableStateOf(false) }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { haptics.medium(); scope.launch { refreshing = true; vm.refresh(setOf(Resource.News)); refreshing = false } },
                    modifier = Modifier.padding(top = padding.calculateTopPadding())) {
                    LazyColumn(Modifier.fillMaxSize().readableWidth().padding(horizontal = 20.dp),
                               contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                               verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // lazy keys must be unique: an article listed twice by the school site would crash the list
                        items(list.distinctBy { it.url }, key = { it.url }) { md -> NewsCard(md, Modifier.testTag("news.row")) { onOpen(md.url) } }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
            vm.newsFailed -> ErrorState(stringResource(R.string.server_connection_failed),
                onRetry = { scope.launch { vm.refresh(setOf(Resource.News)) } },
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp))
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Loading() }
        }
    }
}

@Composable
fun NewsCard(md: NewsArticle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    // the iOS NewsCard: bold title with the accent newspaper glyph, date · views,
    // three-line excerpt, tag chips, author
    Card(onClick = { haptics.light(); onClick() }, shape = CardShape, modifier = modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(md.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                     maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Newspaper, null, Modifier.padding(top = 4.dp).size(20.dp), tint = accent)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(md.createdDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(4.dp))
                Text("${md.views}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (md.description.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(md.description, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (md.tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    md.tags.forEach { tag ->
                        Text(tag, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = accent,
                             modifier = Modifier.background(accent.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                 .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                 .padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(4.dp))
                Text(md.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                     maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
