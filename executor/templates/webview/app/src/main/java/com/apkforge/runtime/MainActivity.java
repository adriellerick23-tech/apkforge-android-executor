package com.apkforge.runtime;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class MainActivity extends Activity {
    private WebView webView;
    private boolean showingFallback;

    private void showFallback(String title, String detail) {
        if (showingFallback) return;
        showingFallback = true;
        if (webView != null) webView.stopLoading();
        TextView message = new TextView(this);
        message.setTextColor(Color.rgb(230, 238, 245));
        message.setBackgroundColor(Color.rgb(5, 6, 8));
        message.setTextSize(16f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(48, 48, 48, 48);
        message.setText(title + "\n\n" + detail);
        setContentView(message);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            WebView view = new WebView(this);
            webView = view;
            setContentView(view);
            view.getSettings().setJavaScriptEnabled(true);
            view.getSettings().setDomStorageEnabled(true);
            view.getSettings().setDatabaseEnabled(true);
            view.getSettings().setAllowFileAccess(true);
            view.getSettings().setAllowContentAccess(true);
            view.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
            view.getSettings().setSupportMultipleWindows(false);
            view.getSettings().setMediaPlaybackRequiresUserGesture(true);
            view.getSettings().setSafeBrowsingEnabled(true);
            view.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    showFallback("Não foi possível abrir o app", "Verifique a conexão ou gere o APK novamente.\nCódigo: " + errorCode);
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        showFallback("Não foi possível abrir o app", "Verifique a conexão ou gere o APK novamente.\nCódigo: " + error.getErrorCode());
                    }
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                    if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                        showFallback("O site não respondeu", "HTTP " + errorResponse.getStatusCode() + ". Verifique a URL pública e gere o APK novamente.");
                    }
                }

                @Override
                public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    showFallback("O motor do app foi reiniciado", "A WebView encontrou uma falha de renderização. Feche e abra o APK novamente.");
                    return true;
                }
            });
            view.setWebChromeClient(new WebChromeClient());
            String mode = BuildConfig.APKFORGE_MODE;
            String target = BuildConfig.APKFORGE_URL;
            if ("local".equals(mode)) {
                view.loadUrl("file:///android_asset/index.html");
            } else if (target != null && target.startsWith("https://")) {
                view.loadUrl(target);
            } else {
                showFallback("Configuração inválida", "Este APK não recebeu uma URL HTTPS válida.");
            }
        } catch (Throwable error) {
            showFallback("Falha ao iniciar o app", "O runtime Android encontrou um erro seguro. Gere o APK novamente.\n" + error.getClass().getSimpleName());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(new WebViewClient());
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
