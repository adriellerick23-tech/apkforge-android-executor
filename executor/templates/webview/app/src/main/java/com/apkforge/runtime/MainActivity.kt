package com.apkforge.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView

class MainActivity : Activity() {
    private var webView: WebView? = null
    private var showingFallback = false

    private fun showFallback(title: String, detail: String) {
        if (showingFallback) return
        showingFallback = true
        webView?.stopLoading()
        val message = TextView(this).apply {
            setTextColor(Color.rgb(230, 238, 245))
            setBackgroundColor(Color.rgb(5, 6, 8))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            text = "$title\n\n$detail"
        }
        setContentView(message)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val view = WebView(this)
            webView = view
            setContentView(view)
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mediaPlaybackRequiresUserGesture = true
                safeBrowsingEnabled = true
            }
            view.webViewClient = object : WebViewClient() {
                @Suppress("DEPRECATION")
                override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                    showFallback("Não foi possível abrir o app", "Verifique a conexão ou gere o APK novamente.\nCódigo: $errorCode")
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) showFallback("Não foi possível abrir o app", "Verifique a conexão ou gere o APK novamente.\nCódigo: ${error.errorCode}")
                }
            }
            view.webChromeClient = WebChromeClient()
            val mode = BuildConfig.APKFORGE_MODE
            val target = BuildConfig.APKFORGE_URL
            when {
                mode == "local" -> view.loadUrl("file:///android_asset/index.html")
                target.startsWith("https://") -> view.loadUrl(target)
                else -> showFallback("Configuração inválida", "Este APK não recebeu uma URL HTTPS válida.")
            }
        } catch (error: Throwable) {
            showFallback("Falha ao iniciar o app", "O runtime Android encontrou um erro seguro. Gere o APK novamente.\n${error.javaClass.simpleName}")
        }
    }

    @Deprecated("Use the system back dispatcher on newer Android versions")
    override fun onBackPressed() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
