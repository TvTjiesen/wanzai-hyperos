package com.muyao.wanzaihyperos.ui.tronclass

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.MultiFormatReader
import com.muyao.wanzaihyperos.data.tronclass.QrDecoder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.Executors

/**
 * 相機掃碼（原生）。
 *
 * ### 為什麼要自己做
 *
 * 學在城院網頁版的掃碼交給**宿主 App**（`window.wx.scanQRCode`，
 * 還有釘釘/飛書/WeLink 等 9 套適配器）——它假設自己被裝在某個 App 裡。
 * 我們的 WebView 沒有 `window.wx`，所以那條路直接失敗。
 *
 * ### 技術選擇
 *
 * - **CameraX**：官方生命週期感知的相機庫，不必自己管 `open/close/release`；
 * - **ZXing**：純 Java 解碼，**不依賴 GMS**（國產機普遍沒裝 Google 服務，
 *   用 Google 的 code-scanner 會在目標機型上直接不可用）；
 * - 取 `YUV_420_888` 的 **Y 平面**做解碼（灰度就夠，省一次轉換）。
 *
 * ### 三種失敗都要有出口（不能讓使用者對著黑屏等）
 *
 * 1. **權限被拒** → 顯示說明 + 重試按鈕（系統第二次問會直接拒，所以要引導去設定）
 * 2. **沒有相機 / 相機被佔用** → 明確文字
 * 3. **一直掃不到** → 使用者可以自己返回，走「手動粘貼」那條路（[RollcallPage] 已支援）
 */
@Composable
fun QrScannerPage(
    onExit: () -> Unit,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    // 解碼結果只回報一次：同一個碼在連續幀裡會重複命中，
    // 不去重的話會疊加多次提交（而簽到是寫操作）。
    var reported by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            errorText = "没有相机权限，无法扫码。可以返回后用「粘贴扫码结果」的方式签到。"
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    // 解碼器只建一次（見 decode 的註釋），生命週期跟著這個頁面。
    val reader = remember { MultiFormatReader() }
    DisposableEffect(Unit) {
        onDispose {
            // 必須關掉：不關執行緒會在每次進出掃碼頁時累積。
            runCatching { executor.shutdown() }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "扫码签到",
                subtitle = "对准二维码",
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "返回",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = errorText ?: "需要相机权限才能扫码",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("授予相机权限") }
                    Text(
                        text = "也可以返回后用「粘贴扫码结果」签到，不需要相机。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = runCatching { providerFuture.get() }.getOrNull()
                            if (provider == null) {
                                errorText = "相机初始化失败（设备可能被占用）"
                                return@addListener
                            }
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                // 只要最新一幀：掃碼不需要排隊處理舊幀，
                                // 否則會越積越多、畫面卡頓且延遲變大。
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                // ⚠️ 用 ResolutionSelector 而不是已廢棄的 setTargetResolution：
                                //   舊 API 在 CameraX 1.4 起標為 deprecated，且與
                                //   ResolutionSelector 同時設定會拋例外。
                                .setResolutionSelector(
                                    androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                                        .setAspectRatioStrategy(
                                            androidx.camera.core.resolutionselector.AspectRatioStrategy
                                                .RATIO_16_9_FALLBACK_AUTO_STRATEGY,
                                        )
                                        .build(),
                                )
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                if (!reported) {
                                    // 解碼邏輯在 QrDecoder（純函式，可單測）——
                                    // 這裡只負責把相機的平面資料交給它。
                                    val text = QrDecoder.decodeWithStride(
                                        buffer = proxy.planes[0].buffer,
                                        width = proxy.width,
                                        height = proxy.height,
                                        rowStride = proxy.planes[0].rowStride,
                                        reader = reader,
                                    )
                                    if (text != null) {
                                        reported = true
                                        onScanned(text)
                                    }
                                }
                                proxy.close()
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }.onFailure { e ->
                                errorText = "相机打不开：${e.javaClass.simpleName}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                errorText?.let { msg ->
                    Text(
                        text = msg,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    )
                }
                Text(
                    text = "对准学在城院的签到二维码",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }
    }
}
