package moe.ouom.neriplayer.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PagerTabSafetyTest {

    @Test
    fun `shrink from 4 tabs to 3 clamps index 3 to 2`() {
        assertEquals(2, safePageIndex(3, 3))
    }

    @Test
    fun `shrink from 6 tabs to 5 clamps index 5 to 4`() {
        assertEquals(4, safePageIndex(5, 5))
    }

    @Test
    fun `index 5 with count 5 clamps to 4`() {
        assertEquals(4, safePageIndex(5, 5))
    }

    @Test
    fun `empty list never indexes`() {
        assertEquals(0, safePageIndex(0, 0))
        assertEquals(0, safePageIndex(3, 0))
        assertEquals(0, safePageIndex(-1, 0))
    }

    @Test
    fun `negative page clamps to zero`() {
        assertEquals(0, safePageIndex(-1, 3))
    }

    @Test
    fun `in-range page passes through`() {
        assertEquals(0, safePageIndex(0, 3))
        assertEquals(1, safePageIndex(1, 3))
        assertEquals(2, safePageIndex(2, 3))
    }

    @Test
    fun `safeTabIndex delegates to list size`() {
        val tabs = listOf("a", "b", "c")
        assertEquals(2, safeTabIndex(3, tabs))
        assertEquals(1, safeTabIndex(1, tabs))
        assertEquals(0, safeTabIndex(0, emptyList<String>()))
    }
}
