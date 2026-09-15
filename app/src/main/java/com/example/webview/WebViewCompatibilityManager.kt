package com.example.webview

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

object WebViewCompatibilityManager {
    private const val TAG = "WebViewCompatManager"

    // Default desktop user agent for desktop mode
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * Android 9 (API 28) requirement:
     * In Android 9+, if an application creates WebViews in multiple processes,
     * each process must specify its own data directory suffix to avoid crashes.
     */
    fun initProcessDataDirectory(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val processName = Application.getProcessName()
                val packageName = context.packageName
                if (processName != packageName) {
                    WebView.setDataDirectorySuffix(processName)
                    Log.d(TAG, "Initialized WebView data directory suffix for process: $processName")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Unable to set data directory suffix", e)
            }
        }
    }

    /**
     * Initializes Safe Browsing if supported by device WebView and OS.
     */
    fun initSafeBrowsing(context: Context) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
            try {
                WebViewCompat.startSafeBrowsing(context) { success ->
                    Log.d(TAG, "Safe Browsing initialized: $success")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to start Safe Browsing", e)
            }
        }
    }

    /**
     * Configures CookieManager for full compatibility with Google Authentication
     * and SPA session management on Android 9 and older devices.
     */
    fun configureCookies(webView: WebView) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            cookieManager.flush()
        } catch (e: Throwable) {
            Log.e(TAG, "Error configuring cookies", e)
        }
    }

    /**
     * Thoroughly configures WebSettings to ensure modern web apps (NotebookLM & Gemini)
     * operate smoothly on Android 9 and legacy device WebViews.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun applyCompatibleSettings(
        webView: WebView,
        isDarkTheme: Boolean,
        isDesktopMode: Boolean
    ) {
        val settings = webView.settings

        // 1. Core Scripting & Storage
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // 2. File & Content Access for Source Uploads
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // 3. Viewport & Scaling
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // 4. Multi-window / Popups for Google Sign-in & Help
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        // 5. Media & Audio playback (NotebookLM Audio Overviews)
        settings.mediaPlaybackRequiresUserGesture = false

        // 6. Mixed Content Mode for asset loading
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // 7. Caching strategy
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 8. User Agent Optimization:
        // Google Sign-In blocks WebView authentication if standard '; wv' is detected.
        // Stripping '; wv' and 'Version/4.0 ' gives seamless Google Account login.
        if (isDesktopMode) {
            settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            val originalUa = WebSettings.getDefaultUserAgent(webView.context)
            val cleanedUa = originalUa
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
            settings.userAgentString = cleanedUa
        }

        // 9. AndroidX WebKit Dark Mode Support (backward compatible)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDarkTheme)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                val mode = if (isDarkTheme) {
                    WebSettingsCompat.FORCE_DARK_ON
                } else {
                    WebSettingsCompat.FORCE_DARK_OFF
                }
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(settings, mode)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Dark mode feature configuration skipped", e)
        }

        // 10. Hardware Rendering Layer Setup with Software fallback for legacy GPUs
        try {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } catch (e: Throwable) {
            Log.w(TAG, "Hardware layer acceleration not available, falling back to software", e)
            try {
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Flushes cookies to persistent storage.
     */
    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to flush cookies", e)
        }
    }

    /**
     * Clears cache, history, and cookies cleanly.
     */
    fun clearCacheAndCookies(webView: WebView?, onCompleted: () -> Unit) {
        try {
            webView?.clearCache(true)
            webView?.clearHistory()
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {
                cookieManager.flush()
                onCompleted()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error clearing cache and cookies", e)
            onCompleted()
        }
    }
}
