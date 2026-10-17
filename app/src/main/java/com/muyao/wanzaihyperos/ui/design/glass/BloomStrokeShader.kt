package com.muyao.wanzaihyperos.ui.design.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * **真正的玻璃高光**：雙光源 + 曲面法線（AGSL）。
 *
 * ## 這是什麼，以及它和「那圈白光」的差別
 *
 * 業主說過兩次：「奇怪邊緣白光，去掉」。
 *
 * 我原本用庫的 `Highlight(...)` 畫一條寬 0.6dp 的白線 ——
 * 那是**平面的、均勻的**一圈，看起來就是描了個白邊。
 *
 * 真玻璃的高光是**曲面上的反射**：
 * - 玻璃邊緣是**圓角曲面**，法線不是平的
 * - 光從某方向來，法線與光向的點積決定亮度
 * - 所以**左上一段亮、右下一段暗**，而且亮的部位隨光源方向移動
 *
 * 這個 shader 用 **SDF 算出的 3D 法線**（含 z 深度）來做。
 *
 * ## 來源
 *
 * 逐字移植 Miuix `miuix-blur` 的 `BloomStroke`
 *（`top.yukonga.miuix.kmp.blur.highlight`，Apache-2.0）。
 * 那個庫要 minSdk 33 用不了，但 `kyant/backdrop` 提供同一個
 * `runtimeShaderEffect` 擴展點，所以把 shader 與 uniform 算法搬過來。
 *
 * 數值同時對應參考 app（萬能變焦環）的 `GlassStrokeMiddleLight`：
 * ```
 * strokeWidth = 2.8f, innerBlurRadius = 2.8f
 * primary   = LightPosition(0.5, 0.5, -0.5), intensity 0.4
 * secondary = LightPosition(0.5, 0.8, -0.5), intensity 0.25
 * ```
 *
 * ## 數學三步
 *
 * 1. **SDF**：到圓角矩形邊界的有向距離（負 = 在內部）
 * 2. **法線**：`z = sqrt(innerBlurRadiusSq - t²)` 讓邊緣「翹起來」
 * 3. **雙光源**：`亮度 = (n·lightDir)² × intensity`，兩組疊加，
 *    再加一道「軸向衰減」讓光只落在一側
 */
object BloomStrokeShader {

    /**
     * 高光 shader（移植自 Miuix `ShadersKt.buildBloomStrokeShader(false)`）。
     *
     * 注意：這是**疊加**用的 shader —— 它輸出的是「高光顏色 + alpha」，
     * 由 `runtimeShaderEffect` 鏈在現有 render effect 之後。
     */
    const val SOURCE: String = """
uniform shader content;

uniform float2 halfView;
uniform float2 halfViewFloor;
uniform float4 cornerRadii;
uniform float strokeWidth;
uniform float innerBlurRadius;
uniform float innerBlurRadiusSq;
uniform float highlightAlpha;

uniform float4 strokeColor;
uniform float strokeAlphaMul;

uniform float3 lightDir1;
uniform float4 lightColor1;
uniform float lightIntensity1;

uniform float3 lightDir2;
uniform float4 lightColor2;
uniform float lightIntensity2;

uniform float2 axis1;
uniform float2 axis2;

float pickRadius(float2 fragCoord, float4 radii) {
    float2 up = fragCoord.y < halfView.y ? radii.xy : radii.zw;
    return fragCoord.x < halfView.x ? up.x : up.y;
}

float roundedBoxSDF(float2 pos, float2 halfSize, float radius) {
    radius = min(radius, min(halfSize.x, halfSize.y));
    float2 d = pos - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}

float3 getNormal(float2 fragCoord, float sdf, float R) {
    float2 xy = fragCoord - halfViewFloor;
    float2 xy_a = abs(xy);
    float t = smoothstep(-innerBlurRadius, 0.0, sdf);
    float z = sqrt(max(innerBlurRadiusSq - t * t, 0.0));
    float3 coord = float3(xy_a, -z);

    float2 corner = halfView - R;
    corner.x = min(corner.x, xy_a.x);
    corner.y = min(corner.y, xy_a.y);

    float2 dir = normalize(coord.xy - corner.xy);
    corner += dir * (R - innerBlurRadius);

    if (any(lessThan(xy_a, corner))) {
        return float3(0.0, 0.0, -1.0);
    }

    float2 signal = sign(xy);
    float3 n = normalize(coord - float3(corner, 0.0));
    n.xy *= signal;
    return n;
}

half4 main(float2 fragCoord) {
    float2 xy = abs(fragCoord - halfView);

    float originRadius = pickRadius(fragCoord, cornerRadii);
    float R = max(originRadius, innerBlurRadius);

    // 內部：原樣透出，不加高光
    if (all(lessThan(xy, halfView - R))) {
        return content.eval(fragCoord);
    }

    float sdf = roundedBoxSDF(xy, halfView, originRadius);
    half outMask = half(smoothstep(0.0, -1.0, sdf));
    float strokeAlpha = smoothstep(-strokeWidth, -strokeWidth + 1.0, sdf);

    half3 rgb = strokeColor.rgb * half(strokeAlphaMul * strokeAlpha * strokeAlpha);

    float3 n = getNormal(fragCoord, sdf, R);

    float l1 = dot(n.xy, lightDir1.xy);
    rgb += half(l1 * l1 * lightIntensity1) * lightColor1.rgb;
    float l2 = dot(n.xy, lightDir2.xy);
    rgb += half(l2 * l2 * lightIntensity2) * lightColor2.rgb;

    float falloff1 = max(dot(float3(axis1, 0.0), n), 0.0);
    float light1 = clamp(dot(n, lightDir1) * falloff1, 0.0, 1.0);
    rgb += half(light1 * light1 * lightIntensity1) * lightColor1.rgb;

    float falloff2 = max(dot(float3(axis2, 0.0), n), 0.0);
    float light2 = clamp(dot(n, lightDir2) * falloff2, 0.0, 1.0);
    rgb += half(light2 * light2 * lightIntensity2) * lightColor2.rgb;

    half4 hl = half4(rgb * half(highlightAlpha), 1.0) * outMask;

    // 疊回原內容（premultiplied add）
    half4 base = content.eval(fragCoord);
    return base + hl;
}
"""

    /**
     * 光源參考點（逆向 `HighlightStyleKt.LIGHT_REF_X/Y`）。
     *
     * 光的方向 = `光源位置 - 參考點`，再歸一化。
     * 參考點是 **(0.5, 0.7)** 而不是中心 (0.5, 0.5) ——
     * 意思是「光從**上方**照下來」，所以玻璃上沿比下沿亮。這是刻意的。
     */
    const val LIGHT_REF_X = 0.5f
    const val LIGHT_REF_Y = 0.7f
}

/**
 * 一個光源。
 *
 * @param x 光源 x（0~1，相對寬度）
 * @param y 光源 y（0~1，相對高度；**越小越靠上**）
 * @param z 光源 z（**負值 = 在玻璃前方**；參考 app 用 -0.5）
 * @param intensity 強度
 * @param color 顏色（通常白色）
 */
data class GlassLight(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val z: Float = -0.5f,
    val intensity: Float = 0.4f,
    val color: Color = Color.White,
)

/**
 * 高光參數。
 *
 * 預設值對應參考 app 的 `GlassStrokeMiddleLight`。
 */
data class GlassHighlight(
    /** 描邊寬度（px）。這不是「一條線」，是 SDF 的過渡帶。 */
    val strokeWidth: Dp = 1.2.dp,
    /** 內模糊半徑：決定邊緣「翹起來」的高度（越大越像厚玻璃）。 */
    val innerBlurRadius: Dp = 2.8.dp,
    val primary: GlassLight = GlassLight(0.5f, 0.5f, -0.5f, 0.40f),
    val secondary: GlassLight = GlassLight(0.5f, 0.8f, -0.5f, 0.25f),
    /** 整體 alpha。 */
    val alpha: Float = 1f,
    /** 描邊底色（通常透明，讓光自己說話）。 */
    val strokeColor: Color = Color.Transparent,
)

/**
 * 把雙光源高光套到 [BackdropEffectScope]。
 *
 * ⚠️ 需要 API 33+（RuntimeShader）。不支援時靜默跳過 ——
 * 沒有高光只是「少了點質感」，不該讓界面壞掉。
 *
 * @param shape 容器形狀。**必須是 RoundedCornerShape**，否則跳過（不猜）。
 */
fun BackdropEffectScope.bloomStrokeHighlight(
    highlight: GlassHighlight,
    shape: Shape,
) {
    val rr = shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: return
    if (highlight.alpha <= 0f) return

    val sizePx = size
    val w = sizePx.width
    val h = sizePx.height
    if (w <= 0f || h <= 0f) return

    val halfW = w * 0.5f
    val halfH = h * 0.5f
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

    val strokeWidthPx = highlight.strokeWidth.toPx()
    val innerBlurPx = highlight.innerBlurRadius.toPx()

    runtimeShaderEffect(
        key = "GlassBloomStroke",
        shaderString = BloomStrokeShader.SOURCE,
        uniformShaderName = "content",
    ) {
        // receiver 是 RuntimeShader（Kotlin 側型別是 `RuntimeShader.() -> Unit`）
        setFloatUniform("halfView", halfW, halfH)
        setFloatUniform(
            "halfViewFloor",
            kotlin.math.floor(halfW),
            kotlin.math.floor(halfH),
        )
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("strokeWidth", strokeWidthPx)
        setFloatUniform("innerBlurRadius", innerBlurPx)
        setFloatUniform("innerBlurRadiusSq", innerBlurPx * innerBlurPx)
        setFloatUniform("highlightAlpha", highlight.alpha)

        setFloatUniform(
            "strokeColor",
            highlight.strokeColor.red,
            highlight.strokeColor.green,
            highlight.strokeColor.blue,
            highlight.strokeColor.alpha,
        )
        setFloatUniform("strokeAlphaMul", highlight.strokeColor.alpha)

        // 兩組光源（照逆向 applyLightUniforms 的數學）
        applyLightTo(this, highlight.primary, true)
        applyLightTo(this, highlight.secondary, false)
    }
}

/**
 * 設定一組光源的 uniform（照逆向 `HighlightStyleKt.applyLightUniforms`）。
 *
 * ```
 * dx = pos.x - 0.5; dy = pos.y - 0.7; dz = pos.z
 * len = sqrt(dx² + dy² + dz²)
 * lightDir = (dx/len, dy/len, dz/len)
 * axis = normalize(dx, dy)      // xy 平面投影方向
 * ```
 */
private fun applyLightTo(
    shader: com.kyant.backdrop.RuntimeShader,
    light: GlassLight,
    isPrimary: Boolean,
) {
    val dx = light.x - BloomStrokeShader.LIGHT_REF_X
    val dy = light.y - BloomStrokeShader.LIGHT_REF_Y
    val dz = light.z
    var len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    if (len < 1e-6f) len = 1e-6f

    val n = if (isPrimary) "1" else "2"
    shader.setFloatUniform("lightDir$n", dx / len, dy / len, dz / len)
    shader.setFloatUniform(
        "lightColor$n",
        light.color.red,
        light.color.green,
        light.color.blue,
        light.color.alpha,
    )
    shader.setFloatUniform("lightIntensity$n", light.intensity * light.color.alpha)

    val axisLen = kotlin.math.sqrt(dx * dx + dy * dy)
    if (axisLen > 0.001f) {
        shader.setFloatUniform("axis$n", dx / axisLen, dy / axisLen)
    } else {
        shader.setFloatUniform("axis$n", 0f, if (isPrimary) -1f else 1f)
    }
}
