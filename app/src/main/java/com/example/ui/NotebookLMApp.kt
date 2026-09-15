package com.example.ui

import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.webview.DownloadHelper
import com.example.webview.NotebookWebChromeClient
import com.example.webview.NotebookWebViewClient
import com.example.webview.WebViewCompatibilityManager

private const val NOTEBOOKLM_URL = "https://notebooklm.google.com/"

@Composable
fun NotebookLMApp(
    onOpenFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Boolean,
    onRequestAudioPermission: (PermissionRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = isSystemInDarkTheme()

    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Intercept back button to navigate within NotebookLM history
    BackHandler(enabled = canGoBack) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        }
    }

    // Update settings when dark theme changes
    LaunchedEffect(isDarkTheme, webViewInstance) {
        webViewInstance?.let { webView ->
            WebViewCompatibilityManager.applyCompatibleSettings(
                webView = webView,
                isDarkTheme = isDarkTheme,
                isDesktopMode = false
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Pure full-screen NotebookLM WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this

                    // Configure persistent cookies for Google Authentication
                    WebViewCompatibilityManager.configureCookies(this)

                    // Apply WebSettings compatible with Android 9 and legacy device WebViews
                    WebViewCompatibilityManager.applyCompatibleSettings(
                        webView = this,
                        isDarkTheme = isDarkTheme,
                        isDesktopMode = false
                    )

                    // Backward-compatible WebViewClient
                    webViewClient = NotebookWebViewClient(
                        context = ctx,
                        onPageLoadingChanged = { loading ->
                            isLoading = loading
                            canGoBack = canGoBack()
                            canGoForward = canGoForward()
                        },
                        onUrlChanged = { _ ->
                            canGoBack = canGoBack()
                            canGoForward = canGoForward()
                        },
                        onErrorReceived = { errorOccurred, desc ->
                            isError = errorOccurred
                            errorMessage = desc
                        },
                        onSslErrorState = { _ -> }
                    )

                    // Backward-compatible WebChromeClient
                    webChromeClient = NotebookWebChromeClient(
                        onProgressChangedCallback = { newProgress ->
                            progress = newProgress
                            if (newProgress >= 100) {
                                isLoading = false
                            }
                            canGoBack = canGoBack()
                            canGoForward = canGoForward()
                        },
                        onTitleChangedCallback = { _ -> },
                        onOpenFileChooser = { callback, params ->
                            onOpenFileChooser(callback, params)
                        },
                        onRequestAudioPermission = { request ->
                            onRequestAudioPermission(request)
                        }
                    )

                    // DownloadListener for exports and generated Audio Overviews
                    setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
                        DownloadHelper.handleDownload(
                            context = ctx,
                            url = downloadUrl,
                            userAgent = userAgent,
                            contentDisposition = contentDisposition,
                            mimeType = mimetype
                        )
                    }

                    // Direct load of NotebookLM
                    loadUrl(NOTEBOOKLM_URL)
                }
            },
            update = { webView ->
                canGoBack = webView.canGoBack()
                canGoForward = webView.canGoForward()
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("notebook_webview")
        )

        // Subtle thin progress bar during loading
        val animatedProgress by animateFloatAsState(
            targetValue = if (isLoading) (progress / 100f).coerceIn(0.05f, 1f) else 0f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "progress_animation"
        )

        AnimatedVisibility(
            visible = isLoading && progress < 100,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        }

        // Connection Error Overlay with Retry option
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connection_error_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = stringResource(R.string.connection_error_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = errorMessage ?: stringResource(R.string.connection_error_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    isError = false
                                    webViewInstance?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }

    // Clean up WebView resources on disposal
    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.apply {
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
        }
    }
}
