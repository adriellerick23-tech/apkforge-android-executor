package com.apkforge.runtime

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()
        val target = if (BuildConfig.APKFORGE_MODE == "local") "file:///android_asset/index.html" else BuildConfig.APKFORGE_URL
        webView.loadUrl(target)
        setContentView(webView)
    }
}
