package com.muyao.wanzaihyperos.ui.home

import android.content.Intent
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.muyao.wanzaihyperos.ui.design.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollMotionUiTest {
    @Test fun actualLazyListTrailsReversesSettlesAndHonorsReducedMotion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent().setClassName(context,
            "com.muyao.wanzaihyperos.dev.AppearanceLabActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ComponentActivity
        val ready = CompletableFuture<Unit>()
        lateinit var list: LazyListState
        lateinit var entrance: SpringListEntrance
        lateinit var scope: CoroutineScope
        val enabled = mutableStateOf(true)
        instrumentation.runOnMainSync {
            activity.setContent {
                CompositionLocalProvider(LocalMotionPreview provides MotionFlags(animationsEnabled = enabled.value)) {
                    val state = rememberLazyListState()
                    val spring = rememberSpringListEntrance(state)
                    val coroutineScope = rememberCoroutineScope()
                    SideEffect { list = state; entrance = spring; scope = coroutineScope; ready.complete(Unit) }
                    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items((0..30).toList(), key = { it }) { key ->
                            Box(Modifier.fillMaxWidth().height(100.dp).springListEntrance(spring, key).background(Color.Gray))
                        }
                    }
                }
            }
        }
        ready.get(10, TimeUnit.SECONDS)
        fun scroll(step: Float): Float {
            val done = CompletableFuture<Float>()
            instrumentation.runOnMainSync {
                scope.launch {
                    var observed = 0f
                    list.scroll(MutatePriority.UserInput) {
                        repeat(18) {
                            scrollBy(step)
                            delay(20)
                            if (kotlin.math.abs(entrance.lag) > kotlin.math.abs(observed)) observed = entrance.lag
                        }
                    }
                    done.complete(observed)
                }
            }
            return done.get(10, TimeUnit.SECONDS)
        }
        assertTrue("Real list scroll must displace drawing", scroll(20f) > 1f)
        assertTrue("Reverse scroll must reverse displacement", scroll(-15f) < -1f)
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < deadline && entrance.lag != 0f) SystemClock.sleep(25)
        assertEquals("Spring must return to its original position", 0f, entrance.lag, .05f)
        instrumentation.runOnMainSync { enabled.value = false }
        SystemClock.sleep(100)
        assertEquals("Reduced motion must disable trail", 0f, scroll(20f), .05f)
    }
}
