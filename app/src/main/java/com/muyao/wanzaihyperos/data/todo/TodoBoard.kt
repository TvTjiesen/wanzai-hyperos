package com.muyao.wanzaihyperos.data.todo

import com.muyao.wanzaihyperos.data.cjh52.Cjh52Notice
import com.muyao.wanzaihyperos.data.portal.PortalBill
import com.muyao.wanzaihyperos.data.tronclass.TronTodo

/**
 * 多來源待辦（B6）。
 *
 * ### 為什麼要它
 *
 * 學校的「要辦的事」散在**好幾個互不相通的系統**裡：
 *
 * | 來源 | 內容 | 系統 |
 * |------|------|------|
 * | 學在城院 | **作業**（C2.1 計算機系統作業…） | TronClass（`mobile.hzcu.edu.cn`） |
 * | 門戶 | **流程審批**（請假、申請…） | 門戶自建（`iappdd.hzcu.edu.cn`） |
 *
 * 兩者**互補而非重複**（作業 ≠ 審批）。使用者要的是「**今天總共要辦什麼**」，
 * 而不是「先去 A 系統看一眼，再去 B 系統看一眼」。
 *
 * ### 計劃書的兩條硬要求
 *
 * 1. **按來源分組展示** —— 因為兩邊的「辦」法完全不同（作業要去交，審批要去批），
 *    混在一起排會讓人不知道該點哪個。分組也讓「某個來源掛了」一眼看得出來。
 * 2. **來源內去重** —— 同一個系統裡可能因為分頁重疊/重複下發而出現同一條兩次。
 *    **只在來源內去重，不跨來源** —— 兩邊 id 體系不同，跨來源「去重」會誤刪。
 *
 * ### 「未知總數不當 0」
 *
 * 這是 A2 就定下的紀律：取不到時顯示「—」而不是 `0`。
 * **`0` 是一個斷言**（「確實沒有」），而我們常常只是**還不知道**。
 * 所以 [TodoGroup.total] 是 `Int?`，`null` 表示未知。
 */

/** 待辦的來源。 */
enum class TodoSource(val label: String) {
    /** 學在城院的作業（TronClass）。 */
    TRONCLASS("学在城院 · 作业"),

    /** 門戶的流程審批。 */
    PORTAL("门户 · 审批"),
}

/**
 * 一條待辦（跨來源的統一視圖）。
 *
 * 刻意**不**把兩個來源的原始模型合成一個大類：
 * 它們的欄位語義不同（作業有截止時間、審批有處理狀態），
 * 強行合併會造出一堆「對 A 有意義對 B 永遠是 null」的欄位。
 * 這裡只保留**兩邊都成立**的資訊 + 一個指向原始資料的引用。
 */
data class TodoItem(
    val source: TodoSource,
    /**
     * 來源內唯一鍵。
     *
     * - 學在城院：`todo_list[].id`（數字，實測 19 條各有不同 id）
     * - 門戶：`billid`
     *
     * **只在同一來源內保證唯一**。
     */
    val key: String,
    /** 主標題。學在城院是作業名，門戶是事務名。 */
    val title: String,
    /** 副標題。學在城院是課程名，門戶是發起人。可能為空。 */
    val subtitle: String,
    /**
     * 截止/建立時間的**可比較值**（毫秒）。null = 該來源沒給或沒取到。
     *
     * ⚠️ 學在城院的 `end_time` 實測是 **null**（19 條全是），
     * 所以排序時不能假設它有值。
     */
    val sortTimeMillis: Long?,
    /** 時間的顯示文字（原始形態，如 `2026.10.14 23:30`）。空 = 沒有。 */
    val timeText: String,
)

/**
 * 一個來源的待辦分組。
 *
 * @param items 已去重、已排序的條目
 * @param total **服務端說的總數**；`null` = 未知（**不當 0**）
 * @param error 這個來源取數失敗時的原因（給使用者看的簡短文字）；null = 沒失敗
 */
data class TodoGroup(
    val source: TodoSource,
    val items: List<TodoItem>,
    val total: Int?,
    val error: String? = null,
    /**
     * 這個來源**還有下一頁**（我們只取了第一頁）。
     *
     * ★ 為什麼要這個旗標：只有第一頁時把條數當成總數顯示，是**虛報**。
     *   使用者看到「3」會以為「就這 3 件」，而事實是「至少 3 件」。
     *   所以 [countText] 在 hasMore 時顯示「3+」。
     */
    val hasMore: Boolean = false,
) {
    /** 這個來源是否取數失敗。 */
    val failed: Boolean get() = error != null

    /**
     * 標題上顯示的數量文字。
     *
     * **未知不當 0**：拿不到總數時顯示「—」。
     * 失敗時也不顯示 0 —— 那會讓人以為「這個來源沒有待辦」。
     * 只取了第一頁時顯示「N+」而不是「N」（見 [hasMore]）。
     */
    val countText: String
        get() = when {
            failed -> "—"
            total != null -> if (hasMore) "$total+" else total.toString()
            items.isNotEmpty() -> if (hasMore) "${items.size}+" else items.size.toString()
            else -> "0"
        }
}

/** 多來源待辦的整體狀態。 */
data class TodoBoard(
    val groups: List<TodoGroup>,
) {
    /** 所有來源都失敗了（用於決定要不要顯示整頁錯誤）。 */
    val allFailed: Boolean get() = groups.isNotEmpty() && groups.all { it.failed }

    /**
     * 已知的待辦總數；**任何一個來源未知就回 null**（不給一個偏小的數字）。
     *
     * ⚠️ 有來源 `hasMore`（只取了第一頁）時也回 null：
     *   那種情況下的和只是**下限**，說「共 N 條」是虛報
     *   （使用者會以為「就這 N 件」，其實是「至少 N 件」）。
     *   寧可說「總數未知」，也不報一個看起來精確其實偏小的數。
     */
    val knownTotal: Int?
        get() = if (groups.any { it.failed || it.total == null || it.hasMore }) null
        else groups.sumOf { it.total ?: 0 }
}

/**
 * 一個來源的取數結果（餵給 [TodoBoardBuilder]）。
 *
 * 用一個型別而不是「每來源三個參數」：來源會增加（B6 之後還可能接請假、報修），
 * 每加一個就多三個參數的話，`build` 很快會變成十几個 positional 引數的函式 ——
 * 那種簽名**呼叫時順序打錯也不會報錯**，是最貴的一種 bug。
 *
 * @param items 條目；**null = 這個來源沒取到**（失敗或未接入），
 *   與「取到了、就是空的」（`emptyList()`）是兩件事。
 */
data class SourceResult(
    val source: TodoSource,
    val items: List<TodoItem>?,
    /** 服務端說的總數；null = 未知。 */
    val total: Int? = null,
    /** 還有下一頁（只取了第一頁）。 */
    val hasMore: Boolean = false,
    /** 失敗/未接入的原因（給使用者看的簡短文字）。 */
    val error: String? = null,
)

/**
 * 把多來源的原始資料**合成**待辦看板。
 *
 * ### 去重規則（來源內）
 *
 * 用「來源 + key」去重，**保留第一次出現的**。
 * 為什麼保留第一條而不是最後一條：服務端通常是「重要的排前面」，
 * 而重複項往往是分頁重疊造成的尾巴 —— 保留第一條更符合它的意圖。
 *
 * ### 排序規則（來源內）
 *
 * 有時間的排前面（**按時間升序**，最緊急的在最上），沒時間的排後面。
 * 同為「有時間」時按 key 穩定排序，保證**兩次刷新順序一致**
 * （否則使用者每次進來看到的順序都在跳，會以為內容變了）。
 */
object TodoBoardBuilder {

    fun build(results: List<SourceResult>): TodoBoard = TodoBoard(
        results.map { r ->
            if (r.items == null) {
                // 取數失敗：**items 空但 error 有值** —— 界面上要能區分
                // 「這個來源沒有待辦」與「這個來源沒取到」。
                TodoGroup(
                    source = r.source,
                    items = emptyList(),
                    total = null,
                    error = r.error ?: "取数失败",
                )
            } else {
                TodoGroup(
                    source = r.source,
                    items = dedupeAndSort(r.items),
                    total = r.total,
                    error = r.error,
                    hasMore = r.hasMore,
                )
            }
        },
    )

    /**
     * 來源內去重 + 排序。抽出來是為了**可單測**（這是 B6 最容易寫錯的一段）。
     *
     * ⚠️ 去重的鍵是「**來源 + key**」而不是裸 `key`：
     *   本函式目前只被單來源呼叫，但用裸 key 去重等於把
     *   「同一來源」這個前提**藏在呼叫方** —— 哪天有人傳進混來源的清單，
     *   門戶的 billid `"1"` 就會把學在城院的 id `"1"` 吃掉，
     *   而那種丟失是**靜默的**（界面上只是少了一條，沒有任何報錯）。
     *   把前提寫進鍵裡，這個函式對任何輸入都是對的。
     */
    fun dedupeAndSort(items: List<TodoItem>): List<TodoItem> {
        val seen = HashSet<String>()
        val deduped = items.filter { seen.add("${it.source.name}/${it.key}") }
        return deduped.sortedWith(
            compareBy(
                // 有時間的在前
                { it.sortTimeMillis == null },
                // 時間升序（最緊急在上）
                { it.sortTimeMillis ?: Long.MAX_VALUE },
                // 同時間按來源 + key 穩定排序，保證兩次刷新順序一致
                //（否則使用者每次進來看到的順序都在跳，會以為內容變了）。
                { it.source.name },
                { it.key },
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// 從各來源的原始模型轉成統一視圖
// ---------------------------------------------------------------------------

/**
 * 學在城院的待辦 → 統一視圖。
 *
 * 契約（真機取證 `build/tron3.json`）：
 * ```jsonc
 * GET /api/todos → { todo_list: [{ course_code, course_id, course_name, course_type,
 *                                  end_time, id, is_locked, is_student,
 *                                  prerequisites[], title, type }] }
 * ```
 * 實測 19 條，`end_time` **全是 null** —— 所以時間顯示要能處理「沒有」。
 */
fun TronTodo.toTodoItem(): TodoItem = TodoItem(
    source = TodoSource.TRONCLASS,
    key = id.toString(),
    title = title,
    subtitle = courseName,
    sortTimeMillis = endTimeMillis,
    timeText = timeText,
)

/** 門戶的事務 → 統一視圖。 */
fun PortalBill.toTodoItem(): TodoItem = TodoItem(
    source = TodoSource.PORTAL,
    key = id,
    title = name,
    subtitle = ownerName,
    // 門戶給的是 `yyyy-MM-dd HH:mm:ss` 字串；解析不出來就當「沒有時間」
    //（**不猜一個時間**，那會讓排序看起來很確定其實是編的）。
    sortTimeMillis = parsePortalTime(createdAt),
    timeText = createdAt,
)

/**
 * 解析門戶的時間字串。
 *
 * 用 `java.time` 而不是 `SimpleDateFormat`：後者**不是執行緒安全**的，
 * 而這個函式會在列表解析時被反覆呼叫（倉庫跑在 IO 執行緒池上）。
 */
internal fun parsePortalTime(raw: String): Long? {
    if (raw.isBlank()) return null
    return runCatching {
        java.time.LocalDateTime
            .parse(raw.trim().replace(' ', 'T'))
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

/** 通知 → 統一視圖（B6 的第三個來源：學校發的通知也算「要看的」）。 */
fun Cjh52Notice.toTodoItem(): TodoItem = TodoItem(
    source = TodoSource.PORTAL,
    key = "notice-$id",
    title = title,
    subtitle = publisher,
    sortTimeMillis = sortKey.takeIf { it > 0 },
    timeText = "",
)
