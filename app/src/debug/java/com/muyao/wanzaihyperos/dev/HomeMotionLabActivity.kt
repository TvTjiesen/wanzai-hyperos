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
import com.muyao.wanzaihyperos.ui.main.LocalPageEntryGate
import com.muyao.wanzaihyperos.ui.main.LocalPageEntryOwner
import com.muyao.wanzaihyperos.ui.main.PageEntryGate
import com.muyao.wanzaihyperos.ui.main.pages.HomeSectionPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs the actual home list; claiming entry suppresses its automatic school reads. */
class HomeMotionLabActivity : ComponentActivity() {
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
            val gate = remember { PageEntryGate().apply { claim("home", 0L) } }
            LaunchedEffect(Unit) { graph = withContext(Dispatchers.IO) { AppGraph.get(applicationContext) } }
            WanzaiTheme(AppThemeMode.valueOf(UiPrefs.themeModeName)) {
                CompositionLocalProvider(LocalPageEntryGate provides gate, LocalPageEntryOwner provides 0L) {
                    graph?.let { HomeSectionPage(it, {}, {}, {}, {}) }
                }
            }
        }
    }
}
