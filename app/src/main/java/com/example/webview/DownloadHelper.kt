package com.example.webview

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import com.example.R

object DownloadHelper {
    private const val TAG = "DownloadHelper"

    fun handleDownload(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val uri = Uri.parse(url)

            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription(context.getString(R.string.download_started))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                // Pass cookies so authenticated downloads (like NotebookLM exports or audio files) succeed
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) {
                    addRequestHeader("Cookie", cookies)
                }
                if (!userAgent.isNullOrEmpty()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                mimeType?.let { setMimeType(it) }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (downloadManager != null) {
                downloadManager.enqueue(request)
                Toast.makeText(
                    context,
                    "${context.getString(R.string.download_started)}: $fileName",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, "Download manager unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initiating file download", e)
            Toast.makeText(context, "Failed to download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
