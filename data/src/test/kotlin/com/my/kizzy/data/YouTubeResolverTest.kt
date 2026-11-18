package com.my.kizzy.data

import com.my.kizzy.data.rpc.YouTubeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class YouTubeResolverTest {

    @Test
    fun `YMusic video URLs become YouTube short links`() {
        assertEquals(
            "https://youtu.be/5CIlX6dcmZA",
            YouTubeResolver.toRpcUrl("https://ymusicapp.com/watch?v=5CIlX6dcmZA")
        )
        assertEquals(
            "https://youtu.be/1E1gmFdGCw",
            YouTubeResolver.toRpcUrl("https://www.ymusicapp.com/watch?v=1E1gmFdGCw")
        )
    }

    @Test
    fun `lookalike host is not treated as a YouTube URL`() {
        assertFalse(YouTubeResolver.isYoutubeFamilyUrl("https://notymusicapp.com/watch?v=5CIlX6dcmZA"))
        assertEquals(
            "https://notymusicapp.com/watch?v=5CIlX6dcmZA",
            YouTubeResolver.toRpcUrl("https://notymusicapp.com/watch?v=5CIlX6dcmZA")
        )
    }
}
