package com.muyao.wanzaihyperos.ui.tronclass

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.muyao.wanzaihyperos.ui.design.BodyMark
import com.muyao.wanzaihyperos.ui.design.MarkIcon
import java.util.concurrent.atomic.AtomicBoolean
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * ### 同一台相机，两个地方用
 *
 * 签到页里它是「扫码签到」；应用页的扫一扫里它是通用扫码。
 * 标题 / 副标题 / 提示 / 拒权文案因此都是参数 —— 写死的话，
 * 从扫一扫进来会读到「扫码签到 · 对准学在城院的签到二维码」，
 * 手里拿着活动报名码的人会被这句话骗。
 * 默认值就是签到页那一套，所以签到页的调用点一个字都不用改。
 */
@Composable
fun QrScannerPage(
    onExit: () -> Unit,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "扫码签到",
    subtitle: String = "对准二维码",
    hint: String = "对准学在城院的签到二维码",
    noPermissionText: String = "没有相机权限，无法扫码。可以返回后用粘贴的方式签到，或者从相册里选一张图。",
    fromGalleryText: String = "从相册选一张",
    /**
     * 進來就先把相冊打開。
     *
     * 掃一掃的首屏讓使用者選「相機 / 相冊」；選了相冊就不該讓他自己進到相機裡
     * 再去找底下那顆按鈕 —— 那等於首屏白選一次。默認 false，簽到頁那條路不變。
     */
    autoOpenGallery: Boolean = false,
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
    // 分析流實際給的是多大一帧，只在第一帧記一次（自檢用，見 ANALYSIS_RESOLUTION）。
    val loggedSize = remember { AtomicBoolean(false) }

    // 扫了一会儿还没对上时，补一句能照着做的话——用状态而不是每帧计数，避免每帧重组。
    var slow by remember { mutableStateOf(false) }
    // 相册那张图正在认（几千万像素的图缩完再解码要几百毫秒）。
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            errorText = noPermissionText
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                picking = true
                val text = withContext(Dispatchers.IO) {
                    runCatching { decodePickedImage(context, uri) }.getOrNull()
                }
                picking = false
                if (text != null) {
                    reported = true
                    onScanned(text)
                } else {
                    errorText = "这张照片里没找到二维码。换一张把码拍正、拍清楚的。"
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    // 首屏選了「相冊」就直接把選圖器拉起來：不必進到相機裡再找底下那顆按鈕。
    // 只跑一次（Unit）；使用者取消選圖就退回相機，相冊那顆按鈕仍在。
    LaunchedEffect(autoOpenGallery) {
        if (autoOpenGallery) {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    // 页面上没有任何「在动」的东西时，人分不清是「还在找」还是「卡死了」。
    LaunchedEffect(hasPermission, reported) {
        if (!hasPermission || reported) return@LaunchedEffect
        delay(SLOW_HINT_MS)
        slow = true
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

    // 頂欄走共享殼（[SecondaryScaffold]）：漸變、安全區、返回鍵、欄高只由設計層決定一次。
    // ⚠️ 取景框**不鋪到頂欄底下**：頂欄這一行有標題與返回鍵，
    //   壓在取景畫面上既看不清、也會讓返回鍵看起來像浮在相機裡。
    com.muyao.wanzaihyperos.ui.design.SecondaryScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onExit,
        modifier = modifier,
        // 這一頁沒有清單：取景是實時的視頻流，沒有「滾動進度」這種東西。
        onRefresh = null,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 區塊圖標：這一支講的是「出事了」，用 Warn（不是 Info）。
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        BodyMark(
                            icon = MarkIcon.Warn,
                            tint = MiuixTheme.colorScheme.error,
                            size = com.muyao.wanzaihyperos.ui.design.Mark.size,
                        )
                        Text(
                            text = errorText ?: "需要相机权限才能扫码",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    Button(
                        onClick = { launcher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("授予相机权限") }
                    Text(
                        text = "也可以返回后用“粘贴扫码结果”签到，不需要相机。",
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
                                        .setResolutionStrategy(ANALYSIS_RESOLUTION)
                                        .build(),
                                )
                                .build()
                            analysis.setAnalyzer(executor) { proxy ->
                                if (!reported) {
                                    // 分辨率是这条链路唯一「看不见」的变量：拿不到 1080p 时
                                    // 症状和「解码器坏了」一模一样。留一行可查的事实，
                                    // 免得下次又靠猜（只在自检开关打开时写）。
                                    if (loggedSize.compareAndSet(false, true)) {
                                        if (com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning.debugLog) {
                                            android.util.Log.i(
                                                "wz-scan",
                                                "analysis frame ${proxy.width}x${proxy.height} " +
                                                    "rowStride=${proxy.planes[0].rowStride}",
                                            )
                                        }
                                    }
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
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 64.dp),
                    )
                }
                // 取景畫面上那行提示：**加一枚 Scan 圖標**。
                // 這一頁沒有清單、沒有區塊標題，這行提示就是它唯一的「區塊」——
                // 一枚掃描圖標讓人一眼看出「這裡在找碼」，而不用先讀完那行字。
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BodyMark(
                        icon = MarkIcon.Scan,
                        tint = MiuixTheme.colorScheme.primary,
                        size = com.muyao.wanzaihyperos.ui.design.Mark.size,
                    )
                    Text(
                        text = hint,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (slow && !reported && !picking) {
                    Text(
                        text = "还没对上。让码占满中间，或者从相册里选一张。",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp),
                    )
                }
            }
            // 这条路不需要相机权限：拒了权限的人也还能把已经拍下来的码认出来。
            Button(
                onClick = {
                    errorText = null
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            ) {
                Text(if (picking) "正在认这张图…" else fromGalleryText)
            }
        }
    }
}

/**
 * 從相簿選到的圖裡找二維碼（讀圖、縮放、轉灰度都在這裡）。
 *
 * 為什麼要自己縮圖：相簿裡的照片常有幾千萬像素，整張轉成 int 陣列會直接 OOM。
 * 縮到長邊 [MAX_PICKED_PX] 之後，一張佔畫面一半的碼仍有 800px 上下，模組遠大於解碼下限。
 */
internal fun decodePickedImage(context: android.content.Context, uri: Uri): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // 先試小的，認不出再回到大的重試——縮圖會吃掉模組邊界，見 QrDecoder.sampleLadder。
    for (sample in QrDecoder.sampleLadder(bounds.outWidth, bounds.outHeight)) {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val found = try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) {
                null
            } else {
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                QrDecoder.decodeArgb(pixels, w, h, MultiFormatReader())
            }
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
        if (found != null) return found
    }
    return null
}

/** 扫了这么多毫秒还没对上，就补一句能照着做的话。 */
private const val SLOW_HINT_MS = 4_000L

/**
 * 要哪一档分析分辨率。
 *
 * ### 为什么必须显式指定（这是「怎么扫都没反应」的真正原因）
 *
 * CameraX 的 `ImageAnalysis` 在不指定时的默认值是 `SizeUtil.RESOLUTION_VGA` +
 * `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`，落到 16:9 上就是 **960×540** 左右。
 * 二维码能不能解，看的不是帧多大，而是**每个模块还剩几个像素**。
 *
 * 实测（同一张真照片反复缩放，见 QrDecoder 的注释）：
 *
 * | 帧 | 码占画面宽 12% | 15% | 18% |
 * |---|---|---|---|
 * | 960×540 | 模组 2.2px ✗ | 2.7px ✗ | 3.2px ~ |
 * | 1920×1080 | 4.3px ✓ | 5.4px ✓ | 6.5px ✓ |
 *
 * 而人把手机举起来对准投影幕时，码通常只占画面宽一兩成 —— 教室那台电脑离得远，
 * 码在大屏上本来就只占角落一块。所以 VGA 这条默认值让「码明明在画面里」变成了
 * 「一直没反应」。
 *
 * ### 为什么是 1080p 而不是更高
 *
 * 解码耗时随像素线性涨，但**没码**的那一帧要把四趟跑满才放弃（最坏情形）：
 * 1080p 约 7ms、4K 约 300ms。1080p 既够大（占宽 10% 的码也有 3.6px/模块），
 * 又留得住 30fps —— 4K 会在没对准时把帧全部丢掉，反而更难扫上。
 *
 * 取 `CLOSEST_LOWER_THEN_HIGHER`：宁可低一档也别越界，某些机型报的 1080p
 * 实际只能用 720p，这条规则会自己退，不会抛。
 */
private val ANALYSIS_RESOLUTION =
    androidx.camera.core.resolutionselector.ResolutionStrategy(
        android.util.Size(1920, 1080),
        androidx.camera.core.resolutionselector.ResolutionStrategy
            .FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
    )
