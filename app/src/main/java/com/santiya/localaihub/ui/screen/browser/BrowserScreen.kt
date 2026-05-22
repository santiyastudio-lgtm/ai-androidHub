package com.santiya.localaihub.ui.screen.browser

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santiya.localaihub.browser.BrowserCommand
import com.santiya.localaihub.browser.BrowserToolState
import com.santiya.localaihub.browser.BrowserUrlPolicy
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.components.ActionButton
import com.santiya.localaihub.ui.icons.TnIcons

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateBack: () -> Unit,
) {
    val state by BrowserToolState.state.collectAsStateWithLifecycle()
    val initialUrl = BrowserUrlPolicy.normalizeOrNull(state.currentUrl) ?: "https://www.google.com"
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }

    LaunchedEffect(state.pendingCommandToken) {
        val webView = webViewHolder[0] ?: return@LaunchedEffect
        when (state.pendingCommand) {
            BrowserCommand.GO_BACK -> if (webView.canGoBack()) webView.goBack()
            BrowserCommand.GO_FORWARD -> if (webView.canGoForward()) webView.goForward()
            BrowserCommand.REFRESH -> webView.reload()
            BrowserCommand.NONE -> Unit
        }
        BrowserToolState.clearPendingCommand()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(localizedText("Браузер", "Browser"))
                        Text(
                            text = state.pageTitle ?: state.currentUrl ?: initialUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = localizedText("Назад", "Back")
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Standards.SpacingMd),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    enabled = state.canGoBack,
                    onClick = { BrowserToolState.queueCommand(BrowserCommand.GO_BACK) },
                    label = { Text(localizedText("Назад", "Back")) },
                    leadingIcon = { androidx.compose.material3.Icon(TnIcons.ArrowLeft, null) },
                )
                FilterChip(
                    selected = false,
                    enabled = state.canGoForward,
                    onClick = { BrowserToolState.queueCommand(BrowserCommand.GO_FORWARD) },
                    label = { Text(localizedText("Вперёд", "Forward")) },
                    leadingIcon = { androidx.compose.material3.Icon(TnIcons.ArrowRight, null) },
                )
                FilterChip(
                    selected = false,
                    onClick = { BrowserToolState.queueCommand(BrowserCommand.REFRESH) },
                    label = { Text(localizedText("Обновить", "Refresh")) },
                    leadingIcon = { androidx.compose.material3.Icon(TnIcons.Refresh, null) },
                )
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewHolder[0] = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return BrowserUrlPolicy.normalizeOrNull(request?.url?.toString()) == null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                BrowserToolState.updatePageState(
                                    url = BrowserUrlPolicy.normalizeOrNull(url),
                                    title = view?.title,
                                    canGoBack = view?.canGoBack() == true,
                                    canGoForward = view?.canGoForward() == true
                                )
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                super.onReceivedTitle(view, title)
                                BrowserToolState.updatePageState(
                                    url = BrowserUrlPolicy.normalizeOrNull(view?.url),
                                    title = title,
                                    canGoBack = view?.canGoBack() == true,
                                    canGoForward = view?.canGoForward() == true
                                )
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
                update = { webView ->
                    webViewHolder[0] = webView
                    val desiredUrl = BrowserUrlPolicy.normalizeOrNull(state.currentUrl)
                    if (!desiredUrl.isNullOrBlank() && desiredUrl != webView.url) {
                        webView.loadUrl(desiredUrl)
                    }
                }
            )
        }
    }
}
