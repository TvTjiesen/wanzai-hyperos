package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 娑叉厠鐜荤拑鐨?*鎶樺皠 + 鑹叉暎** shader锛圓GSL锛夈€?
 *
 * ## 渚嗘簮锛氶€嗗悜 `xyz.nextalone.hyperzoomring` 1.34.0-beta锛堣惉鑳借畩鐒︾挵锛?
 *
 * 妤富鎻愪緵閭ｅ€?APK 瑾€屼綘閭ｅ€嬫妱涓嶆槑鐧藉氨鎶勬垜閫欏€嬭畩鐒︾挵妯＄祫鐨勭幓鐠冨惂銆嶃€?
 * 瀹冪殑瀵︿綔鍦?`defpackage.js.F(scope, refractionHeight, refractionAmount,
 * depthEffect, chromaticAberration)`锛屽叐娈?AGSL 鍘熸ǎ鍦ㄥ瓧涓茶！銆?
 * 鏈獢鏄?*閫愬瓧绉绘**锛堝彧鏀逛簡绺帓鑸囪ɑ閲嬶級锛屾暩瀛镐竴琛屾矑鍕曘€?
 *
 * ## 鐐轰粈楹艰鑷繁瀵?shader锛岃€屼笉鏄敤 backdrop 鐨?`lens()`
 *
 * `kyant/backdrop` 鐨?`lens()` 鍙湁**鍧囧嫽鎶樺皠**锛堟暣鐗囨湞涓€鍊嬫柟鍚戝亸锛夈€?
 * 鑰岀湡姝ｇ殑娑叉厠鐜荤拑瑕佷笁浠朵簨锛屽畠鍋氫笉鍒帮細
 *
 * 1. **鍙湪閭婄罚鎶樺皠** 鈥斺€?鍏ч儴蹇呴爤鍘熸ǎ閫忓嚭锛坄if (-sd >= refractionHeight) return content.eval(coord)`锛夈€?
 *    `lens()` 鏄暣鐗囦竴璧锋壄锛屾墍浠ラ倞绶ｅ拰涓績涓€妯ｇ硦锛岀湅璧蜂締鍍忋€岄殧钁楁按鐪嬨€嶈€屼笉鏄€岄殧钁楃幓鐠冪湅銆嶃€?
 * 2. **鑹叉暎** 鈥斺€?閭婄罚瑕佸嚭鐝惧僵铏瑰垎闆€傞€欐槸銆岄€氶€?+ 鎶樺皠銆嶇殑闈堥瓊锛宍lens()` 瀹屽叏娌掓湁銆?
 * 3. **鍦撹鏀拹** 鈥斺€?鎶樺皠鏂瑰悜瑕佽窡钁楀湏瑙掕蛋锛坄cornerRadii` 鍥涜鍒嗗垾绠楋級锛?
 *    `lens()` 鍙獚涓€鍊嬬当涓€褰㈢媭銆?
 *
 * ## 鏁稿鎬庨杭閬嬩綔锛堢湅鎳傞€欎笁琛屽氨鎳傛恫鎱嬬幓鐠冿級
 *
 * ```
 * sd  = 鍒板湏瑙掔煩褰㈤倞鐣岀殑銆屾湁鍚戣窛闆€嶏紙璨?= 鍦ㄥ収閮級
 * d   = circleMap(1 - (-sd)/refractionHeight) * refractionAmount
 *     鈫?鍙湪闆㈤倞鐣?refractionHeight 浠ュ収闈為浂锛屼笖瓒婇潬閭婅秺澶э紙鍦撳姬鏄犲皠锛?
 * grad = 閭婄晫娉曠窔 + depthEffect 脳 鎸囧悜涓績鐨勫悜閲?
 *     鈫?鍏╅爡鐩稿姞 = 鎶樺皠鏂瑰悜鏃€屾湞鍏с€嶅張銆岀篂钁楅倞绶ｈ蛋銆嶁啋 鐜荤拑鏈冮紦璧蜂締
 * refractedCoord = coord + d * grad
 * ```
 *
 * 鑹叉暎鐗堝啀澶氬仛 7 娆℃帯妯ｏ紙绱呮榛冪稜闈掕棈绱悇鍋忎竴榛烇級锛?
 * 鎸夐€氶亾鍔犳瑠姹傚拰 鈫?閭婄罚鍑虹従鐪熷鑹叉暎銆?
 */
object LiquidGlassShader {

    /**
     * **鐒¤壊鏁ｇ増**锛堝彧鏈夋姌灏勶級銆?
     *
     * 鐢ㄦ柤锛氬ぇ闈㈢鐜荤拑銆佹晥鑳芥晱鎰熻檿銆?
     */
    const val REFRACT_ONLY: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}


float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    // 闆㈤倞鐣岃秴閬?refractionHeight 鈫?鐩存帴鍘熸ǎ閫忓嚭锛堝収閮ㄤ笉鎶樺皠锛?
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

    /**
     * **鑹叉暎鐗?*锛堟姌灏?+ 7 鑹插垎闆級銆?
     *
     * 鐢ㄦ柤锛氬簳娆勩€佹粦濉娿€佹寜閳曠瓑銆岃鐪嬪嚭鏄幓鐠冦€嶇殑灏忛潰绌嶅厓浠躲€?
     *
     * 浠ｅ児锛氭瘡鍊嬪儚绱犳帯妯?**7 娆?*鑰屼笉鏄?1 娆°€傛墍浠ュ彧绲﹀皬鍏冧欢鐢紝
     * 澶ч潰绌嶏紙鏁撮爜鍗＄墖锛夌敤 [REFRACT_ONLY]銆?
     */
    const val REFRACT_DISPERSION: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;


float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}


float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
}

/**
 * 灏?[BackdropEffectScope] 濂楃敤鎶樺皠锛? 鍙伕鑹叉暎锛夈€?
 *
 * ## 鍙冩暩闋嗗簭鐓ч€嗗悜绲愭灉
 *
 * 閫嗗悜鍑轰締鐨?uniform 瑷畾锛坄defpackage.rv0`锛夛細
 * ```java
 * setFloatUniform("size", w, h)
 * setFloatUniform("offset", -padding, -padding)     // 娉ㄦ剰鏄矤鐨?
 * setFloatUniform("cornerRadii", radii[4])
 * setFloatUniform("refractionHeight", h)
 * setFloatUniform("refractionAmount", -amount)      // 鈽?璨犲€?
 * setFloatUniform("depthEffect", depth ? 1 : 0)
 * setFloatUniform("chromaticAberration", ca)
 * ```
 *
 * `refractionAmount` 鏄?*璨?*鐨?鈥斺€?鍥犵偤 shader 瑁?`coord + d * grad`锛?
 * 瑕佸線鍏у嚬灏卞緱鏄矤鏂瑰悜銆傞€欏€嬬铏熸垜瑭﹂尟涓€娆℃墠鏄庣櫧锛堟鐨勬渻寰€澶栧嚫锛岀湅璧蜂締鍍忔斁澶ч彙锛夈€?
 *
 * ## padding 蹇呴爤鍏堣ō
 *
 * shader 鏈冩帯妯ｈ秴鍑哄厓浠堕倞鐣岀殑鍍忕礌锛堟姌灏勮寰€澶栨姄锛夛紝
 * 涓嶈ō padding 閭婄罚鏈冭瑁佹帀锛屽嚭鐝鹃粦閭娿€?
 * 閫嗗悜瑁℃槸 `if (padding < refractionAmount) padding = refractionAmount`銆?
 *
 * @param height 鎶樺皠楂樺害锛坧x锛夛細闆㈤倞鐣屽閬犻枊濮嬫姌灏?
 * @param amount 鎶樺皠閲忥紙px锛夛細閭婄罚鏈€澶氬亸绉诲灏?
 * @param depth 鏄惁鍔犲叆銆屾湞涓績鏀躲€嶇殑娣卞害闋咃紙璁撶幓鐠冮紦璧蜂締锛?
 * @param dispersion 鑹叉暎寮峰害锛? = 鐒¤壊鏁ｏ級
 */
fun BackdropEffectScope.liquidGlassRefraction(
    height: Float,
    amount: Float,
    depth: Boolean = true,
    dispersion: Float = 0f,
) {
    if (height <= 0f || amount <= 0f) return

    // 鈶?padding锛氭姌灏勮寰€鍏冧欢澶栨姄鍍忕礌锛屼笉绲︽渻瑁佸嚭榛戦倞
    if (padding < amount) {
        padding = amount
    }

    // 鈶?鍙敮鎻村湏瑙掔煩褰€傚叾瀹冨舰鐙€鐩存帴璺抽亷锛堜笉鐚滐級銆?
    val rr = shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: return

    // 鈶?鍙栧洓瑙掑崐寰戯紙渚?LayoutDirection 瑾挎暣宸﹀彸锛?
    val sizePx = size
    val w = sizePx.width
    val h = sizePx.height
    val halfMin = minOf(w, h) / 2f
    val isLtr = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr
    val topStart = (if (isLtr) rr.topStart else rr.topEnd).toPx(sizePx, this)
    val topEnd = (if (isLtr) rr.topEnd else rr.topStart).toPx(sizePx, this)
    val bottomStart = (if (isLtr) rr.bottomStart else rr.bottomEnd).toPx(sizePx, this)
    val bottomEnd = (if (isLtr) rr.bottomEnd else rr.bottomStart).toPx(sizePx, this)
    // 鍗婂緫涓嶈兘瓒呴亷鐭倞涓€鍗?
    val radii = floatArrayOf(
        minOf(topStart, halfMin),
        minOf(topEnd, halfMin),
        minOf(bottomEnd, halfMin),
        minOf(bottomStart, halfMin),
    )

    val useDispersion = dispersion > 0f
    val shaderName = if (useDispersion) "LiquidGlassLensDispersion" else "LiquidGlassLens"
    val source = if (useDispersion) {
        LiquidGlassShader.REFRACT_DISPERSION
    } else {
        LiquidGlassShader.REFRACT_ONLY
    }

    runtimeShaderEffect(
        key = shaderName,
        shaderString = source,
        uniformShaderName = "content",
    ) {
        // receiver 鏄?RuntimeShader锛岀洿鎺ュ懠鍙?
        setFloatUniform("size", w, h)
        val negPadding = -padding
        setFloatUniform("offset", negPadding, negPadding)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", height)
        // 鈽?璨犲€硷細姝ｇ殑鏈冨線澶栧嚫锛堟斁澶ч彙锛夛紝璨犵殑鎵嶅線鍏у嚬锛堢幓鐠冿級
        setFloatUniform("refractionAmount", -amount)
        setFloatUniform("depthEffect", if (depth) 1f else 0f)
        if (useDispersion) {
            setFloatUniform("chromaticAberration", dispersion)
        }
    }
}
