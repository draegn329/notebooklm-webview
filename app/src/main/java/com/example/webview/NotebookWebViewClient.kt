package com.example.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class NotebookWebViewClient(
    private val context: Context,
    private val onPageLoadingChanged: (Boolean) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val onErrorReceived: (isError: Boolean, errorMessage: String?) -> Unit,
    private val onSslErrorState: (hasSslIssue: Boolean) -> Unit
) : WebViewClient() {

    companion object {
        private const val TAG = "NotebookWebViewClient"

        // Domains that should always stay inside the internal WebView
        private val INTERNAL_DOMAINS = listOf(
            "notebooklm.google.com",
            "gemini.google.com",
            "accounts.google.com",
            "myaccount.google.com",
            "google.com",
            "google.co",
            "gstatic.com",
            "googleapis.com",
            "googleusercontent.com"
        )
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageLoadingChanged(true)
        onErrorReceived(false, null)
        onSslErrorState(false)
        url?.let { onUrlChanged(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        onPageLoadingChanged(false)
        url?.let { onUrlChanged(it) }
        // Ensure cookies (auth tokens) are flushed to storage on older devices
        WebViewCompatibilityManager.flushCookies()
    }

    /**
     * Modern URL override handling (Android 7.0+ / API 24+)
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return handleUrlNavigation(view, uri)
    }

    /**
     * Legacy URL override handling for older Android runtimes
     */
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = Uri.parse(url)
        return handleUrlNavigation(view, uri)
    }

    private fun handleUrlNavigation(view: WebView?, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false

        // Handle custom schemes: mailto, tel, sms, market, intent
        if (scheme != "http" && scheme != "https") {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No app available to handle custom scheme: $scheme", e)
                return true
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling uri scheme: $scheme", e)
                return true
            }
        }

        // Web URLs: check if domain is internal Google / NotebookLM
        val host = uri.host?.lowercase() ?: ""
        val isInternal = INTERNAL_DOMAINS.any { host == it || host.endsWith(".$it") }

        return if (isInternal) {
            // Stay inside the WebView
            false
        } else {
            // Let the WebView load it or open externally if desired.
            // For NotebookLM, users often open reference links. Keeping navigation smooth inside WebView:
            false
        }
    }

    /**
     * Modern error handling (API 23+)
     */
    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // Only trigger the error screen if the main page failed to load, not a minor sub-resource
        if (request?.isForMainFrame == true) {
            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                error?.description?.toString() ?: "Connection failed"
            } else {
                "Connection failed"
            }
            Log.e(TAG, "Main frame load error: $description for ${request.url}")
            onPageLoadingChanged(false)
            onErrorReceived(true, description)
        }
    }

    /**
     * Legacy error handling for older Android runtimes
     */
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        super.onReceivedError(view, errorCode, description, failingUrl)
        Log.e(TAG, "Legacy load error: $errorCode - $description on $failingUrl")
        onPageLoadingChanged(false)
        onErrorReceived(true, description ?: "Connection error ($errorCode)")
    }

    /**
     * SSL Error handling: alerts UI to certificate inconsistencies
     */
    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler?,
        error: SslError?
    ) {
        Log.w(TAG, "SSL Error received: ${error?.primaryError}")
        onSslErrorState(true)
        // By default, for security compliance, we do not bypass SSL errors automatically,
        // but notify UI to inform user
        super.onReceivedSslError(view, handler, error)
    }
}
