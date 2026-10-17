package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 娑叉厠鐜荤拑瀹瑰櫒銆? *
 * 鍏夊灞ゆ锛堢敱涓嬪埌涓婏級鈥斺€?閫欐槸閫嗗悜 com.linxin.demo 鐨?LxBottomBar 瀛稿埌鐨勶細
 *
 *   1. vibrancy()          澧為＝鍜岋紝璁撹儗寰岀殑椤忚壊鏇存績锛堢幓鐠冩墠鏈夈€岄鑹层€嶏級
 *   2. blur(radius)        妯＄硦 2~12dp锛?*涓嶆槸** 30+锛? *   3. lens(h, amount)     閭婄罚鎶樺皠 鈥斺€?娑叉厠鐜荤拑鐨勯潏榄? *   4. Highlight           閭婄罚楂樺厜
 *   5. Shadow              澶栭櫚褰憋紙璁撶幓鐠冩诞璧蜂締锛? *   6. InnerShadow         鍏ч櫚褰憋紙璁撶幓鐠冩湁鍘氬害锛夆槄 鎴戠涓€鐗堝畬鍏ㄦ矑鍋? *   7. onDrawSurface       琛ㄩ潰搴曡壊锛堢背鐧?34% 娣?/ 16% 娣憋級
 *
 * 灏戜换浣曚竴灞ら兘涓嶅儚鐜荤拑锛? * - 娌掓湁 lens 鈫?鍙槸纾ㄧ爞鏉? * - 娌掓湁 InnerShadow 鈫?鍍忓崐閫忔槑璨肩礄锛屾矑鏈夊帤搴? * - 娌掓湁 vibrancy 鈫?鑳屽緦鐨勯鑹茬櫦鐏? *
 * 鐢ㄦ硶蹇呴爤鏄叐娈靛紡锛堣 [GlassBackdropHost]锛夛紝鍚﹀墖鎶樺皠鍒扮┖鐧姐€? *
 * @param backdrop 鎶樺皠渚嗘簮銆俷ull 鈫?鑷嫊闄嶇礆鎴愬蹇冿紙涓嶆渻璁婄伆濉婏級銆? * @param material 鏉愯唱銆係OLID 涓嶈蛋鐜荤拑銆? * @param pressedScale 鎸変笅鏅傜殑鏀惧ぇ鍊嶇巼锛堟粦濉婄敤 1.39锛岃 LxGlass.PillPressedScale锛夈€? * @param pressFraction 0~1 鐨勬寜涓嬮€插害锛岄﹨鍕曟姌灏勯噺鑸囧収闄板奖銆? */
@Composable
fun GlassSurface(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    material: BarMaterial = LocalBarMaterial.current,
    shape: Shape = RoundedCornerShape(LocalGlassSpec.current.cornerRadius),
    pressFraction: Float = 1f,
    pressedScale: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f

    // 闄嶇礆锛氭矑 backdrop銆佹垨鏉愯唱鏄?SOLID銆佹垨 spec 鏄?FALLBACK锛堢劇妯＄硦鐒℃姌灏勶級
    if (backdrop == null || !spec.usesGlass(material) || spec.blurDp <= 0.dp) {
        Box(
            modifier = modifier
                .background(
                    color = spec.surfaceTint.copy(
                        alpha = if (isDark) 0.14f else 0.94f,
                    ),
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.Black.copy(alpha = 0.08f)
                    },
                    shape = shape,
                ),
            content = content,
        )
        return
    }

    val blurRadius = spec.blurFor(material)

    Box(
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // 鈶?澧為＝鍜岋細璁撹儗寰岀殑椤忚壊鏇存績銆?                //    灏戜簡閫欎竴姝ワ紝鐜荤拑鑳屽緦鐨勫収瀹规渻鐧肩伆銆佺湅璧蜂締寰堛€岄珤銆嶃€?                vibrancy()

                // 鈶?妯＄硦銆?~12dp 灏卞 鈥斺€?娑叉厠鐜荤拑闈犳姌灏勪笉闈犵硦銆?                if (blurRadius > 0.dp) {
                    blur(blurRadius.toPx())
                }

                // 鈶?鎶樺皠 + 鑹叉暎 鈥斺€?娑叉厠鐜荤拑鐨勯潏榄傘€?                //
                // 鐢ㄧ殑鏄?*鑷繁瀵殑 AGSL shader**锛堣 LiquidGlassShader.kt锛夛紝
                // 涓嶆槸搴殑 lens()銆傚師鍥狅細lens() 鍙湁銆屾暣鐗囧潎鍕诲亸绉汇€嶏紝
                // 鑰岀湡姝ｇ殑娑叉厠鐜荤拑瑕併€屽彧鍦ㄩ倞绶ｆ姌灏?+ 鑹叉暎 + 璺熷湏瑙掕蛋銆嶃€?                //
                // FROSTED 鍙硦涓嶆姌灏勶紙閭ｆ墠鏄€岀（鐮傘€嶏級銆?                if (material == BarMaterial.LIQUID && spec.lensAmountDp > 0.dp) {
                    val f = pressFraction.coerceIn(0f, 1f)
                    liquidGlassRefraction(
                        height = spec.lensHeightDp.toPx() * f,
                        amount = spec.lensAmountDp.toPx() * f,
                        depth = spec.depthEffect > 0f,
                        dispersion = spec.chromaticAberration * f,
                    )
                }
            },
            // 鈶?閭婄罚楂樺厜 鈥斺€?鈽?閫欎竴灞ゆ槸妤富瑾€屽鎬倞绶ｇ櫧鍏夈€嶇殑渚嗘簮锛岄爯瑷棞鎺夈€?            //
            // 鐐轰粈楹煎畠鏄尟鐨勶細鐪熸鐨勯珮鍏変笉鏄€屼竴鍦堝潎鍕荤櫧绶氥€嶏紝鑰屾槸
            // **鏈変綅缃殑鍏夋簮**鍦ㄧ幓鐠冮倞绶ｇ殑鍙嶅皠锛堝乏涓婁寒銆佸彸涓嬫殫锛夈€?            // 涓€鍦?uniform 鐧界窔鐪嬭捣渚嗗氨鏄€屾弿浜嗗€嬬櫧閭娿€嶏紝闈炲父寤夊児銆?            //
            // 璁婄劍鐠扮殑姝ｈВ锛圡iuix `BloomStroke`锛夌敤鐨勬槸
            // `LightSource(LightPosition(x, y, z=-0.5), alpha)` 闆欏厜婧愭ā鍨?鈥斺€?            // 閭ｉ渶瑕佷竴鏁村闆欏厜婧?shader锛屽爆涓嬩竴姝ュ伐浣溿€?            //
            // 鍦ㄦ涔嬪墠锛?*瀵у彲娌掓湁楂樺厜锛屼篃涓嶈閭ｅ湀鍋囩櫧绶氥€?*
            highlight = {
                if (spec.highlightAlpha > 0f) {
                    Highlight(
                        width = 0.6.dp,
                        blurRadius = 2.dp,
                        alpha = spec.highlightAlpha * pressFraction.coerceIn(0f, 1f),
                    )
                } else {
                    null
                }
            },
            // 鈶?澶栭櫚褰憋細璁撶幓鐠冦€屾诞銆嶅湪鍏у涓娿€?            shadow = {
                Shadow(
                    radius = spec.shadowRadiusDp,
                    alpha = spec.shadowAlpha,
                )
            },
            // 鈶?鈽?鍏ч櫚褰憋細鐜荤拑鐨勩€屽帤搴︺€嶃€?            //    閫嗗悜鍑轰締鏄?PillInnerShadowDp = 8dp锛岄毃鎸夊寮峰害绺斁銆?            //    鎴戠涓€鐗堝畬鍏ㄦ矑鍋氶€欎竴灞わ紝鎵€浠ョ湅璧蜂締鍍忓崐閫忔槑璨肩礄銆?            innerShadow = {
                InnerShadow(
                    radius = spec.innerShadowDp * pressFraction.coerceIn(0f, 1f),
                    alpha = spec.innerShadowAlpha * pressFraction.coerceIn(0f, 1f),
                )
            },
            // 鎸変笅鏀惧ぇ锛氭粦濉婅鎸変笅鏅傛渻銆岄紦璧蜂締銆嶃€?            layerBlock = {
                if (pressedScale != 1f) {
                    scaleX = pressedScale
                    scaleY = pressedScale
                }
            },
            // 鈶?琛ㄩ潰搴曡壊銆傛泛 34% / 娣?16%锛岀背鐧戒笉鏄磾鐧姐€?            onDrawSurface = {
                drawRect(
                    color = spec.surfaceTint.copy(alpha = spec.surfaceAlpha),
                )
            },
        ),
        content = content,
    )
}

/**
 * 绲︾幓鐠冩彁渚涙姌灏勪締婧愩€? *
 * 鈽呪槄 闂滈嵉瑕忓墖锛?*瑕佽鎶樺皠鐨勫収瀹硅！涓嶈兘鏈夌幓鐠冦€?*
 *
 * 閫欐槸銆屽暉鍕曞嵆姝汇€嶇殑鐪熸鍘熷洜锛堟煡浜嗗緢涔咃級锛氬収瀹硅！鑻ヤ篃鏈夌幓鐠冿紝
 * 瀹冩渻鍦ㄥ悓涓€灞よ！鎺℃ǎ閭ｄ竴灞?鈥斺€?鎺℃ǎ鑷繁 鈫?鐒￠檺閬炶看 鈫? * 娓叉煋鍗℃ 鈫?绯荤当浠?coldStart timeout 娈洪€茬▼
 *锛堟墍浠?logcat 娌掓湁 FATAL EXCEPTION锛屽彧鏈?onTaskVanished锛夈€? *
 * 鎵€浠ュ垎鎴愬叐鍊?slot锛? * - [content]锛氳鐣€?layer锛?*涓嶈兘鍚幓鐠?*锛? * - [overlay]锛氬湪 layer 涔嬪锛堢幓鐠冩斁閫欒！锛? *
 * 閫嗗悜鍙冭€冿細com.linxin.demo 鐢? * `rememberCombinedBackdrop(澶栭儴backdrop, rememberLayerBackdrop())`
 * 鎶婂叐鍊?backdrop 绲勫悎璧蜂締锛屾晥鏋滈浼尖€斺€斿畠鐨勫閮?backdrop 渚嗚嚜鏇翠笂灞わ紝
 * 鎴戝€戦€欒！绨″寲鎴愩€屽収瀹瑰堡 + 瑕嗚搵灞ゃ€嶅氨澶犵敤銆? */
@Composable
fun GlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.(LayerBackdrop) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()

    Box(modifier = modifier) {
        Box(modifier = Modifier.matchParentSize().layerBackdrop(backdrop)) {
            content()
        }
        overlay(backdrop)
    }
}

/** 鐩墠闋侀潰鐨?[LayerBackdrop]銆?*/
val LocalGlassBackdrop =
    androidx.compose.runtime.staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 渚濇繁娣烘彁渚?[GlassSpec] 鑸?[BarMaterial]銆? *
 * @param override 瑾垮弮闋佸偝閫蹭締鐨勫€硷紱null = 鐢ㄦ繁娣哄畾绋裤€? * @param material 搴曟瑒鏉愯唱锛沶ull = 鐢?[LocalBarMaterial] 鐨勯爯瑷紙娑叉厠锛夈€? */
@Composable
fun GlassTheme(
    override: GlassSpec? = null,
    material: BarMaterial? = null,
    content: @Composable () -> Unit,
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val spec = override ?: GlassSpec.forDark(isDark)
    CompositionLocalProvider(
        LocalGlassSpec provides spec,
        LocalBarMaterial provides (material ?: BarMaterial.LIQUID),
        content = content,
    )
}

/** 鐜荤拑搴曟瑒鐨勬婧栭珮搴︼紙閫嗗悜锛歀xGlass.PillHeightDp = 56dp锛夈€?*/
val GlassBarHeight = 64.dp
