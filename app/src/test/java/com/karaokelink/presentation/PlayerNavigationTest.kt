package com.karaokelink.presentation

import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test

class PlayerNavigationTest {
    @Test fun blankInputOpensYouTube() { assertEquals(PlayerNavigation.HOME, PlayerNavigation.resolve(" ")) }
    @Test fun httpsSongLinkIsPreserved() {
        val url = "https://www.youtube.com/watch?v=hello&list=world"
        assertEquals(url, PlayerNavigation.resolve(url))
    }
    @Test fun thaiSongSearchIsEncodedAndIncludesKaraoke() {
        val url = PlayerNavigation.resolve("ทดสอบ & เพลง")!!
        assertEquals("ทดสอบ & เพลง คาราโอเกะ", URLDecoder.decode(url.substringAfter("search_query="), "UTF-8"))
    }
    @Test fun unsafeAndMalformedLinksAreRejected() {
        for (url in listOf("http://example.com", "javascript:alert(1)", "intent://watch", "data:text/html,hello", "https://", "https://user:pass@example.com"))
            assertNull(url, PlayerNavigation.resolve(url))
    }
}