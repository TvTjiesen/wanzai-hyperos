package com.muyao.wanzaihyperos.dev

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * AI 小球调试台（仅 debug 源集，release 不含）。
 *
 * 打开 app/src/debug/assets/grokball/lab.html，里面跑的是 grok-ball 引擎原版
 * （1980 行，MIT，未改一行），因此这里能调的就是引擎自己的全部能力：
 * 32 个表情、6 种动画类型、body/eyes 每个参数、巡演、注视、自旋、撒花、草图风。
 *
 * 为什么用 WebView 而不是 Compose 重画：业主的要求是「保留他的完整特性和动画」，
 * 重画一份就等于第二套实现，两边迟早对不上。调试台先验真，验完再决定移植哪几条。
 */
class AiBallLabActivity : ComponentActivity() {

    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            // file:// 下的脚本默认是 opaque origin，JS 报错会被浏览器遮成
            // 一句 "Script error."（连行号都没有），调试台就没法用了。
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(0xFF1A1917.toInt())
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            loadUrl("file:///android_asset/grokball/lab.html")
        }
        web = view
        setContentView(view)
    }

    override fun onDestroy() {
        web?.apply {
            loadUrl("about:blank")
            destroy()
        }
        web = null
        super.onDestroy()
    }
}
