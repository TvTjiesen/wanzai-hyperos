package com.muyao.wanzaihyperos.ui.design.glass

import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 液態玻璃的折射 + 色散 shader（AGSL）。
 *
 * ## 來源：逆向 xyz.nextalone.hyperzoomring 1.34.0-beta（萬能變焦環）
 *
 * 業主提供那個 APK 說「你那個抄不明白就抄我這個變焦環模組的玻璃」。
 * 實作在 `defpackage.js.F(scope, refractionHeight, refractionAmount,
 * depthEffect, chromaticAberration)`，兩段 AGSL 原樣在字串裡。
 * 本檔是**逐字移植**（只改縮排與註釋），數學一行沒動。
 *
 * ## 為什麼要自己寫 shader，而不是用 backdrop 的 lens()
 *
 * `kyant/backdrop` 的 `lens()` 只有**均勻折射**（整片朝一個方向偏）。
 * 真正的液態玻璃要三件事，它做不到：
 *
 * 1. **只在邊緣折射** —— 內部必須原樣透出
 *    （`if (-sd >= refractionHeight) return content.eval(coord)`）。
 *    `lens()` 是整片一起扭，所以邊緣和中心一樣糊。
 * 2. **色散** —— 邊緣要出現彩虹分離。`lens()` 完全沒有。
 * 3. **圓角支撐** —— 折射方向要跟著圓角走（`cornerRadii` 四角分別算）。
 *
 * ## 數學怎麼運作（看懂這三行就懂液態玻璃）
 *
 * ```
 * sd  = 到圓角矩形邊界的「有向距離」（負 = 在內部）
 * d   = circleMap(1 - (-sd)/refractionHeight) * refractionAmount
 *     ↑ 只在離邊界 refractionHeight 以內非零，越靠邊越大（圓弧映射）
 * grad = 邊界法線 + depthEffect × 指向中心的向量
 *     ↑ 兩項相加 = 折射方向既「朝內」又「繞著邊緣走」→ 玻璃會鼓起來
 * refractedCoord = coord + d * grad
 * ```
 */
object LiquidGlassShader {

    /** **無色散版**（只有折射）。用於大面積玻璃、效能敏感處。 */
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
     * **色散版**（折射 + 7 色分離）。
     *
     * 代價：每個像素採樣 **7 次**而不是 1 次。所以只給小面積元件用。
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

    // ── 色散（chromatic aberration）────────────────────────────────
    //
    // ★ 這裡換掉了原參考實作的 7 色採樣，理由（真機實測）：
    //
    //   原實作：dispersionIntensity = ca * ((x*y) / (hx*hy))，再按 7 個色相
    //   各偏移一點、用 color.r/g/b 分別累加**半通道**。
    //   問題有兩個：
    //     1. `x*y` 在四個象限正負號不同 → 四個角的色散方向不一致，
    //        疊出來不是「彩虹邊」而是**一片綠/青色塊**
    //        （業主看到的「綠色塑料片」就是它）。
    //     2. 通道累加沒歸一化（red.r/3.5 + orange.r/3.5 + yellow.r/3.5 …），
    //        總量超過 1，整體偏色。
    //
    //   正解：**沿折射方向對 R/G/B 做對稱偏移**，這才是物理上的色散 ——
    //   不同波長折射率不同，所以三個通道分開一點。偏移量與 `d`（折射量）
    //   成正比，所以只在邊緣可見；內部 d=0，三通道重合，沒有色偏。
    //
    //   強度係數直接用 chromaticAberration，範圍 0~1。
    if (chromaticAberration > 0.0) {
        float2 dir = normalize(grad + float2(0.0001, 0.0));
        float split = chromaticAberration * abs(d) * 0.5;
        float2 off = dir * split;

        half4 cr = content.eval(refractedCoord + off);
        half4 cg = content.eval(refractedCoord);
        half4 cb = content.eval(refractedCoord - off);

        // 保留 alpha 取中間通道（三通道 alpha 幾乎相同）
        return half4(cr.r, cg.g, cb.b, cg.a);
    }

    return content.eval(refractedCoord);
}
"""
}

/**
 * 對 [BackdropEffectScope] 套用折射（+ 可選色散）。
 *
 * ## 參數順序照逆向結果（defpackage.rv0）
 *
 * ```
 * setFloatUniform("size", w, h)
 * setFloatUniform("offset", -padding, -padding)
 * setFloatUniform("cornerRadii", radii[4])
 * setFloatUniform("refractionHeight", h)
 * setFloatUniform("refractionAmount", -amount)   // ★ 負值
 * setFloatUniform("depthEffect", depth ? 1 : 0)
 * setFloatUniform("chromaticAberration", ca)
 * ```
 *
 * `refractionAmount` 是**負**的 —— 因為 shader 裡 `coord + d * grad`，
 * 要往內凹就得是負方向。正的會往外凸（看起來像放大鏡）。
 *
 * ## padding 必須先設
 *
 * shader 會採樣超出元件邊界的像素（折射要往外抓），
 * 不設 padding 邊緣會被裁掉，出現黑邊。
 * 逆向裡是 `if (padding < refractionAmount) padding = refractionAmount`。
 *
 * ⚠️ 但那圈 padding 在圓角形狀**之外**，所以調用方必須 `Modifier.clip(shape)`，
 * 否則會露出一塊異色方塊（真機上表現為「底欄右邊多出一塊白」）。
 *
 * @param height 折射高度（px）：離邊界多遠開始折射
 * @param amount 折射量（px）：邊緣最多偏移多少
 * @param depth 是否加入「朝中心收」的深度項（讓玻璃鼓起來）
 * @param dispersion 色散強度（0 = 無色散）
 */
fun BackdropEffectScope.liquidGlassRefraction(
    height: Float,
    amount: Float,
    depth: Boolean = true,
    dispersion: Float = 0f,
) {
    if (height <= 0f || amount <= 0f) return

    // ① padding：折射要往元件外抓像素，不給會裁出黑邊
    if (padding < amount) {
        padding = amount
    }

    // ② 只支援圓角矩形。其它形狀直接跳過（不猜）。
    val rr = shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: return

    // ③ 取四角半徑（依 LayoutDirection 調整左右）
    val sizePx = size
    val w = sizePx.width
    val h = sizePx.height
    val halfMin = minOf(w, h) / 2f
    val isLtr = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr
    val topStart = (if (isLtr) rr.topStart else rr.topEnd).toPx(sizePx, this)
    val topEnd = (if (isLtr) rr.topEnd else rr.topStart).toPx(sizePx, this)
    val bottomStart = (if (isLtr) rr.bottomStart else rr.bottomEnd).toPx(sizePx, this)
    val bottomEnd = (if (isLtr) rr.bottomEnd else rr.bottomStart).toPx(sizePx, this)
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
        setFloatUniform("size", w, h)
        val negPadding = -padding
        setFloatUniform("offset", negPadding, negPadding)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", height)
        // ★ 負值：正的會往外凸（放大鏡），負的才往內凹（玻璃）
        setFloatUniform("refractionAmount", -amount)
        setFloatUniform("depthEffect", if (depth) 1f else 0f)
        if (useDispersion) {
            setFloatUniform("chromaticAberration", dispersion)
        }
    }
}
