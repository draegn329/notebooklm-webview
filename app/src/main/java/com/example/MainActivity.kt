package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.ui.NotebookLMApp
import com.example.ui.theme.MyApplicationTheme
import com.example.webview.WebViewCompatibilityManager

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingAudioRequest: PermissionRequest? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var audioPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize multi-process data directory for Android 9 (API 28) and safe browsing
        WebViewCompatibilityManager.initProcessDataDirectory(this)
        WebViewCompatibilityManager.initSafeBrowsing(this)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register file chooser launcher for NotebookLM source uploads (PDF, Docs, Markdown, Audio)
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (fileUploadCallback == null) return@registerForActivityResult

            var results: Array<Uri>? = null
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data
                val dataString = data?.dataString
                val clipData = data?.clipData

                if (clipData != null) {
                    val count = clipData.itemCount
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until count) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                    results = uris.toTypedArray()
                } else if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                } else if (data?.data != null) {
                    results = arrayOf(data.data!!)
                }
            }

            // Crucial: Must always call onReceiveValue (even with null) to prevent WebView deadlock
            fileUploadCallback?.onReceiveValue(results)
            fileUploadCallback = null
        }

        // Register audio permission launcher for Gemini & NotebookLM voice/recording features
        audioPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            val request = pendingAudioRequest
            pendingAudioRequest = null
            if (request != null) {
                if (isGranted) {
                    try {
                        request.grant(request.resources)
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error granting audio permission to webview", e)
                    }
                } else {
                    request.deny()
                    Toast.makeText(
                        this,
                        "Microphone permission is required for voice features",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        setContent {
            MyApplicationTheme {
                NotebookLMApp(
                    onOpenFileChooser = { callback, fileChooserParams ->
                        handleOpenFileChooser(callback, fileChooserParams)
                    },
                    onRequestAudioPermission = { request ->
                        handleRequestAudioPermission(request)
                    }
                )
            }
        }
    }

    private fun handleOpenFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        // Cancel any pending callback
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = callback

        return try {
            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }

            // Ensure multiple file selection is supported if requested
            if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            fileChooserLauncher.launch(
                Intent.createChooser(intent, getString(R.string.file_upload_title))
            )
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to launch file chooser", e)
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
            false
        }
    }

    private fun handleRequestAudioPermission(request: PermissionRequest) {
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            try {
                request.grant(request.resources)
            } catch (e: Throwable) {
                Log.e(TAG, "Error granting audio permission", e)
            }
        } else {
            pendingAudioRequest = request
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        // Guarantee cookies and authentication state are written to persistent storage
        WebViewCompatibilityManager.flushCookies()
    }
}

