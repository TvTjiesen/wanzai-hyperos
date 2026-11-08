package com.muyao.wanzaihyperos.ui.design

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 三条"用户已经表过态"的开关，集中一处读、一处通知。
 *
 * ## 为什么要有这个文件（业主 2026-10-20 批准的打磨清单第 1、2 条）
 *
 * 之前全工程**一行都没读**这些，于是玻璃出事故时界面没有任何一条**有设计的**退路：
 *
 * - **动画时长缩放**：开发者选项拉到 0，系统动画全没了，**但 Compose 不自动尊重它**
 *   （`animateFloatAsState` / `AnimatedVisibility` 照跑）。用户明确说了"别动"，我们还在动。
 *   判据用官方的 [ValueAnimator.areAnimatorsEnabled]。
 * - **高对比文字**：`AccessibilityManager.isHighTextContrastEnabled`。开着它的人要的是
 *   "字和底分得开"，而半透明玻璃正好反着来。
 * - **减弱透明度**：⚠️ **Android 没有这个系统开关**（那是 iOS/macOS 的）。所以它只能是
 *   我们自己设置页里的一个开关（存 `UiPrefs`）。这里说实话、不写"跟随系统"，
 *   因为系统里根本没有这个东西可跟。
 *
 * ## 形状为什么长这样
 *
 * 照 [com.muyao.wanzaihyperos.ui.design.liquid.GlassTuning] 那条已经跑通的通道：
 * `@Volatile` 字段 + `refresh(context)` + `ContentObserver`，**不另起一套**。
 * 同一件事有两套监听通道，迟早一套是死的（那条通道的坑写在 `GlassTuning` 档头）。
 */
object MotionPrefs {

    /** 系统「动画时长缩放」的原始读数（不带应用内覆盖）。 */
    @Volatile
    var systemAnimationsEnabled = true
        private set

    /** 系统「高对比文字」的原始读数（不带应用内覆盖）。 */
    @Volatile
    var systemHighTextContrast = false
        private set

    /**
     * 应用内「减少动态」（返工总单 C4）。
     *
     * 以前只有系统信号，稿要的是应用内自己那条 ⇒ 现在两条是**或**关系：
     * 系统关了动不了，应用内关了同样动不了。
     */
    @Volatile
    var reduceMotion = false
        private set

    /** 应用内「高对比显示」，与系统高对比是**或**关系。 */
    @Volatile
    var highContrastOverride = false
        private set

    /** 对外口径不变：能不能动 = 系统允许 **且** 应用内没关。 */
    val animationsEnabled: Boolean
        get() = systemAnimationsEnabled && !reduceMotion

    /** 对外口径不变：要高对比 = 系统开了 **或** 应用内开了。 */
    val highTextContrast: Boolean
        get() = systemHighTextContrast || highContrastOverride

    fun setReduceMotion(on: Boolean) {
        if (reduceMotion == on) return
        reduceMotion = on
        version++
    }

    fun setHighContrastOverride(on: Boolean) {
        if (highContrastOverride == on) return
        highContrastOverride = on
        version++
    }

    /** 由设置页写入（也写进 [com.muyao.wanzaihyperos.data.UiPrefs]），不是系统来的。 */
    @Volatile
    var reduceTransparency = false
        private set

    /**
     * 改「减弱透明度」。
     *
     * ⚠️ 不许在界面里直接赋值：那条 `@Volatile` 字段**不参与重组**，
     * 直接改只会让盘里的值变、玻璃照旧 —— 用户看到的是「开关动了，界面没动」。
     * 必须在这里顺带自增 [version]，把 `rememberMotion()` 的订阅者叫醒。
     */
    fun setReduceTransparency(on: Boolean) {
        if (reduceTransparency == on) return
        reduceTransparency = on
        version++
    }

    /** 这三条里任何一条变了，都要让界面重新组合一次。 */
    /**
     * 玻璃要不要**折射**（lens）。`false` = 只糊不折，就是设置页那格「磨砂」。
     *
     * 为什么单独一条，不并进 [reduceTransparency]：那条是无障碍信号（关掉后连采样层都不挂，
     * 见 T67），这条只是材质取向（仍然透光、仍然糊，只是不要那道把背后内容往外弯的棱）。
     * 读它必须经过 [version]，否则改了界面不会重组。
     */
    var refractsGlass = true
        private set

    fun setRefractsGlass(on: Boolean) {
        if (refractsGlass == on) return
        refractsGlass = on
        version++
    }

    var version by mutableStateOf(0)
        private set

    /** 这块面**能不能**是半透明的。任何一条说不，就老实画实的。 */
    val glassIsTranslucent: Boolean
        get() = !reduceTransparency && !highTextContrast

    /** @return 这一次有没有**真的**改变（理由和 `GlassTuning.refresh` 一样：无条件自增会害死重组）。 */
    fun refresh(context: Context): Boolean {
        val animators = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        // ⚠️ `AccessibilityManager.isHighTextContrastEnabled` 是 API 34 才有的 getter，
        //   我们这个包 minSdk 33 —— 编不过，而且**反射拿一个"可能不存在的方法"比读设置表更糟**
        //   （它返回 null 时我们会当成"没开"，静默）。这条开关真正的落盘位置就是 Secure 表。
        // ⚠️ **读不到 = 没开**。这个键在绝大多数机器上根本不存在（HyperOS 上实测就是 null），
        //   当时图省事复用了触感那条的"读不到当开着"，结果**高对比被永久判成开**
        //   ⇒ `glassIsTranslucent` 永远 false ⇒ 底栏/顶栏/卡片全部变实心。
        //   业主原话：「你妈个逼我液态玻璃底栏没了」。两条开关的"缺失默认值"语义相反，
        //   所以这里必须显式传，不能共用一个默认。
        val contrast = secureFlag(context, "accessibility_high_text_contrast_enabled", whenMissing = false)
        // 比较与赋值都用**系统原始值**：应用内覆盖不归这里管，混进来会让
        // 「系统没变但应用内开关动了」被误判成「系统变了」。
        val changed = animators != systemAnimationsEnabled || contrast != systemHighTextContrast
        systemAnimationsEnabled = animators
        systemHighTextContrast = contrast
        if (changed) version++
        // ★ 只在可调试包把这四个值写进 logcat。默认值写错的时候（2026-10-20 那次
        //   高对比被判成"永久开着"，整片玻璃变实心）界面看起来只是"风格变了"，
        //   不会报错也不会崩 —— 没有这行日志，我只能靠截屏猜，而截屏要用他的手机。
        //   有了它，`adb logcat -s wz-motion` 一条命令就能读到真相，不打扰他。
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            android.util.Log.i(
                "wz-motion",
                "anim=$animators contrast=$contrast reduce=$reduceTransparency " +
                    "translucent=$glassIsTranslucent",
            )
        }
        return changed
    }

    /**
     * 开始盯着这两条系统值。
     *
     * ⚠️ `ANIMATOR_DURATION_SCALE` 在 `global` 表、`HIGH_TEXT_CONTRAST` 在 `accessibility` 表，
     * 两个 URI 都要注册：只注册一个的话，用户改了另一个界面不动，而我们**看不出来它没动**
     * （`GlassTuning` 那份注释里"读到过值不等于这一版跑过"就是同一类骗）。
     */
    fun startObserving(context: Context): () -> Unit {
        val resolver = context.contentResolver
        val handler = Handler(Looper.getMainLooper())
        // 两个 URI 都要注册：动画缩放在 global 表、高对比在 secure 表。
        // 只注册一个的话，用户改了另一个界面不动，而我们**看不出来它没动**。
        val observers = listOf(
            Settings.Global.getUriFor("animator_duration_scale"),
            Settings.Secure.getUriFor("accessibility_high_text_contrast_enabled"),
        ).map { uri ->
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    refresh(context)
                }
            }.also { resolver.registerContentObserver(uri, false, it) }
        }
        return { observers.forEach { resolver.unregisterContentObserver(it) } }
    }
}

/**
 * 界面侧读的那一份。
 *
 * 为什么经过 [version] 再算而不是直接读 `@Volatile`：直接读全局对象**不会触发重组**，
 * 用户改完设置界面不动，量出来的数就是假的（`GlassTuning` 档头那条警告对这里同样成立）。
 */
@Composable
fun rememberMotion(): MotionFlags {
    MotionPrefs.version   // ← 读它就是为了订阅
    return MotionFlags(
        animationsEnabled = MotionPrefs.animationsEnabled,
        reduceTransparency = MotionPrefs.reduceTransparency,
        highContrast = MotionPrefs.highTextContrast,
        refractsGlass = MotionPrefs.refractsGlass,
    )
}

/**
 * 三条信号的快照。
 *
 * ⚠️ **不叫 `Motion`**：`ui/design/DesignTokens.kt` 里已经有一个 `object Motion`
 *   （时长令牌：PRESS_MS/FAST_MS/…）。两个同名声明在同一个包里会直接编译不过，
 *   而且就算换了包，"Motion" 这个词在一个工程里指两样东西也是坑。
 */
data class MotionFlags(
    /** false = 用户在系统里把动画关掉了 ⇒ 只允许淡入淡出，不允许位移/弹簧。 */
    val animationsEnabled: Boolean = true,
    /** true = 玻璃一律退成"近实底 + 明确描边"，不做模糊与折射。 */
    val reduceTransparency: Boolean = false,
    /** true = 系统开了高对比文字 ⇒ 同上。 */
    val highContrast: Boolean = false,
    /** true = 玻璃带折射（液态）；false = 只糊不折（设置页那格「磨砂」）。 */
    val refractsGlass: Boolean = true,
) {
    /** 这块面能不能是半透明的。 */
    val glassIsTranslucent: Boolean get() = !reduceTransparency && !highContrast

    /** 位移/弹簧类动效能不能用（淡入淡出仍然允许）。 */
    val canMove: Boolean get() = animationsEnabled
}

/**
 * 触觉能不能发。
 *
 * 关掉系统触感的人不该被我们"偷偷震动"。`View.performHapticFeedback` 自己会看
 * `isHapticFeedbackEnabled`，但那只覆盖 View 层；这里额外读系统设置，是为了**在触发点就不发**，
 * 而不是发出去被吞掉 —— 否则以后"为什么这里没反馈"又要从头查一遍。
 */
/** 读一个 0/1 的开关。**读不到就当"开着"**（宁可给反馈，不要因为拿不到值就静默关掉）。 */
/**
 * 读一个 0/1 的 Secure 开关。
 *
 * ⚠️ [whenMissing] **必须由调用方显式给**，因为这两条的"键不存在"含义正好相反：
 * - `accessibility_high_text_contrast_enabled`：绝大多数 ROM 上根本没这个键 ⇒ **没开**（false）。
 *   给它 true 就等于把所有无障碍降级永久打开 —— 玻璃会整片消失（2026-10-20 真机踩过）。
 * - `haptics_feedback`：读不到 ⇒ 按系统默认**有触感**处理（true），宁可给反馈，
 *   不要因为拿不到值就静默关掉一条本该有的反馈。
 */
private fun secureFlag(context: Context, name: String, whenMissing: Boolean): Boolean = runCatching {
    Settings.Secure.getInt(context.contentResolver, name) != 0
}.getOrDefault(whenMissing)

@Composable
fun rememberHapticsEnabled(context: Context): Boolean {
    MotionPrefs.version   // 同上：让它能被重新评估
    return secureFlag(context, "haptics_feedback", whenMissing = true)
}
