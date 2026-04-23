package com.htmlwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

object HtmlRenderer {

    fun render(
        ctx: Context,
        uri: String,
        widgetWPx: Int,
        widgetHPx: Int,
        scrollX: Int,
        scrollY: Int,
        zoom: Int,
        delayMs: Long,
        onDone: (Bitmap?) -> Unit
    ) {
        LocationHelper.getLocation(ctx,
            callback = { lat, lon ->
                doRender(ctx, uri, widgetWPx, widgetHPx, scrollX, scrollY, zoom, delayMs, lat, lon, onDone)
            },
            onFail = {
                doRender(ctx, uri, widgetWPx, widgetHPx, scrollX, scrollY, zoom, delayMs, null, null, onDone)
            }
        )
    }

    private fun doRender(
        ctx: Context, uri: String,
        widgetWPx: Int, widgetHPx: Int,
        scrollX: Int, scrollY: Int,
        zoom: Int, delayMs: Long,
        lat: Double?, lon: Double?,
        onDone: (Bitmap?) -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                val scale = zoom / 100f
                val realScrollX = (scrollX / scale).toInt().coerceAtLeast(0)
                val realScrollY = (scrollY / scale).toInt().coerceAtLeast(0)
                val virtualW = (widgetWPx / scale).toInt().coerceAtLeast(100)
                val virtualH = (widgetHPx / scale).toInt().coerceAtLeast(100)
                val totalW = realScrollX + virtualW + 100
                val totalH = realScrollY + virtualH + 100

                val wv = WebView(ctx)
                wv.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(totalW, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(totalH, android.view.View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, totalW, totalH)
                wv.setBackgroundColor(Color.TRANSPARENT)

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = false
                    useWideViewPort = true
                    allowFileAccess = true
                    allowContentAccess = true
                    @Suppress("DEPRECATION") allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    textZoom = 100
                    setGeolocationEnabled(true)
                    setGeolocationDatabasePath(ctx.filesDir.absolutePath)
                }

                // Geolocation izin isteklerini otomatik onayla
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onGeolocationPermissionsShowPrompt(
                        origin: String?,
                        callback: GeolocationPermissions.Callback?
                    ) {
                        callback?.invoke(origin, true, false)
                    }
                }

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {

                        val geoJs = if (lat != null && lon != null)
                            LocationHelper.buildGeolocationJs(lat, lon)
                        else ""

                        val zoomJs = """
                            document.documentElement.style.transformOrigin = '0 0';
                            document.documentElement.style.transform = 'scale(${scale})';
                            document.documentElement.style.width = '${(totalW / scale).toInt()}px';
                        """.trimIndent()

                        Handler(Looper.getMainLooper()).postDelayed({
                            wv.evaluateJavascript("$geoJs\n$zoomJs") {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        wv.scrollTo(realScrollX, realScrollY)
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            try {
                                                val fullBmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
                                                val canvas = Canvas(fullBmp)
                                                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                                                wv.draw(canvas)
                                                wv.destroy()

                                                val cropX = realScrollX.coerceAtLeast(0)
                                                val cropY = realScrollY.coerceAtLeast(0)
                                                val cropW = virtualW.coerceAtMost(totalW - cropX)
                                                val cropH = virtualH.coerceAtMost(totalH - cropY)
                                                val cropped = Bitmap.createBitmap(fullBmp, cropX, cropY, cropW, cropH)
                                                fullBmp.recycle()

                                                val final = Bitmap.createScaledBitmap(cropped, widgetWPx, widgetHPx, true)
                                                if (cropped != final) cropped.recycle()
                                                onDone(final)
                                            } catch (e: Exception) {
                                                try { wv.destroy() } catch (_: Exception) {}
                                                onDone(null)
                                            }
                                        }, 200)
                                    } catch (e: Exception) {
                                        try { wv.destroy() } catch (_: Exception) {}
                                        onDone(null)
                                    }
                                }, delayMs)
                            }
                        }, 400)
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        try { wv.destroy() } catch (_: Exception) {}
                        onDone(null)
                    }
                }

                try {
                    val parsedUri = Uri.parse(uri)
                    val stream = ctx.contentResolver.openInputStream(parsedUri)
                    val html = stream?.bufferedReader()?.readText() ?: ""
                    stream?.close()
                    val basePath = parsedUri.path?.let {
                        val parent = java.io.File(it).parent
                        if (parent != null) "file://$parent/" else null
                    }
                    wv.loadDataWithBaseURL(basePath, html, "text/html", "UTF-8", null)
                } catch (e: Exception) {
                    try { wv.destroy() } catch (_: Exception) {}
                    onDone(null)
                }
            } catch (e: Exception) {
                onDone(null)
            }
        }
    }

    fun dpToPx(ctx: Context, dp: Int): Int =
        (dp * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(50)
}
