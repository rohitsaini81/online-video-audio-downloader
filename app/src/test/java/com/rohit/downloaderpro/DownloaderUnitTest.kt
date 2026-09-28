package com.rohit.downloaderpro

import org.junit.Test

import org.junit.Assert.*

class DownloaderUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun extractUrl_youtubeShareWithTitle() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?si=abc123",
            extractUrl("Check this out https://youtu.be/dQw4w9WgXcQ?si=abc123"),
        )
    }

    @Test
    fun extractUrl_instagramReel() {
        assertEquals(
            "https://www.instagram.com/reel/C1a2b3c4/?igsh=xyz",
            extractUrl("https://www.instagram.com/reel/C1a2b3c4/?igsh=xyz"),
        )
    }

    @Test
    fun extractUrl_noLink() {
        assertNull(extractUrl("just some text"))
    }
}
