package com.lgka

import android.content.Intent
import android.graphics.Bitmap
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
import lgka.api.covers
import lgka.api.knownClass
import lgka.api.normalizeClass
import lgka.api.pagerIndex
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val connectionFailed = stringResource(R.string.server_connection_failed)
    val errorLoading = stringResource(R.string.error_loading)
    val isSchedule = currentSchedule != null

    suspend fun loadPdf(file: File, targetPage: Int?) {
        val opened = withContext(Dispatchers.IO) { PdfPages(file) }
        val previous = pages
        pages = opened
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

    fun applyClass(cls: String, page: Int) {
        prefs.selectedScheduleClass = cls
        val name = classDisplayName(resources, cls)
        currentTitle = name
        classInput = ""
        showClassBar = false
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

    Box(Modifier.fillMaxSize()) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(currentTitle, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onClose() }) { Icon(Icons.Filled.Close, stringResource(R.string.a11y_close)) }
            },
            actions = {
                if (isSchedule) {
                    IconButton(onClick = { haptics.light(); showClassBar = !showClassBar }, modifier = Modifier.testTag("pdf.changeClass")) {
                        Icon(if (showClassBar) Icons.Filled.Close else Icons.Outlined.School, stringResource(R.string.a11y_change_class))
                    }
                }
                IconButton(onClick = {
                    haptics.light()
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
                }) { Icon(Icons.Filled.Share, stringResource(R.string.a11y_share)) }
            })
    }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
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
                    Button(onClick = { submitClass() }, enabled = normalizeClass(classInput).length >= 2,
                           modifier = Modifier.testTag("pdf.classSubmit")) { Text(stringResource(R.string.set_class_button)) }
                }
            }
            val p = pages
            if (p == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (openFailed) Text(stringResource(R.string.error_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Loading()
                }
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
    ToastHost(toast)
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
        contentAlignment = Alignment.Center) { content() }
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
