package com.muyao.wanzaihyperos.dev

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.muyao.wanzaihyperos.app.AppGraph
import com.muyao.wanzaihyperos.data.UiPrefs
import com.muyao.wanzaihyperos.ui.AppThemeMode
import com.muyao.wanzaihyperos.ui.WanzaiTheme
import com.muyao.wanzaihyperos.ui.main.pages.AppearancePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Debug-only emulator harness: production appearance UI, no login or refresh calls. */
class AppearanceLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.HARDWARE !in setOf("ranchu", "goldfish") && !Build.PRODUCT.startsWith("sdk")) {
            finish()
            return
        }
        enableEdgeToEdge()
        UiPrefs.load(this)
        setContent {
            var graph by remember { mutableStateOf<AppGraph?>(null) }
            var theme by remember { mutableStateOf(AppThemeMode.valueOf(UiPrefs.themeModeName)) }
            LaunchedEffect(Unit) { graph = withContext(Dispatchers.IO) { AppGraph.get(applicationContext) } }
            WanzaiTheme(theme) {
                graph?.let { AppearancePage(onClose = { finish() }, graph = it,
                    themeMode = theme, onThemeMode = { next -> theme = next }) }
            }
        }
    }
}
