package com.my.kizzy.feature_rpc_base.services.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LastFmResponseTest {

    @Test
    fun `decodes session from Last fm response envelope`() {
        val session = LastFM.decodeSessionResponse(
            """{"session":{"name":"listener","key":"session-key","subscriber":1}}"""
        )

        assertEquals("listener", session.name)
        assertEquals("session-key", session.key)
        assertEquals(1, session.subscriber)
    }

    @Test
    fun `reports Last fm API errors in successful HTTP response`() {
        try {
            LastFM.decodeSessionResponse("""{"error":9,"message":"Invalid session key"}""")
            fail("Expected Last.fm API error")
        } catch (error: LastFmException) {
            assertEquals(9, error.code)
            assertEquals("Invalid session key", error.errorMessage)
        }
    }

    @Test
    fun `uses independent Last fm compatible sessions and API endpoints`() {
        LastFM.sessionKey = "lastfm-session"
        LibreFM.sessionKey = "librefm-session"

        assertEquals("https://ws.audioscrobbler.com/2.0/", LastFM.API_BASE_URL)
        assertEquals("https://libre.fm/2.0/", LibreFM.API_BASE_URL)
        assertEquals("lastfm-session", LastFM.sessionKey)
        assertEquals("librefm-session", LibreFM.sessionKey)

        LastFM.sessionKey = null
        LibreFM.sessionKey = null
    }
}
