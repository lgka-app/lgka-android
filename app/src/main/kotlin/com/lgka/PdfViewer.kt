package com.lgka

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Page renderer with lazy, width-fitted bitmaps: only visible pages are
 * rasterized, at most [cacheSize] bitmaps stay in memory, and the underlying
 * PdfRenderer (not thread-safe) is serialized behind a mutex.
 */
class PdfPages(file: File, private val cacheSize: Int = 6) : AutoCloseable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    private val cache = object : LruCache<Int, Bitmap>(cacheSize) {}
    val pageCount: Int = renderer.pageCount
    /** width/height ratios so placeholders reserve the right space. */
    val aspectRatios: List<Float> = (0 until pageCount).map { i ->
        renderer.openPage(i).use { it.width.toFloat() / it.height.toFloat() }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap = mutex.withLock {
        cache.get(index)?.takeIf { it.width == widthPx }?.let { return it }
        val bmp = renderer.openPage(index).use { page ->
            val scale = widthPx.toFloat() / page.width
            val target = createBitmap(widthPx, (page.height * scale).toInt().coerceAtLeast(1))
            target.eraseColor(android.graphics.Color.WHITE)
            page.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            target
        }
        cache.put(index, bmp)
        bmp
    }

    override fun close() {
        cache.evictAll()
        renderer.close()
        fd.close()
    }
}

@Composable
fun PdfViewerDialog(request: PdfRequest, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) { PdfViewerContent(request, onClose) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfViewerContent(request: PdfRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val api = LocalContainer.current.api
    val prefs = LocalContainer.current.prefs
    val vm = LocalHomeViewModel.current
    var currentFile by remember { mutableStateOf(request.file) }
    var currentTitle by remember { mutableStateOf(request.title) }
    var currentGrade by remember { mutableStateOf(request.gradeLevel) }
    var pages by remember { mutableStateOf<PdfPages?>(null) }
    var pageTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val noMatches = stringResource(R.string.no_matches)
    val connectionFailed = stringResource(R.string.server_connection_failed)

    suspend fun loadPdf(file: File, targetPage: Int?) {
        val opened = withContext(Dispatchers.IO) { PdfPages(file) }
        pages?.close()
        pages = opened
        pageTexts = withContext(Dispatchers.IO) { runCatching { pageTextsAndroid(file) }.getOrDefault(emptyList()) }
        targetPage?.let { display -> listState.scrollToItem((display - 2).coerceIn(0, opened.pageCount - 1)) }
    }

    LaunchedEffect(request.file) { loadPdf(request.file, request.targetPage) }
    DisposableEffect(Unit) { onDispose { pages?.close() } }

    fun search() {
        val q = query.trim().lowercase()
        feedback = null
        val isClass = q.matches(Regex("^(j1[12]|\\d{1,2}[a-e])$"))
        if (currentGrade != null && isClass) {
            prefs.selectedScheduleClass = q
            val targetGroup = if (q.startsWith("j")) "J11/J12" else "Klassen 5-10"
            if (targetGroup != currentGrade) {
                // cross-PDF class switching (_navigateCrossPdf parity)
                val other = vm.preferredGroup.firstOrNull { it.gradeLevel == targetGroup }
                if (other != null) {
                    scope.launch {
                        try {
                            val (file, index) = api.schedulePdf(other)
                            currentFile = file
                            currentGrade = targetGroup
                            val name = resources.getString(classNameRes(q), q.replaceFirstChar { it.uppercase() })
                            currentTitle = name
                            loadPdf(file, index[q])
                            feedback = resources.getString(R.string.class_changed, name)
                        } catch (e: Exception) {
                            feedback = connectionFailed
                        }
                    }
                    return
                }
            }
        }
        matches = pageTexts.withIndex().filter { q.isNotEmpty() && it.value.contains(q) }.map { it.index }
        matchIndex = 0
        if (matches.isEmpty() && q.isNotEmpty()) {
            feedback = if (isClass) resources.getString(R.string.no_results, q.uppercase()) else noMatches
        }
        matches.firstOrNull()?.let { scope.launch { listState.animateScrollToItem(it) } }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(currentTitle, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.a11y_close)) }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Filled.Search, stringResource(R.string.a11y_search))
                }
                IconButton(onClick = {
                    // pdf_share_service parity: friendly filename
                    val prefix = if (currentGrade != null) "LGKA_Stundenplan_" else "LGKA_Vertretungsplan_"
                    val safe = currentTitle.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
                    val shareFile = File(context.cacheDir, prefix + safe.ifEmpty { "Plan" } + ".pdf")
                    currentFile.copyTo(shareFile, overwrite = true)
                    val uri = FileProvider.getUriForFile(context, "com.lgka.files", shareFile)
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }, resources.getString(R.string.share_pdf)))
                }) { Icon(Icons.Filled.Share, stringResource(R.string.a11y_share)) }
            })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            if (showSearch) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_in_pdf)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        singleLine = true, modifier = Modifier.weight(1f))
                    if (matches.isNotEmpty()) {
                        val position = stringResource(R.string.a11y_match_position, matchIndex + 1, matches.size)
                        Text("  ${matchIndex + 1}/${matches.size}  ", style = MaterialTheme.typography.labelLarge,
                             modifier = Modifier.semantics { contentDescription = position })
                        IconButton(onClick = {
                            matchIndex = (matchIndex - 1 + matches.size) % matches.size
                            scope.launch { listState.animateScrollToItem(matches[matchIndex]) }
                        }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.a11y_previous_match)) }
                        IconButton(onClick = {
                            matchIndex = (matchIndex + 1) % matches.size
                            scope.launch { listState.animateScrollToItem(matches[matchIndex]) }
                        }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.a11y_next_match)) }
                    } else {
                        IconButton(onClick = { search() }) { Icon(Icons.Filled.Search, stringResource(R.string.a11y_search)) }
                    }
                }
            }
            feedback?.let { fb ->
                Text(fb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val p = pages
            if (p == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Loading() }
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = constraints.maxWidth
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, panDelta, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    pan = if (scale > 1f) pan + panDelta else Offset.Zero
                                }
                            }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y)) {
                        items(p.pageCount, key = { it }) { index -> PdfPage(p, index, widthPx) }
                    }
                }
            }
        }
    }
}

private fun classNameRes(cls: String): Int = when (cls) {
    "j11" -> R.string.jahrgang11
    "j12" -> R.string.jahrgang12
    else -> R.string.class_name
}

@Composable
private fun PdfPage(pages: PdfPages, index: Int, widthPx: Int) {
    val bitmap by produceState<Bitmap?>(null, pages, index, widthPx) {
        value = withContext(Dispatchers.IO) { runCatching { pages.render(index, widthPx) }.getOrNull() }
    }
    val label = stringResource(R.string.a11y_page, index + 1)
    val ratio = pages.aspectRatios.getOrNull(index) ?: 0.7f
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).padding(bottom = 4.dp)) {
        bitmap?.let { Image(it.asImageBitmap(), label, modifier = Modifier.fillMaxSize().testTag("plan.page")) }
    }
}
