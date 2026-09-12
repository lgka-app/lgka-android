package com.lgka

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/// Krankmeldung pre-info — mirrors krankmeldung_info_screen.dart.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KrankmeldungInfoScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val prefs = LocalContainer.current.prefs
    val haptics = rememberHaptics()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.krankmeldung_info_header)) },
            navigationIcon = {
                IconButton(onClick = { haptics.light(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.a11y_back)) }
            })
    }) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).navigationBarsPadding().padding(16.dp)) {
            listOf(Icons.Outlined.WarningAmber to R.string.krankmeldung_disclaimer,
                   Icons.Outlined.SupportAgent to R.string.krankmeldung_contact)
                .forEach { (icon, text) ->
                    Card(shape = CardShape, modifier = Modifier.padding(bottom = 16.dp).semantics(mergeDescendants = true) {}) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(18.dp))
                            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
                                 color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { haptics.medium(); prefs.krankmeldungInfoShown = true; onContinue() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.MedicalServices, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.krankmeldung_button), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/// In-app browser — mirrors webview_screen.dart (progress, error, retry;
/// no cache/cookies like the Flutter incognito settings). It never answers
/// HTTP auth challenges: the school credentials stay inside the API client.
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(url: String, title: String, confineToHost: String? = null, onBack: () -> Unit) {
    val haptics = rememberHaptics()
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
                            // webview_screen parity: external links -> browser
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val target = request?.url ?: return false
                                if (confineToHost != null && !isConfined(target.host, confineToHost)) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, target))
                                    return true
                                }
                                return false
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) failed = true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        }
                        loadUrl(url)
                    }
                }, modifier = Modifier.fillMaxSize())
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

/** Host suffix match: "lgka-online.de" confines to that domain and its subdomains only. */
fun isConfined(host: String?, confined: String): Boolean =
    host != null && (host == confined || host.endsWith(".$confined"))
