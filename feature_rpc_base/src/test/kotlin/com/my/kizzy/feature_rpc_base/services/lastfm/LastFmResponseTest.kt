package com.my.kizzy.feature_rpc_base.services.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LastFmResponseTest {

    @Test
    fun `decodes session from Last fm response envelope`() {
        val session = LastFMandLibreFM.decodeSessionResponse(
            """{"session":{"name":"listener","key":"session-key","subscriber":1}}"""
        )

        assertEquals("listener", session.name)
        assertEquals("session-key", session.key)
        assertEquals(1, session.subscriber)
    }

    @Test
    fun `reports Last fm API errors in successful HTTP response`() {
        try {
            LastFMandLibreFM.decodeSessionResponse("""{"error":9,"message":"Invalid session key"}""")
            fail("Expected Last.fm API error")
        } catch (error: LastFmException) {
            assertEquals(9, error.code)
            assertEquals("Invalid session key", error.errorMessage)
        }
    }

    @Test
    fun `uses independent Last fm compatible sessions and API endpoints`() {
        LastFMandLibreFM.sessionKey = "lastfm-session"
        LibreFMandLibreFM.sessionKey = "librefm-session"

        assertEquals("https://ws.audioscrobbler.com/2.0/", LastFMandLibreFM.API_BASE_URL)
        assertEquals("https://libre.fm/2.0/", LibreFMandLibreFM.API_BASE_URL)
        assertEquals("lastfm-session", LastFMandLibreFM.sessionKey)
        assertEquals("librefm-session", LibreFMandLibreFM.sessionKey)

        LastFMandLibreFM.sessionKey = null
        LibreFMandLibreFM.sessionKey = null
    }
}
