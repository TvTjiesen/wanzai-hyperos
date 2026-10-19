package com.muyao.wanzaihyperos.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 界面偏好的**唯一落盘点**（SharedPreferences，明文）。
 *
 * ## 为什么现在才加
 *
 * 设置页要做成"真的记住东西"，就必须有落盘：在此之前 `themeMode` 只活在内存里
 * （`AppUiState`），重启就回到「跟随系统」，等于设置页是个摆设。
 * 这一份是**统一**的那一处 —— 不要再在某个页面里偷偷开第二个 SharedPreferences，
 * 否则会出现「一半偏好记得、一半不记得」，那种状态比全不记得更难解释。
 *
 * ## 红线：这里只放界面偏好，**绝不放凭据**
 *
 * 明文存储。凭据一律走 `SecureStore`（加密存储），日志/文件/剪贴板都不行
 * （`net/PortalCredentials.kt`、`session/SecureStore.kt` 那两条纪律）。
 *
 * ## 为什么是 object + Compose 状态
 *
 * 设置页要"改完立刻生效"，同时"下次冷启动也记得"。所以：
 * 值本身是 `mutableStateOf`（改了会重组），写盘在同一个 setter 里做掉，
 * 不给调用方"忘了 save"的机会。
 */
object UiPrefs {

    private const val FILE = "wanzai_ui"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_TIMETABLE_GRID = "timetable_grid"
    private const val KEY_PERIOD_TIMES = "period_times"

    /** 主题：跟随系统 / 浅色 / 深色。存的是枚举名，**不存序号**（序号会随枚举顺序漂）。 */
    var themeModeName by mutableStateOf("System")
        private set

    /** 課表默認排版：true = 三天窗口網格，false = 按天列表。 */
    var timetableGrid by mutableStateOf(true)
        private set

    /**
     * 節次時間表：每個元素是「上課時間,下課時間」，第 N 個對應第 N 節。
     *
     * ⚠️ **默認是空的**。學校接口沒有「節次→時間」表（`TronScheduleSlot` 檔頭那條），
     *   編一個默認值就是造數據 —— 所以沒填之前課表左列只標節次號，填了才顯示時間。
     */
    var periodTimes by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    private var loaded = false

    /** 冷启动时调一次（幂等）。 */
    fun load(context: Context) {
        holder = context.applicationContext
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        themeModeName = p.getString(KEY_THEME, "System") ?: "System"
        timetableGrid = p.getBoolean(KEY_TIMETABLE_GRID, true)
        periodTimes = (p.getString(KEY_PERIOD_TIMES, "") ?: "")
            .split("|")
            .mapNotNull { row ->
                val parts = row.split(",")
                if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
            }
    }

    fun saveThemeMode(name: String) {
        themeModeName = name
        prefs()?.edit()?.putString(KEY_THEME, name)?.apply()
    }

    fun saveTimetableGrid(grid: Boolean) {
        timetableGrid = grid
        prefs()?.edit()?.putBoolean(KEY_TIMETABLE_GRID, grid)?.apply()
    }

    fun savePeriodTimes(rows: List<Pair<String, String>>) {
        periodTimes = rows
        prefs()?.edit()?.putString(
            KEY_PERIOD_TIMES,
            rows.joinToString("|") { "${it.first},${it.second}" },
        )?.apply()
    }

    // 没 load 过时返回 null：单测/预览里不会真去碰 Android 存储。
    private var holder: Context? = null

    private fun prefs() = holder?.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
