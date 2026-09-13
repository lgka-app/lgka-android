package com.lgka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures

/** Full-screen photo viewer: pinch and double-tap zoom on a black stage. */
@Composable
fun PhotoViewerDialog(url: String, alt: String, onClose: () -> Unit) {
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
