package com.lgka

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── News ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val list = HomeModel.newsList
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(L.s("news")) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            })
    }) { padding ->
        when {
            list != null -> LazyColumn(
                Modifier.padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(list) { i, md ->
                    Card(shape = RoundedCornerShape(16.dp),
                         modifier = Modifier.clickable { nav.navigate("newsDetail/$i") }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(md.title, fontWeight = FontWeight.SemiBold)
                            if (md.description.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(md.description, maxLines = 2,
                                     style = MaterialTheme.typography.bodyMedium,
                                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("${md.author} · ${md.createdDate}",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }
            HomeModel.newsFailed -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(L.s("serverConnectionFailed"))
                Spacer(Modifier.height(12.dp))
                Button(onClick = { scope.launch { HomeModel.loadNews(FetchMode.Refresh) } }) {
                    Text(L.s("tryAgain"))
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(nav: NavController, index: Int) {
    val md = HomeModel.newsList?.getOrNull(index) ?: return
    val context = LocalContext.current
    var article by remember { mutableStateOf<lgka.News.Article?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(md.url) {
        try { article = SchoolApi.article(md.url) } catch (e: Exception) { failed = true }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            },
            actions = {
                IconButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(md.url)))
                }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, L.s("openInBrowser")) }
            })
    }) { padding ->
        val a = article
        when {
            a != null -> LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp)) {
                item {
                    Text(md.title, style = MaterialTheme.typography.headlineSmall,
                         fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${md.author} · ${md.createdDate} · ${md.views} 👁",
                         style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    a.content?.let { Text(it) }
                    Spacer(Modifier.height(16.dp))
                }
                items(a.images.size) { i ->
                    (a.images[i]["url"] as? String)?.let { url ->
                        AsyncImage(model = url, contentDescription = null,
                                   modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    }
                }
                item {
                    // "Weitere Neuigkeiten" — recommended articles (parity)
                    val others = HomeModel.newsList?.withIndex()
                        ?.filter { it.value.url != md.url }?.take(3) ?: emptyList()
                    if (others.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(if (L.isGerman) "Weitere Neuigkeiten" else "More news",
                             style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        others.forEach { (idx, other) ->
                            Card(shape = RoundedCornerShape(16.dp),
                                 modifier = Modifier.padding(bottom = 12.dp).clickable {
                                     nav.navigate("newsDetail/$idx")
                                 }) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(other.title, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${other.author} · ${other.createdDate}",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                    (a.standaloneLinks.map { it["text"] to it["url"] } +
                     a.downloads.map { (it["title"] as? String) to (it["url"] as? String) })
                        .forEach { (text, url) ->
                            if (text != null && url != null) {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    Text(text, maxLines = 1)
                                }
                            }
                        }
                    Spacer(Modifier.height(24.dp))
                }
            }
            failed -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(L.s("serverConnectionFailed"))
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

// ── PDF viewer (PdfRenderer + pdfbox-android page-text search) ─────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerDialog(request: PdfRequest, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose,
           properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            PdfViewerContent(request, onClose)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerContent(request: PdfRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchIndex by remember { mutableStateOf(0) }
    var scale by remember { mutableStateOf(1f) }
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(request.file) {
        withContext(Dispatchers.IO) {
            val fd = ParcelFileDescriptor.open(request.file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(fd).use { renderer ->
                pages = (0 until renderer.pageCount).map { i ->
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2,
                                                      Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
            pageTexts = runCatching { pageTextsAndroid(request.file) }.getOrDefault(emptyList())
        }
        request.targetPage?.let { display ->
            listState.scrollToItem((display - 2).coerceAtLeast(0))
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(request.title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, null) }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Filled.Search, L.s("searchInPdf"))
                }
                IconButton(onClick = {
                    val uri = FileProvider.getUriForFile(context, "com.lgka.files", request.file)
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, L.s("sharePdf")))
                }) { Icon(Icons.Filled.Share, L.s("sharePdf")) }
            })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            if (showSearch) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text(L.s("searchInPdf")) },
                        singleLine = true, modifier = Modifier.weight(1f))
                    if (matches.isNotEmpty()) {
                        Text("  ${matchIndex + 1}/${matches.size}  ",
                             fontSize = 13.sp)
                        IconButton(onClick = {
                            matchIndex = (matchIndex - 1 + matches.size) % matches.size
                            scope.launch { listState.animateScrollToItem(matches[matchIndex]) }
                        }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = {
                            matchIndex = (matchIndex + 1) % matches.size
                            scope.launch { listState.animateScrollToItem(matches[matchIndex]) }
                        }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                    } else {
                        IconButton(onClick = {
                            val q = query.trim().lowercase()
                            // schedule-PDF parity: searching a class persists it
                            if (request.targetPage != null &&
                                q.matches(Regex("^(j1[12]|\\d{1,2}[a-e])$"))) {
                                prefs.selectedScheduleClass = q
                            }
                            matches = pageTexts.withIndex()
                                .filter { it.value.contains(q) && q.isNotEmpty() }
                                .map { it.index }
                            matchIndex = 0
                            matches.firstOrNull()?.let {
                                scope.launch { listState.animateScrollToItem(it) }
                            }
                        }) { Icon(Icons.Filled.Search, null) }
                    }
                }
            }
            if (pages.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale)) {
                    itemsIndexed(pages) { _, bmp ->
                        Image(bmp.asImageBitmap(), null,
                              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    }
                }
            }
        }
    }
}

// ── Krankmeldung info + WebView screens ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrankmeldungInfoScreen(nav: NavController) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(L.s("krankmeldungInfoHeader")) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            })
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            listOf(Icons.Outlined.WarningAmber to L.s("krankmeldungDisclaimer"),
                   Icons.Outlined.SupportAgent to L.s("krankmeldungContact"))
                .forEach { (icon, text) ->
                    Card(shape = RoundedCornerShape(16.dp),
                         modifier = Modifier.padding(bottom = 16.dp)) {
                        Row(Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(18.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    prefs.krankmeldungInfoShown = true
                    nav.navigate("krankmeldungForm") { popUpTo("home") }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Outlined.MedicalServices, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(L.s("krankmeldungButton"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(nav: NavController, url: String, title: String) {
    var progress by remember { mutableStateOf(0) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.Filled.Close, null)
                }
            })
    }) { padding ->
        Box(Modifier.padding(padding)) {
            AndroidView(factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.userAgentString = "LGKA+/3.0.0"
                    CookieManager.getInstance().removeAllCookies(null)
                    webViewClient = object : WebViewClient() {}
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    loadUrl(url)
                }
            }, modifier = Modifier.fillMaxSize())
            if (progress < 100) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(L.s("loading"),
                             color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// ── Settings sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(nav: NavController, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Text(L.s("settings"), style = MaterialTheme.typography.headlineSmall,
                 fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Text(L.s("settingsSectionAppearance"),
                 style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L.s("appearanceTitle"), Modifier.weight(1f))
                        ThemeModeRow()
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(L.s("accentColor"), Modifier.weight(1f))
                        AccentRow(swatchSize = 26)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(L.s("settingsSectionMore"), style = MaterialTheme.typography.labelSmall,
                 color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(14.dp)) {
                Column {
                    SettingsTile(Icons.Outlined.BugReport, L.s("bugReport")) {
                        onDismiss(); nav.navigate("bugReport")
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    SettingsTile(Icons.Outlined.PrivacyTip, L.s("privacyLabel")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://luka-loehr.github.io/LGKA/privacy.html")))
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    SettingsTile(Icons.Outlined.Info, L.s("legalLabel")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://luka-loehr.github.io/LGKA/impressum.html")))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("© ${java.time.Year.now().value} ",
                     style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text("Luka Löhr", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                     fontWeight = FontWeight.Medium)
                Text(" • v3.0.0", style = MaterialTheme.typography.bodySmall,
                     color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun SettingsTile(icon: androidx.compose.ui.graphics.vector.ImageVector,
                         label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp),
             tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(14.dp),
             tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
    }
}
