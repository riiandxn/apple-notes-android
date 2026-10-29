package com.yerni.applenotes

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible

/**
 * Apple Notes untuk Android.
 *
 * Apple tidak menyediakan API publik untuk Notes, jadi aplikasi ini memakai
 * iCloud Notes resmi (icloud.com/notes) di dalam WebView. Login Apple ID,
 * verifikasi dua langkah, dan sinkronisasi semuanya ditangani langsung oleh
 * Apple; aplikasi ini tidak pernah melihat atau menyimpan kata sandi.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var offlineView: View

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var defaultUserAgent: String = ""

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)
        offlineView = findViewById(R.id.offlineView)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<Button>(R.id.retryButton).setOnClickListener { reload() }
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { showMenu(it) }

        setupWebView()

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            loadNotes()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Form login Apple ID dimuat dari idmsa.apple.com di dalam iframe.
            setAcceptThirdPartyCookies(webView, true)
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Penanda "; wv" dan "Version/4.0" membuat sebagian situs menganggap
            // ini WebView yang tidak didukung; hapus agar terlihat seperti Chrome.
            defaultUserAgent = userAgentString
                .replace("; wv)", ")")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
        }
        applyDesktopMode(prefs.getBoolean(KEY_DESKTOP, false))

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && isAppleHost(uri.host)) return false
                // Tautan di luar iCloud/Apple (misalnya link di dalam catatan) dibuka di browser.
                openExternal(uri)
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.isVisible = true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.isVisible = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    offlineView.isVisible = true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.isVisible = newProgress < 100
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun loadNotes() {
        offlineView.isVisible = false
        webView.loadUrl(NOTES_URL)
    }

    private fun reload() {
        if (!isOnline()) {
            offlineView.isVisible = true
            return
        }
        offlineView.isVisible = false
        if (webView.url.isNullOrEmpty()) loadNotes() else webView.reload()
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_RELOAD, 0, R.string.menu_reload)
        popup.menu.add(0, MENU_DESKTOP, 1, R.string.menu_desktop).apply {
            isCheckable = true
            isChecked = prefs.getBoolean(KEY_DESKTOP, false)
        }
        popup.menu.add(0, MENU_LOGOUT, 2, R.string.menu_logout)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RELOAD -> reload()
                MENU_DESKTOP -> {
                    val enabled = !item.isChecked
                    prefs.edit().putBoolean(KEY_DESKTOP, enabled).apply()
                    applyDesktopMode(enabled)
                    reload()
                }
                MENU_LOGOUT -> confirmLogout()
            }
            true
        }
        popup.show()
    }

    /** iCloud web tampil lebih lengkap (sidebar folder) di mode desktop, terutama di tablet. */
    private fun applyDesktopMode(enabled: Boolean) {
        with(webView.settings) {
            userAgentString = if (enabled) desktopUserAgent() else defaultUserAgent
            useWideViewPort = enabled
            loadWithOverviewMode = enabled
        }
    }

    private fun desktopUserAgent(): String {
        val chrome = Regex("Chrome/[\\d.]+").find(defaultUserAgent)?.value ?: "Chrome/130.0.0.0"
        return "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.logout_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            webView.clearCache(true)
            webView.clearHistory()
            Toast.makeText(this, R.string.logout_done, Toast.LENGTH_SHORT).show()
            loadNotes()
        }
    }

    private fun downloadFile(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme != "https") {
            openExternal(uri)
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(uri)
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        getSystemService(DownloadManager::class.java).enqueue(request)
        Toast.makeText(this, fileName, Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isAppleHost(host: String?): Boolean {
        if (host == null) return false
        return APPLE_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val NOTES_URL = "https://www.icloud.com/notes/"
        private const val KEY_DESKTOP = "desktop_mode"
        private const val MENU_RELOAD = 1
        private const val MENU_DESKTOP = 2
        private const val MENU_LOGOUT = 3

        private val APPLE_DOMAINS = listOf(
            "icloud.com",
            "icloud.com.cn",
            "apple.com",
            "icloud-content.com",
            "cdn-apple.com",
            "apple-cloudkit.com",
            "apple-mapkit.com",
        )
    }
}
