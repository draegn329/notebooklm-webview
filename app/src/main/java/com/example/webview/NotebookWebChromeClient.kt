package com.example.webview

import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class NotebookWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onTitleChangedCallback: (String) -> Unit,
    private val onOpenFileChooser: (ValueCallback<Array<Uri>>?, FileChooserParams?) -> Boolean,
    private val onRequestAudioPermission: (PermissionRequest) -> Unit
) : WebChromeClient() {

    companion object {
        private const val TAG = "NotebookWebChrome"
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleChangedCallback(it) }
    }

    /**
     * Essential for NotebookLM: allows users to upload PDF, DOCX, TXT, MD,
     * and audio files as sources.
     */
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return onOpenFileChooser(filePathCallback, fileChooserParams)
    }

    /**
     * Handles web permissions (e.g. microphone / WebRTC for Gemini & NotebookLM voice features)
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val requestedResources = request.resources
        val hasAudio = requestedResources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }

        if (hasAudio) {
            onRequestAudioPermission(request)
        } else {
            try {
                request.grant(request.resources)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed granting web permissions", e)
            }
        }
    }

    /**
     * Handles popups/multi-window (e.g. Google Sign-in popups, Help pages).
     */
    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        if (view == null || resultMsg == null) return false

        // Extract the popup transport and route to current webview or fetch the URL
        try {
            val transport = resultMsg.obj as? WebView.WebViewTransport
            if (transport != null) {
                // Route new window target to the main webview
                transport.webView = view
                resultMsg.sendToTarget()
                return true
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error handling popup window", e)
        }
        return false
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        // Geolocation is generally not needed by NotebookLM; safe fallback
        callback?.invoke(origin, false, false)
    }
}
