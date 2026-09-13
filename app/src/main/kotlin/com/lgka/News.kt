package com.lgka

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
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
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Download
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
private fun NewsCard(md: NewsArticle, modifier: Modifier = Modifier, onClick: () -> Unit) {
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

@Composable
private fun MetaRow(md: NewsArticle) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // Each icon+text pair wraps as a unit — a view count must never break mid-word.
    @Composable fun Item(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, softWrap = false)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Item(Icons.Outlined.Person, md.author)
        Item(Icons.Outlined.CalendarToday, md.createdDate)
        Item(Icons.Outlined.Visibility, "${md.views} " + stringResource(R.string.views))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(url: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val haptics = rememberHaptics()
    val uriHandler = LocalUriHandler.current
    val vm = LocalHomeViewModel.current
    val context = LocalContext.current
    // Articles arrive fully parsed with the news resource; nothing to fetch per article.
    val md = vm.article(url)
    var photo by remember { mutableStateOf<Pair<String, String>?>(null) }
    photo?.let { (url, alt) -> PhotoViewerDialog(url, alt) { photo = null } }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            },
            actions = {
                IconButton(onClick = { haptics.light(); uriHandler.openUri(url) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_in_browser))
                }
            })
    }) { padding ->
        val a = md
        when {
            a == null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.no_news_available))
            }
            else -> LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).readableWidth().padding(horizontal = 20.dp).testTag("news.detail"),
                                    contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
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
                                val lt = link.text
                                val target = link.url
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
                    image.url.let { imageUrl ->
                        val alt = image.alt?.takeIf { it.isNotBlank() } ?: md.title
                        // tap → full-screen, zoomable photo viewer
                        AsyncImage(model = imageUrl, contentDescription = alt,
                                   modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(bottom = 12.dp)
                                       .clip(RoundedCornerShape(12.dp))
                                       .clickable(onClickLabel = stringResource(R.string.a11y_open_photo)) { haptics.light(); photo = imageUrl to alt })
                    }
                }
                item {
                    // news_detail_screen parity: downloads with a file-type glyph and size,
                    // websites with their favicon and domain
                    a.downloads.forEach { dl ->
                        val title = dl.title
                        val target = dl.url
                        ActionRow(title = title, subtitle = dl.size,
                                  icon = fileTypeIcon(dl.fileType),
                                  trailing = Icons.Outlined.Download,
                                  onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) } })
                    }
                    a.standaloneLinks.forEach { link ->
                        val title = link.text
                        val target = link.url
                        val host = target.toUri().host?.removePrefix("www.")
                        // no favicon service: that would send the reader's IP to a third party
                        ActionRow(title = title, subtitle = host, icon = Icons.Outlined.Link,
                                  trailing = Icons.AutoMirrored.Filled.OpenInNew,
                                  onClick = { uriHandler.openUri(target) })
                    }
                    // "Weitere Neuigkeiten" — recommended articles (parity)
                    val others = vm.newsList?.filter { it.url != md.url }?.take(3) ?: emptyList()
                    if (others.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(stringResource(R.string.weitere_neuigkeiten), style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(12.dp))
                        // the same cards as the news list, not bare titles
                        others.forEach { other -> NewsCard(other, Modifier.padding(bottom = 12.dp)) { onOpen(other.url) } }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Flutter `_getFileTypeIcon` mapping onto Material Symbols. */
private fun fileTypeIcon(type: String?): androidx.compose.ui.graphics.vector.ImageVector = when (type?.lowercase()) {
    "audio", "sound" -> Icons.Outlined.Headphones
    "video", "movie" -> Icons.Outlined.Videocam
    "image", "picture", "photo" -> Icons.Outlined.Image
    "pdf", "document" -> Icons.Outlined.PictureAsPdf
    "archive", "zip", "rar" -> Icons.Outlined.Archive
    "text" -> Icons.AutoMirrored.Outlined.TextSnippet
    "spreadsheet", "excel" -> Icons.Outlined.TableChart
    "presentation", "powerpoint" -> Icons.Outlined.Slideshow
    else -> Icons.Outlined.Download
}

/** A download or website row in an article (news_detail_screen `_buildDownloadButton` / link button). */
@Composable
private fun ActionRow(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector,
                      trailing: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Card(onClick = { haptics.light(); onClick() }, shape = CardShape, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(trailing, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Full-screen photo viewer: pinch and double-tap zoom on a black stage. */
@Composable
private fun PhotoViewerDialog(url: String, alt: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, panDelta, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        pan = if (scale > 1f) pan + panDelta else Offset.Zero
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; if (scale == 1f) pan = Offset.Zero })
                }) {
            AsyncImage(model = url, contentDescription = alt, contentScale = ContentScale.Fit,
                       modifier = Modifier.fillMaxSize()
                           .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y))
            IconButton(onClick = onClose, modifier = Modifier.safeDrawingPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(Icons.Filled.Close, stringResource(R.string.a11y_close), tint = Color.White)
            }
        }
    }
}
