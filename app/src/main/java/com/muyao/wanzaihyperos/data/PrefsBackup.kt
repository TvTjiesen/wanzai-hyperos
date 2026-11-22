package com.muyao.wanzaihyperos.data

import org.json.JSONObject

/** Only portable UI preferences; never credentials, school records, image paths or reminder schedules. */
object PrefsBackup {
    const val VERSION = 2
    const val MAX_BYTES = 64 * 1024
    private val labels = linkedMapOf(
        "theme" to "主题", "background" to "背景色", "glass" to "玻璃材质",
        "container_style" to "背景模式", "home_density" to "首页密度", "home_modules" to "首页模块",
        "apps_web_collapsed" to "网页服务折叠", "timetable_grid" to "默认整周课表",
        "wallpaper_preset" to "内置天空", "wallpaper_dim" to "壁纸暗度", "wallpaper_soft" to "壁纸柔化",
        "glass_opacity" to "玻璃厚度", "reduce_motion" to "减少动态",
        "reduce_transparency" to "减弱透明度", "high_contrast" to "高对比",
    )
    private val enums = mapOf(
        "theme" to setOf("System", "Light", "Dark"),
        "background" to setOf("porcelain", "cream", "mist", "sakura", "lake"),
        "glass" to setOf("liquid", "frost", "off"),
        "container_style" to setOf("designed", "bare"),
        "home_density" to setOf("comfortable", "compact"),
        "wallpaper_preset" to setOf("auto", "morning", "dusk", "night", "slate", "aurora", "blossom"),
    )
    private val booleans = setOf("apps_web_collapsed", "timetable_grid", "reduce_motion", "reduce_transparency", "high_contrast")
    private val ranges = mapOf("wallpaper_dim" to 0..80, "wallpaper_soft" to 0..100, "glass_opacity" to 0..100)
    private val modules = listOf("courses", "rollcall", "todos", "szty")
    data class Preview(val lines: List<Pair<String, String>>, val raw: JSONObject)
    var lastError: String? = null
        private set

    fun export(): String {
        val values = JSONObject().apply {
            put("theme", UiPrefs.themeModeName); put("background", UiPrefs.backgroundPreset)
            put("glass", UiPrefs.glassMode); put("container_style", UiPrefs.containerStyle)
            put("home_density", UiPrefs.homeDensity); put("home_modules", modules.filter { it in UiPrefs.homeModules }.joinToString(","))
            put("apps_web_collapsed", UiPrefs.appsWebCollapsed); put("timetable_grid", UiPrefs.timetableGrid)
            if (UiPrefs.wallpaperPreset != "album") put("wallpaper_preset", UiPrefs.wallpaperPreset)
            put("wallpaper_dim", UiPrefs.wallpaperDim); put("wallpaper_soft", UiPrefs.wallpaperSoft)
            put("glass_opacity", UiPrefs.glassOpacity); put("reduce_motion", UiPrefs.reduceMotion)
            put("reduce_transparency", UiPrefs.reduceTransparency); put("high_contrast", UiPrefs.highContrastOverride)
        }
        return JSONObject().put("version", VERSION).put("savedAt", System.currentTimeMillis()).put("values", values).toString(2)
    }

    fun parse(text: String): Preview? {
        lastError = null
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return reject("备份超过64KB")
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return reject("不是有效的偏好备份")
        val version = root.opt("version")
        if (version !is Number || version.toDouble() != version.toInt().toDouble() || version.toInt() !in 1..VERSION) return reject("备份版本不受支持")
        val raw = root.optJSONObject("values") ?: return reject("没有偏好内容")
        val safe = JSONObject()
        labels.keys.filter { raw.has(it) }.forEach { safe.put(it, raw.get(it)) }
        val error = validate(safe)
        if (error != null) return reject(error)
        return Preview(labels.keys.filter { safe.has(it) }.map { labels.getValue(it) to display(it, safe.get(it)) }, safe)
    }

    private fun reject(message: String): Preview? { lastError = message; return null }
    private fun validate(values: JSONObject): String? {
        if (labels.keys.none { values.has(it) }) return "没有可恢复的界面偏好"
        labels.keys.filter { values.has(it) }.forEach { key ->
            val value = values.get(key)
            val valid = when {
                key in enums -> value is String && value in enums.getValue(key)
                key in booleans -> value is Boolean
                key in ranges -> value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() in ranges.getValue(key)
                key == "home_modules" -> value is String && value.split(',').let { ids -> ids.isNotEmpty() && ids.all { it in modules } && ids.distinct().size == ids.size }
                else -> false
            }
            if (!valid) return "${labels.getValue(key)}的值不合法，整份未应用"
        }
        return null
    }
    private fun display(key: String, value: Any): String = when (key) {
        "theme" -> mapOf("System" to "跟随系统", "Light" to "浅色", "Dark" to "深色")[value] ?: value.toString()
        "glass" -> mapOf("liquid" to "液态", "frost" to "磨砂", "off" to "实色")[value] ?: value.toString()
        "container_style" -> if (value == "bare") "纯色背景" else "显示壁纸"
        "home_density" -> if (value == "compact") "紧凑" else "舒适"
        "home_modules" -> value.toString().split(',').joinToString("、") { mapOf("courses" to "课程", "rollcall" to "签到", "todos" to "待办", "szty" to "校园跑").getValue(it) }
        else -> if (value is Boolean) { if (value) "开启" else "关闭" } else value.toString()
    }

    fun apply(preview: Preview): Int {
        lastError = validate(preview.raw)
        if (lastError != null) return -1
        val v = preview.raw
        if (v.has("theme")) UiPrefs.saveThemeMode(v.getString("theme"))
        if (v.has("background")) UiPrefs.saveBackgroundPreset(v.getString("background"))
        if (v.has("glass")) UiPrefs.saveGlassMode(v.getString("glass"))
        if (v.has("container_style")) UiPrefs.saveContainerStyle(v.getString("container_style"))
        if (v.has("home_density")) UiPrefs.saveHomeDensity(v.getString("home_density"))
        if (v.has("apps_web_collapsed")) UiPrefs.saveAppsWebCollapsed(v.getBoolean("apps_web_collapsed"))
        if (v.has("timetable_grid")) UiPrefs.saveTimetableGrid(v.getBoolean("timetable_grid"))
        if (v.has("home_modules")) {
            val wanted = v.getString("home_modules").split(',')
            modules.forEach { UiPrefs.setHomeModule(it, true) }
            modules.filter { it !in wanted }.forEach { UiPrefs.setHomeModule(it, false) }
        }
        if (v.has("wallpaper_preset")) UiPrefs.saveWallpaperPreset(v.getString("wallpaper_preset"))
        if (v.has("wallpaper_dim")) UiPrefs.saveWallpaperDim(v.getInt("wallpaper_dim"))
        if (v.has("wallpaper_soft")) UiPrefs.saveWallpaperSoft(v.getInt("wallpaper_soft"))
        if (v.has("glass_opacity")) UiPrefs.saveGlassOpacity(v.getInt("glass_opacity"))
        if (v.has("reduce_motion")) UiPrefs.saveReduceMotion(v.getBoolean("reduce_motion"))
        if (v.has("reduce_transparency")) UiPrefs.saveReduceTransparency(v.getBoolean("reduce_transparency"))
        if (v.has("high_contrast")) UiPrefs.saveHighContrastOverride(v.getBoolean("high_contrast"))
        return labels.keys.count { v.has(it) }
    }
    fun suggestedFileName(): String = "qihe-preferences-${java.time.LocalDate.now()}.json"
}
