package com.lgka

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import lgka.ScheduleGrades
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
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

/**
 * pdf_viewer_screen.dart parity: one page at a time (swipe for the next), share,
 * and for schedule PDFs a class selector behind the school icon that validates
 * against the class index and jumps to the class page (switching to the other
 * schedule PDF when the class lives there). Substitution plans get no search.
 */
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
    var currentSchedule by remember { mutableStateOf(request.schedule) }
    var currentIndex by remember { mutableStateOf(request.classIndex) }
    var pages by remember { mutableStateOf<PdfPages?>(null) }
    var classInput by remember { mutableStateOf("") }
    var showClassBar by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val pagerState = rememberPagerState { pages?.pageCount ?: 0 }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val connectionFailed = stringResource(R.string.server_connection_failed)
    val isSchedule = currentSchedule != null

    suspend fun loadPdf(file: File, targetPage: Int?) {
        val opened = withContext(Dispatchers.IO) { PdfPages(file) }
        pages?.close()
        pages = opened
        targetPage?.let { display -> pagerState.scrollToPage((display - 2).coerceIn(0, opened.pageCount - 1)) }
    }

    LaunchedEffect(request.file) { loadPdf(request.file, request.targetPage) }
    DisposableEffect(Unit) { onDispose { pages?.close() } }
    LaunchedEffect(feedback) { if (feedback != null) { delay(2_000); feedback = null } }
    LaunchedEffect(showClassBar) { if (showClassBar) focusRequester.requestFocus() }

    fun applyClass(cls: String, page: Int) {
        prefs.selectedScheduleClass = cls
        val name = classDisplayName(resources, cls)
        currentTitle = name
        classInput = ""
        showClassBar = false
        scope.launch { pagerState.scrollToPage((page - 2).coerceIn(0, (pages?.pageCount ?: 1) - 1)) }
        feedback = resources.getString(R.string.class_changed, name)
    }

    /** _validateAndSaveClass parity: unknown class → "existiert nicht", known → persist, jump, confirm. */
    fun submitClass() {
        val q = classInput.trim().lowercase()
        if (q.length < 2) return
        if (!ScheduleGrades.isClassToken(q)) { feedback = resources.getString(R.string.no_results, q.uppercase()); return }
        val current = currentSchedule
        if (current != null && !current.covers(q)) {
            // cross-PDF class switching (_navigateCrossPdf parity): the PDF whose grades contain the class
            val other = vm.preferredGroup.firstOrNull { it.covers(q) }
            if (other == null) { feedback = resources.getString(R.string.no_results, q.uppercase()); return }
            scope.launch {
                try {
                    val (file, index) = api.schedulePdf(other)
                    val page = index[q]
                    if (page == null) { feedback = resources.getString(R.string.no_results, q.uppercase()); return@launch }
                    currentFile = file
                    currentSchedule = other
                    currentIndex = index
                    loadPdf(file, page)
                    applyClass(q, page)
                } catch (e: Exception) {
                    feedback = connectionFailed
                }
            }
            return
        }
        val page = currentIndex[q]
        if (page == null) { feedback = resources.getString(R.string.no_results, q.uppercase()); return }
        applyClass(q, page)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(currentTitle, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.a11y_close)) }
            },
            actions = {
                if (isSchedule) {
                    IconButton(onClick = { showClassBar = !showClassBar }, modifier = Modifier.testTag("pdf.changeClass")) {
                        Icon(if (showClassBar) Icons.Filled.Close else Icons.Outlined.School, stringResource(R.string.a11y_change_class))
                    }
                }
                IconButton(onClick = {
                    // pdf_share_service parity: friendly filename
                    val prefix = if (currentSchedule != null) "LGKA_Stundenplan_" else "LGKA_Vertretungsplan_"
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
            AnimatedVisibility(showClassBar) {
                // pdf_search_bar.dart parity: class entry, submit on ≥ 2 characters
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = classInput, onValueChange = { classInput = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submitClass() }),
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("pdf.classInput"))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { submitClass() }, enabled = classInput.trim().length >= 2,
                           modifier = Modifier.testTag("pdf.classSubmit")) { Text(stringResource(R.string.set_class_button)) }
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
                    // pdfx PdfView parity: one page at a time, swipe horizontally
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it }) { index ->
                        ZoomablePage { PdfPage(p, index, widthPx) }
                    }
                }
            }
        }
    }
}

/** Pinch-to-zoom for a single page; resets when the page leaves the pager. */
@Composable
private fun ZoomablePage(content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, panDelta, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    pan = if (scale > 1f) pan + panDelta else Offset.Zero
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y),
        contentAlignment = Alignment.TopCenter) { content() }
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
