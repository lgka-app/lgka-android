package com.lgka

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lgka.api.covers
import lgka.api.knownClass
import lgka.api.normalizeClass
import lgka.api.pagerIndex
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Space left and right of a page at fit width, and between pages. The custom plan's reveal lands on these. */
internal val PdfPageMargin = 8.dp
internal val PdfPageSpacing = 12.dp

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
/** A sharper page is at most this many pixels (~32 MB), however far it is zoomed. */
private const val MAX_PAGE_PIXELS = 8_000_000f

@Composable
fun PdfViewerDialog(request: PdfRequest, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PdfViewer(request, onClose)
    }
}

/**
 * The PDF viewer (iOS PdfViewerScreen parity): close, title and share in the top bar; a timetable one class
 * page at a time (swipe for the next), every other PDF as one vertical scroll of pages at fit width. Pinch and
 * double-tap zoom follow the fingers every frame and the pages are rendered again, sharper, once the zoom
 * settles. Schedule PDFs get the class bar, which validates against the class index and jumps to the class
 * page (switching to the other schedule PDF when the class lives there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewer(request: PdfRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = LocalContainer.current.prefs
    val vm = LocalHomeViewModel.current
    var currentFile by remember { mutableStateOf(request.file) }
    var currentTitle by remember { mutableStateOf(request.title) }
    var currentSchedule by remember { mutableStateOf(request.schedule) }
    var currentIndex by remember { mutableStateOf(request.classIndex) }
    var pages by remember { mutableStateOf<PdfPages?>(null) }
    var openFailed by remember { mutableStateOf(false) }
    var classInput by remember { mutableStateOf("") }
    var showClassBar by remember { mutableStateOf(false) }
    val toast = rememberToastState()
    val haptics = rememberHaptics()
    val pagerState = rememberPagerState { pages?.pageCount ?: 0 }
    val listState = rememberLazyListState()
    val zoom = remember { PdfZoom() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val connectionFailed = stringResource(R.string.server_connection_failed)
    val errorLoading = stringResource(R.string.error_loading)
    val isSchedule = currentSchedule != null

    BackHandler(onBack = onClose)

    suspend fun loadPdf(file: File, targetPage: Int?) {
        val opened = withContext(Dispatchers.IO) { PdfPages(file) }
        val previous = pages
        pages = opened
        zoom.reset()
        targetPage?.let { page -> pagerState.scrollToPage(pagerIndex(page, opened.pageCount)) }
        // off the main thread: close() waits for a page that is still rendering
        withContext(Dispatchers.IO) { previous?.close() }
    }

    LaunchedEffect(request.file) {
        try {
            loadPdf(request.file, request.targetPage)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            openFailed = true // not a readable PDF: an uncaught exception here would crash the app
        }
    }
    DisposableEffect(Unit) { onDispose { pages?.close() } }
    LaunchedEffect(showClassBar) { if (showClassBar) focusRequester.requestFocus() }
    // sharper pages once the fingers let go: rendered for the zoom that stayed for a quarter second
    LaunchedEffect(zoom) {
        snapshotFlow { zoom.scale }.collectLatest { scale ->
            delay(250)
            zoom.renderScale = scale
        }
    }

    fun applyClass(cls: String, page: Int) {
        prefs.selectedScheduleClass = cls
        val name = classDisplayName(resources, cls)
        currentTitle = name
        classInput = ""
        showClassBar = false
        zoom.reset()
        scope.launch { pagerState.scrollToPage(pagerIndex(page, pages?.pageCount ?: 1)) }
        haptics.success()
        toast.show(resources.getString(R.string.class_changed, name))
    }

    /** _validateAndSaveClass parity: unknown class → "existiert nicht", known → persist, jump, confirm. */
    fun notFound(q: String) { haptics.error(); toast.show(resources.getString(R.string.no_results, q.uppercase())) }
    fun submitClass() {
        val q = normalizeClass(classInput)
        if (q.length < 2) return
        haptics.medium()
        // same rule as the home class dialog: only classes in the timetable's class index
        if (knownClass(q, vm.preferredGroup) == null) { notFound(q); return }
        val current = currentSchedule
        if (current != null && !current.covers(q)) {
            // cross-PDF class switching (_navigateCrossPdf parity): the PDF whose class index / grades contain the class
            val other = vm.preferredGroup.firstOrNull { it.covers(q) }
            val pdf = other?.pdf
            if (other == null || pdf == null) { notFound(q); return }
            scope.launch {
                try {
                    val file = vm.pdfFile(pdf.sha256, pdf.url)
                    val index = other.classIndex
                    val page = index[q]
                    if (page == null) { notFound(q); return@launch }
                    loadPdf(file, page) // first: a PDF that fails to open must not replace the current one
                    currentFile = file
                    currentSchedule = other
                    currentIndex = index
                    applyClass(q, page)
                } catch (e: Exception) {
                    haptics.error(); toast.show(connectionFailed)
                }
            }
            return
        }
        val page = currentIndex[q]
        if (page == null) { notFound(q); return }
        applyClass(q, page)
    }

    fun share() {
        // pdf_share_service parity: friendly filename
        val prefix = if (currentSchedule != null) "LGKA_Stundenplan_" else "LGKA_Vertretungsplan_"
        val safe = currentTitle.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
        val shareFile = File(context.cacheDir, request.shareName
            ?.let { "LGKA_" + it.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') + ".pdf" }
            ?: (prefix + safe.ifEmpty { "Plan" } + ".pdf"))
        val source = currentFile
        scope.launch {
            try {
                withContext(Dispatchers.IO) { source.copyTo(shareFile, overwrite = true) }
            } catch (e: IOException) {
                // a sync replaced the plan while it was open and removed the old PDF
                haptics.error(); toast.show(errorLoading)
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "com.lgka.files", shareFile)
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, resources.getString(R.string.share_pdf)))
        }
    }

    val background = MaterialTheme.colorScheme.background
    Surface(Modifier.fillMaxSize(), color = background) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = background,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                        navigationIcon = {
                            IconButton(onClick = { haptics.light(); onClose() }, modifier = Modifier.testTag("pdf.close")) {
                                Icon(Icons.Filled.Close, stringResource(R.string.a11y_close))
                            }
                        },
                        actions = {
                            if (isSchedule) {
                                IconButton(onClick = { haptics.light(); showClassBar = !showClassBar }, modifier = Modifier.testTag("pdf.changeClass")) {
                                    Icon(if (showClassBar) Icons.Filled.Close else Icons.Outlined.School, stringResource(R.string.a11y_change_class))
                                }
                            }
                            IconButton(onClick = { haptics.light(); share() }, modifier = Modifier.testTag("pdf.share")) {
                                Icon(Icons.Filled.Share, stringResource(R.string.a11y_share))
                            }
                        })
                }) { padding ->
                Column(Modifier.padding(top = padding.calculateTopPadding())) {
                    AnimatedVisibility(showClassBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        // pdf_search_bar.dart parity: class entry in a floating card, submit on ≥ 2 characters
                        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 4.dp),
                            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 2.dp) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = classInput, onValueChange = { classInput = it },
                                    placeholder = { Text(stringResource(R.string.search_hint)) },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = { submitClass() }),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("pdf.classInput"))
                                Spacer(Modifier.width(10.dp))
                                Button(onClick = { submitClass() }, enabled = normalizeClass(classInput).length >= 2,
                                    modifier = Modifier.testTag("pdf.classSubmit")) { Text(stringResource(R.string.set_class_button)) }
                            }
                        }
                    }
                    val p = pages
                    if (p == null) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (openFailed) Text(stringResource(R.string.error_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Loading()
                        }
                    } else {
                        PdfPagesView(p, paged = isSchedule, zoom = zoom, pagerState = pagerState, listState = listState)
                    }
                }
            }
            ToastHost(toast)
        }
    }
}

/** The pages under one zoom: pinch and pan move a layer every frame, the pages re-render when it settles. */
@Composable
private fun PdfPagesView(pages: PdfPages, paged: Boolean, zoom: PdfZoom,
                         pagerState: androidx.compose.foundation.pager.PagerState,
                         listState: androidx.compose.foundation.lazy.LazyListState) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        zoom.viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        val margin = with(androidx.compose.ui.platform.LocalDensity.current) { PdfPageMargin.roundToPx() }
        val pageWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(1)
        Box(Modifier.fillMaxSize().zoomGestures(zoom, paged, scope)) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offsetX
                translationY = zoom.offsetY
            }) {
                // no stretch at the ends: a drag that the pages do not use pans the zoomed layer instead
                CompositionLocalProvider(LocalOverscrollFactory provides null) {
                    if (paged) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { it },
                            userScrollEnabled = zoom.scale <= 1.001f) { index ->
                            Box(Modifier.fillMaxSize().padding(horizontal = PdfPageMargin, vertical = PdfPageSpacing),
                                contentAlignment = Alignment.Center) {
                                PdfPage(pages, index, pageWidth, zoom)
                            }
                        }
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = PdfPageMargin, vertical = PdfPageSpacing),
                            // a single short page (the custom plan) sits in the middle, as in PDFKit
                            verticalArrangement = Arrangement.spacedBy(PdfPageSpacing, Alignment.CenterVertically)) {
                            items(pages.pageCount) { index -> PdfPage(pages, index, pageWidth, zoom) }
                        }
                    }
                }
            }
        }
        if (pages.pageCount > 1) {
            val current by remember(paged) {
                derivedStateOf {
                    if (paged) pagerState.currentPage
                    else {
                        val info = listState.layoutInfo
                        val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
                        info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index ?: 0
                    }
                }
            }
            PageIndicator(current, pages.pageCount,
                scrolling = if (paged) pagerState.isScrollInProgress else listState.isScrollInProgress,
                modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** "2 / 5" while scrolling, fading out a moment after the pages stop. */
@Composable
private fun PageIndicator(page: Int, count: Int, scrolling: Boolean, modifier: Modifier) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(page, scrolling) {
        visible = true
        if (!scrolling) { delay(1500); visible = false }
    }
    val label = stringResource(R.string.a11y_page, page + 1)
    AnimatedVisibility(visible, modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp), enter = fadeIn(), exit = fadeOut()) {
        Text("${page + 1} / $count", style = MaterialTheme.typography.labelLarge, color = Color.White,
            modifier = Modifier.semantics { contentDescription = label }
                .background(Color.Black.copy(alpha = 0.55f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun PdfPage(pages: PdfPages, index: Int, pageWidth: Int, zoom: PdfZoom) {
    val ratio = pages.aspectRatios.getOrNull(index) ?: 0.7f
    // at display resolution, sharper for the settled zoom; capped so a zoomed page stays a few dozen MB
    val widthPx = (pageWidth * zoom.renderScale).coerceAtMost(sqrt(MAX_PAGE_PIXELS * ratio)).roundToInt().coerceAtLeast(1)
    var bitmap by remember(pages, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(pages, index, widthPx) {
        // the previous rendering stays on screen until the sharper one is ready
        withContext(Dispatchers.IO) { runCatching { pages.render(index, widthPx) }.getOrNull() }?.let { bitmap = it }
    }
    val label = stringResource(R.string.a11y_page, index + 1)
    Box(Modifier.fillMaxWidth().aspectRatio(ratio).shadow(3.dp, RoundedCornerShape(2.dp)).background(Color.White)) {
        bitmap?.let {
            Image(it.asImageBitmap(), label, modifier = Modifier.fillMaxSize().testTag("plan.page"),
                contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        }
    }
}

/** Zoom of the page layer, in screen pixels: scaled around its top-left corner, then moved by the offset. */
@Stable
private class PdfZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set
    var offsetY by mutableFloatStateOf(0f)
        private set
    /** The zoom the pages were last rendered for. */
    var renderScale by mutableFloatStateOf(1f)
    var viewport = IntSize.Zero

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    /** Never smaller than the screen, never beyond its edges. */
    private fun clamp() {
        offsetX = offsetX.coerceIn(-viewport.width * (scale - 1f), 0f)
        offsetY = offsetY.coerceIn(-viewport.height * (scale - 1f), 0f)
    }

    fun zoomBy(factor: Float, focus: Offset) = zoomTo(scale * factor, focus, offsetX, offsetY, scale)

    fun panBy(x: Float, y: Float) {
        offsetX += x
        offsetY += y
        clamp()
    }

    /** Keeps the content under [focus] where it is. */
    private fun zoomTo(target: Float, focus: Offset, fromX: Float, fromY: Float, fromScale: Float) {
        val new = target.coerceIn(1f, MAX_ZOOM)
        val contentX = (focus.x - fromX) / fromScale
        val contentY = (focus.y - fromY) / fromScale
        scale = new
        offsetX = focus.x - contentX * new
        offsetY = focus.y - contentY * new
        clamp()
    }

    suspend fun animateDoubleTap(focus: Offset) {
        val fromScale = scale
        val fromX = offsetX
        val fromY = offsetY
        val target = if (fromScale > 1.05f) 1f else DOUBLE_TAP_ZOOM
        animate(0f, 1f, animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)) { t, _ ->
            zoomTo(lerp(fromScale, target, t), focus, fromX, fromY, fromScale)
        }
    }
}

private fun Modifier.zoomGestures(zoom: PdfZoom, paged: Boolean, scope: kotlinx.coroutines.CoroutineScope): Modifier = this
    .pointerInput(zoom) {
        detectTapGestures(onDoubleTap = { position -> scope.launch { zoom.animateDoubleTap(position) } })
    }
    .pointerInput(zoom) {
        // two fingers: pinch before the pages see it (they must not scroll meanwhile)
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    val factor = event.calculateZoom()
                    val pan = event.calculatePan()
                    zoom.zoomBy(factor, event.calculateCentroid(useCurrent = true))
                    zoom.panBy(pan.x, pan.y)
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
    .pointerInput(zoom, paged) {
        // one finger on a zoomed page: sideways always pans; up and down pans what the page list did not scroll
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val change = event.changes.singleOrNull { it.pressed }
                if (change != null && zoom.scale > 1.001f) {
                    val delta = change.positionChange()
                    val consumed = change.isConsumed
                    if (paged || !consumed) zoom.panBy(delta.x, delta.y) else zoom.panBy(delta.x, 0f)
                    change.consume()
                }
            } while (event.changes.any { it.pressed })
        }
    }
