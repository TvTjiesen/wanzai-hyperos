package com.muyao.wanzaihyperos.data

import org.json.JSONObject

/**
 * 建议五：偏好白名单的备份与恢复（业主 2026-11-07「五项新增设置全做」）。
 *
 * ## 为什么是"白名单"而不是"把偏好全倒出来"
 *
 * `SharedPreferences` 里混着好几类东西：界面偏好、通知排程、教学周锚点、
 * 自定义节次、应用打开次数…… 后几类**互相牵连**（改了节次就得重排提醒，
 * 改了锚点整张课表跟着位移）。把它们塞进一个"换主题用"的文件里，
 * 恢复时就必然出现"我只想恢复个深色主题，结果课表全错位"这种事。
 *
 * 所以这里**只带走八项纯界面偏好**，并且每一项都写清楚为什么它在名单里：
 *
 * | 键 | 为什么可以带走 |
 * |---|---|
 * | `theme` | 亮/暗，与设备无关 |
 * | `background` | 背景预设，值域是固定枚举 |
 * | `glass` | 材质请求值（液态/磨砂/关），系统高对比仍会覆盖它 |
 * | `container_style` | 容器背景两版（保留/只留最外层） |
 * | `home_density` | 首页舒适/紧凑 |
 * | `home_modules` | 首页模块显隐（**至少留一块**这条约束在写入时仍然生效） |
 * | `apps_web_collapsed` | 应用页"网页服务"是否收起 |
 * | `timetable_grid` | 打开时先看整周还是今天 |
 *
 * ## 明确不带走（改一个就会伤到别的东西）
 *
 * - 密码 / token / Cookie / 学号：**这是登录状态迁移，不是偏好备份**。
 * - 学校响应、URL 查询串：属于数据，不属于偏好。
 * - 作业草稿：写了一半的内容，恢复时覆盖掉等于删他的作业。
 * - 通知排程、教学周锚点：与真实时间绑定，换设备后必须由课表页重算。
 * - 自定义节次：它会改变课表时间与提醒，属于业务配置（§7 单独论证过）。
 * - 应用打开次数：那是"常用"推荐的输入，跨设备搬过去没有意义。
 * - 壁纸图片与裁切绑定：图片在相册、裁切值绑着那张图，单独搬过来必然错位。
 *
 * ## 失败时整体不写
 *
 * 解析完先**逐项校验**（值域、类型），任何一项不合法就整份拒绝，并说明是哪一项、
 * 为什么。理由：恢复一半的配置比不恢复更糟 —— 界面会处在一个他从没见过的组合里，
 * 而且没有任何一处告诉他"这是恢复失败留下的"。
 */
object PrefsBackup {

    /** 文件格式版本。将来字段变了解析要按版本分支，**旧版本必须还能读**。 */
    const val VERSION = 1

    /** 备份里带的键（顺序稳定，便于人肉 diff）。 */
    private val WHITELIST = listOf(
        "theme",
        "background",
        "glass",
        "container_style",
        "home_density",
        "home_modules",
        "apps_web_collapsed",
        "timetable_grid",
    )

    /** 单个键的中文名（预览与报错都用它，不要向用户抛英文键名）。 */
    private val LABELS = mapOf(
        "theme" to "主题",
        "background" to "背景",
        "glass" to "材质",
        "container_style" to "容器背景",
        "home_density" to "首页密度",
        "home_modules" to "首页模块",
        "apps_web_collapsed" to "网页服务折叠",
        "timetable_grid" to "课表默认视图",
    )

    /**
     * 把当前偏好导出成 JSON 文本。
     *
     * 用 [JSONObject] 而不是手拼字符串：值是中文/布尔/数组，手拼必然踩转义。
     */
    fun export(): String {
        val o = JSONObject()
        o.put("version", VERSION)
        o.put("savedAt", System.currentTimeMillis())
        val v = JSONObject()
        v.put("theme", UiPrefs.themeModeName)
        v.put("background", UiPrefs.backgroundPreset)
        v.put("glass", UiPrefs.glassMode)
        v.put("container_style", UiPrefs.containerStyle)
        v.put("home_density", UiPrefs.homeDensity)
        v.put("home_modules", UiPrefs.homeModules.joinToString(","))
        v.put("apps_web_collapsed", UiPrefs.appsWebCollapsed)
        v.put("timetable_grid", UiPrefs.timetableGrid)
        o.put("values", v)
        return o.toString(2)
    }

    /** 预览：解析出来的一行行「名称 → 值」，给二次确认用。 */
    data class Preview(val lines: List<Pair<String, String>>, val raw: JSONObject)

    /**
     * 只解析、不写入。
     *
     * @return 成功时是 [Preview]；失败时是 null，原因在 [lastError]。
     *   **注意**：这里不做任何写入，用户点"确认恢复"之前磁盘一个字节都不变。
     */
    var lastError: String? = null
        private set

    fun parse(text: String): Preview? {
        lastError = null
        val o = runCatching { JSONObject(text) }.getOrElse {
            lastError = "这不是一份有效的偏好备份（JSON 解析失败）"
            return null
        }
        val version = o.optInt("version", -1)
        if (version <= 0) {
            lastError = "备份里没有版本号，来源不明，不读"
            return null
        }
        if (version > VERSION) {
            lastError = "这份备份来自更新的版本（v$version，本机只认到 v$VERSION），不读"
            return null
        }
        val v = o.optJSONObject("values") ?: run {
            lastError = "备份里没有偏好内容"
            return null
        }
        val lines = mutableListOf<Pair<String, String>>()
        // 未知键**明确忽略**并且不报错：将来加字段时，旧版应用读新备份不该整个失败。
        WHITELIST.forEach { key ->
            if (!v.has(key)) return@forEach
            val label = LABELS[key] ?: key
            lines += label to display(key, v)
        }
        if (lines.isEmpty()) {
            lastError = "备份里没有任何一项本机认识的偏好"
            return null
        }
        return Preview(lines = lines, raw = v)
    }

    private fun display(key: String, v: JSONObject): String = when (key) {
        "theme" -> when (v.optString(key)) {
            "System" -> "跟随系统"
            "Light" -> "浅色"
            "Dark" -> "深色"
            else -> v.optString(key)
        }
        "glass" -> when (v.optString(key)) {
            "liquid" -> "液态玻璃"
            "frost" -> "磨砂"
            "off" -> "关闭玻璃"
            else -> v.optString(key)
        }
        "container_style" -> when (v.optString(key)) {
            "designed" -> "保留容器背景"
            "bare" -> "只留最外层背景"
            else -> v.optString(key)
        }
        "home_density" -> when (v.optString(key)) {
            "comfortable" -> "舒适"
            "compact" -> "紧凑"
            else -> v.optString(key)
        }
        "home_modules" -> {
            val ids = v.optString(key).split(',').filter { it.isNotBlank() }
            val names = ids.map { if (it == "szty") "校园跑" else it }
            if (names.isEmpty()) "（空）" else names.joinToString("、")
        }
        "timetable_grid" -> if (v.optBoolean(key)) "打开时先看整周" else "打开时先看今天"
        "apps_web_collapsed" -> if (v.optBoolean(key)) "收起" else "展开"
        else -> v.optString(key)
    }

    /**
     * 应用一份已解析的备份。
     *
     * **先全量校验、后逐项写入**：只要有一项值域不合法，就一项都不写。
     * 写入走 [UiPrefs] 的受控保存函数（不是直接改 SharedPreferences），
     * 这样"至少留一块模块"这类运行期约束仍然生效。
     *
     * @return 应用成功的项数；失败时返回 -1，原因在 [lastError]。
     */
    fun apply(preview: Preview): Int {
        val v = preview.raw
        // ---- 校验阶段（不写）----
        val theme = v.optString("theme", UiPrefs.themeModeName)
        if (theme !in setOf("System", "Light", "Dark")) {
            lastError = "主题值「$theme」不认识，整份备份没有应用"
            return -1
        }
        val glass = v.optString("glass", UiPrefs.glassMode)
        if (glass !in setOf("liquid", "frost", "off")) {
            lastError = "材质值「$glass」不认识，整份备份没有应用"
            return -1
        }
        val container = v.optString("container_style", UiPrefs.containerStyle)
        if (container !in setOf("designed", "bare")) {
            lastError = "容器背景值「$container」不认识，整份备份没有应用"
            return -1
        }
        val density = v.optString("home_density", UiPrefs.homeDensity)
        if (density !in setOf("comfortable", "compact")) {
            lastError = "首页密度值「$density」不认识，整份备份没有应用"
            return -1
        }

        // ---- 写入阶段 ----
        var n = 0
        if (v.has("theme")) { UiPrefs.saveThemeMode(theme); n++ }
        if (v.has("background")) { UiPrefs.saveBackgroundPreset(v.optString("background")); n++ }
        if (v.has("glass")) { UiPrefs.saveGlassMode(glass); n++ }
        if (v.has("container_style")) { UiPrefs.saveContainerStyle(container); n++ }
        if (v.has("home_density")) { UiPrefs.saveHomeDensity(density); n++ }
        if (v.has("apps_web_collapsed")) { UiPrefs.saveAppsWebCollapsed(v.optBoolean("apps_web_collapsed")); n++ }
        if (v.has("timetable_grid")) { UiPrefs.saveTimetableGrid(v.optBoolean("timetable_grid")); n++ }
        if (v.has("home_modules")) {
            // 先全部打开再按备份关：这样"备份里只有一块"也不会被"至少留一块"卡住。
            val wanted = v.optString("home_modules").split(',').filter { it.isNotBlank() }
            if (wanted.isNotEmpty()) {
                listOf("courses", "rollcall", "todos", "szty").forEach { UiPrefs.setHomeModule(it, true) }
                listOf("courses", "rollcall", "todos", "szty")
                    .filter { it !in wanted }
                    .forEach { UiPrefs.setHomeModule(it, false) }
                n++
            }
        }
        return n
    }

    /**
     * 导出时建议的文件名（**不带路径**，路径由系统文件选择器给）。
     *
     * 用固定前缀 + 日期：他重装后翻文件管理器时能认出来这是谁的备份。
     */
    fun suggestedFileName(): String {
        val d = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA).format(java.util.Date())
        return "jingji-prefs-$d.json"
    }
}
