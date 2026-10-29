package moe.ouom.neriplayer.ui.screen.tab

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScreenYouTubeGateTest {

    @Test
    fun `classical puts Bilibili next to local in both regions`() {
        val expected = listOf(LibraryTab.LOCAL, LibraryTab.BILI, LibraryTab.FAVORITE, LibraryTab.YTMUSIC)
        assertEquals(expected, libraryTabDisplayOrder(isInternational = false, classical = true))
        assertEquals(expected, libraryTabDisplayOrder(isInternational = true, classical = true))
    }

    @Test
    fun `classical respects YouTube switch`() {
        assertEquals(
            listOf(LibraryTab.LOCAL, LibraryTab.BILI, LibraryTab.FAVORITE),
            libraryTabDisplayOrder(isInternational = true, youtubeEnabled = false, classical = true)
        )
    }

    @Test
    fun `classical shows Netease only with a signed in account`() {
        for (international in listOf(false, true)) {
            for (youtube in listOf(false, true)) {
                for (signedIn in listOf(false, true)) {
                    val tabs = libraryTabDisplayOrder(
                        isInternational = international,
                        youtubeEnabled = youtube,
                        classical = true,
                        neteaseLoggedIn = signedIn
                    )
                    val expected = listOf(LibraryTab.LOCAL, LibraryTab.BILI, LibraryTab.FAVORITE) +
                        (if (youtube) listOf(LibraryTab.YTMUSIC) else emptyList()) +
                        (if (signedIn) listOf(LibraryTab.NETEASE) else emptyList())
                    assertEquals(expected, tabs)
                    assertFalse(tabs.contains(LibraryTab.QQMUSIC))
                }
            }
        }
    }

    @Test
    fun `library tabs exclude YouTube when disabled`() {
        val tabs = libraryTabDisplayOrder(
            isInternational = true,
            youtubeEnabled = false
        )

        assertFalse(tabs.contains(LibraryTab.YTMUSIC))
        assertTrue(tabs.contains(LibraryTab.NETEASE))
        assertTrue(tabs.contains(LibraryTab.BILI))
    }
}
