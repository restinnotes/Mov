package moe.ouom.neriplayer.ui.util

/**
 * Clamp a dynamic pager/tab index synchronously before composition/measure.
 *
 * Must be called during composition, never rely on a later LaunchedEffect
 * to repair an already-invalid selectedTabIndex (ScrollableTabRow measures
 * synchronously and throws IndexOutOfBoundsException).
 *
 * Contract:
 * - count <= 0 -> 0 (caller must not index any list)
 * - page < 0 -> 0
 * - page >= count -> count - 1
 */
internal fun safePageIndex(page: Int, count: Int): Int {
    if (count <= 0) return 0
    if (page < 0) return 0
    if (page >= count) return count - 1
    return page
}

internal fun <T> safeTabIndex(currentPage: Int, tabs: List<T>): Int =
    safePageIndex(currentPage, tabs.size)
