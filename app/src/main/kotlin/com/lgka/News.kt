package com.lgka

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import lgka.News

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = LocalHomeViewModel.current
    val scope = rememberCoroutineScope()
    val list = vm.newsList
    LaunchedEffect(Unit) { if (list == null) vm.loadNews() }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.news)) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
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
                    onRefresh = { scope.launch { refreshing = true; vm.loadNews(FetchMode.Refresh); refreshing = false } },
                    modifier = Modifier.padding(padding)) {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp),
                               verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(list, key = { it.url }) { md -> NewsCard(md) { onOpen(md.url) } }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
            vm.newsFailed -> ErrorState(stringResource(R.string.server_connection_failed),
                onRetry = { scope.launch { vm.loadNews(FetchMode.Refresh) } },
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp))
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Loading() }
        }
    }
}

@Composable
private fun NewsCard(md: News.Metadata, onClick: () -> Unit) {
    Card(onClick = onClick, shape = CardShape) {
        Column(Modifier.padding(16.dp)) {
            Text(md.title, fontWeight = FontWeight.SemiBold)
            if (md.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(md.description, maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            MetaRow(md)
            if (md.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    md.tags.take(3).forEach { tag ->
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                             modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
                                 .padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.mehr_erfahren), style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetaRow(md: News.Metadata) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = color)
        Text(md.author, style = MaterialTheme.typography.bodySmall, color = color)
        Icon(Icons.Outlined.CalendarToday, null, Modifier.size(14.dp), tint = color)
        Text(md.createdDate, style = MaterialTheme.typography.bodySmall, color = color)
        Icon(Icons.Outlined.Visibility, null, Modifier.size(14.dp), tint = color)
        Text("${md.views} " + stringResource(R.string.views), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(url: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = LocalHomeViewModel.current
    val api = LocalContainer.current.api
    val context = LocalContext.current
    val md = vm.newsList?.firstOrNull { it.url == url }
    var article by remember { mutableStateOf<News.Article?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(url) {
        failed = false
        try { article = api.article(url) } catch (e: Exception) { failed = true }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            },
            actions = {
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_in_browser))
                }
            })
    }) { padding ->
        val a = article
        when {
            md == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.no_news_available))
            }
            a != null -> LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp)) {
                item {
                    Text(md.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                         modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(8.dp))
                    MetaRow(md)
                    Spacer(Modifier.height(16.dp))
                    a.content?.let { text ->
                        // tappable embedded links (news_detail RichText parity)
                        val accent = MaterialTheme.colorScheme.primary
                        val annotated = buildAnnotatedString {
                            append(text)
                            a.links.forEach { link ->
                                val lt = link["text"] ?: return@forEach
                                val target = link["url"] ?: return@forEach
                                val start = text.indexOf(lt)
                                if (start >= 0) {
                                    addLink(LinkAnnotation.Url(target), start, start + lt.length)
                                    addStyle(SpanStyle(color = accent, textDecoration = TextDecoration.Underline), start, start + lt.length)
                                }
                            }
                        }
                        Text(annotated, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                items(a.images) { image ->
                    (image["url"] as? String)?.let { imageUrl ->
                        val alt = (image["alt"] as? String)?.takeIf { it.isNotBlank() } ?: md.title
                        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .clickable(onClickLabel = stringResource(R.string.a11y_open_in_browser)) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, imageUrl.toUri()))
                                }) {
                            AsyncImage(model = imageUrl, contentDescription = alt,
                                       modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp))
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                                 Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                                 tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
                item {
                    val links = a.standaloneLinks.map { it["text"] to it["url"] } +
                        a.downloads.map { (it["title"] as? String) to (it["url"] as? String) }
                    links.forEach { (text, target) ->
                        if (text != null && target != null) {
                            OutlinedButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text(text, maxLines = 1)
                            }
                        }
                    }
                    // "Weitere Neuigkeiten" — recommended articles (parity)
                    val others = vm.newsList?.filter { it.url != md.url }?.take(3) ?: emptyList()
                    if (others.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(stringResource(R.string.weitere_neuigkeiten), style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        others.forEach { other ->
                            Card(onClick = { onOpen(other.url) }, shape = CardShape,
                                 modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(other.title, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${other.author} · ${other.createdDate}", style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            failed -> ErrorState(stringResource(R.string.server_connection_failed),
                onRetry = { failed = false; vm.launch { try { article = api.article(url) } catch (e: Exception) { failed = true } } },
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp))
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Loading() }
        }
    }
}
