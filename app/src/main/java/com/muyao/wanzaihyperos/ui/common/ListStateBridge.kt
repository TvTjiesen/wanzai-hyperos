package com.muyao.wanzaihyperos.ui.common

import com.muyao.wanzaihyperos.data.ListState

/**
 * 数据层的 [ListState] → 界面的 [PageState]。
 *
 * ### 为什么要有这个转换，而不是让界面直接读 ListState
 * A1 的界面是围绕 [PageState] 那个显式枚举写的（加载/内容/空/错误/离线/过期/登录失效），
 * 而 A2 的 [ListState] 是数据层的状态机。两者语义**一一对应**，但不该让每个页面
 * 各写一遍 `when` —— 写六遍就会有六处「空 vs 失败」判错的机会。
 *
 * ### 关键映射（正是最容易判错的三处）
 * - `FirstLoad` → [PageState.Loading]（**不是** Empty：还没有结论）
 * - `Empty` → [PageState.Empty]（服务端明确说没有）
 * - `Failed` → [PageState.Error]（**不是** Empty：这是「取不到」，不是「没有」）
 * - `Content` 且 `meta.stale` → [PageState.Stale]（有旧数据但本次刷新失败：
 *   页面该显示内容 + 「可能不是最新的」，**不该清空成错误页**）
 * - `SessionExpired` → [PageState.SessionExpired]（要引导登录，与网络失败分开）
 */
fun ListState<*>.toPageState(): PageState = when (this) {
    is ListState.FirstLoad -> PageState.Loading
    is ListState.Content -> if (meta.stale) PageState.Stale else PageState.Content
    is ListState.Empty -> if (meta.stale) PageState.Stale else PageState.Empty
    is ListState.Failed -> PageState.Error
    is ListState.SessionExpired -> PageState.SessionExpired
}
