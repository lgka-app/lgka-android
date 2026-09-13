package com.lgka

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.viewinterop.AndroidView
import lgka.api.isSchoolHost

/// In-app browser — mirrors webview_screen.dart (progress, error, retry;
/// no cache/cookies like the Flutter incognito settings). It answers a Basic
/// Auth challenge with the stored school credentials for the school's own host
/// only ([isSchoolHost]); every other host is cancelled, so the credentials
/// never leave the school domain.
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(url: String, title: String, onBack: () -> Unit) {
    val haptics = rememberHaptics()
    val credentials = LocalContainer.current.credentials
    var progress by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var reloadToken by remember { mutableIntStateOf(0) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.Filled.Close, stringResource(R.string.a11y_close)) }
            })
    }) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding())) {
            // A new WebView per reload token: no reload storms on recomposition.
            key(reloadToken) {
                AndroidView(factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        settings.userAgentString = AppInfo.userAgent
                        CookieManager.getInstance().removeAllCookies(null)
                        webViewClient = object : WebViewClient() {
                            // mailto:, tel: and other non-web links (the legal notice has an address) go to
                            // the system, like LocalUriHandler in MainNav; the WebView would show an error page.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return false
                                if (!request.isForMainFrame || target.scheme == "http" || target.scheme == "https") return false
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                                return true
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) failed = true
                            }
                            override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler, host: String?, realm: String?) {
                                val login = if (isSchoolHost(host)) credentials.load() else null
                                if (login != null) handler.proceed(login.user, login.password) else handler.cancel()
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        loadUrl(url)
                    }
                }, modifier = Modifier.fillMaxSize(),
                    // a detached WebView keeps its renderer and JS timers alive until destroyed
                    onRelease = { it.destroy() })
            }
            if (failed) {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(32.dp),
                       horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.WifiOff, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.form_load_error), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.form_load_error_hint), style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { haptics.light(); failed = false; progress = 0; reloadToken++ }) { Text(stringResource(R.string.try_again)) }
                }
            } else if (progress < 100) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Loading()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
