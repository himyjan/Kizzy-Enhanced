package kizzy.gateway.test

import com.my.kizzy.domain.interfaces.NoOpLogger
import kizzy.gateway.DiscordWebSocketImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "RPC disappears after a while" bug.
 *
 * The old code used a time window to guess whether a SESSIONS_REPLACE was the
 * echo of our own PRESENCE_UPDATE. That guess was wrong in both directions: it
 * skipped the re-apply when Discord had really dropped the activity, and it
 * re-applied when the activity was still there.
 */
class SessionsReplaceTest {

    private val appId = "1522589499903840448"
    private val trackName = "【歌ってみた】シックス・フィート・アンダー covered by 夕凪機"

    private fun gateway(): DiscordWebSocketImpl = DiscordWebSocketImpl("token", NoOpLogger).apply {
        lastSentActivityApplicationId = appId
        lastSentActivityName = trackName
    }

    @Test
    fun `activity in the aggregated session counts as present`() {
        val json = """
            {"t":"SESSIONS_REPLACE","s":28,"op":0,"d":[
              {"status":"idle","session_id":"all","client_info":{"os":"unknown","client":"unknown"},
               "activities":[
                 {"type":4,"name":"Custom Status"},
                 {"type":2,"name":"$trackName","application_id":"$appId"}
               ]}
            ]}
        """.trimIndent()
        assertTrue(gateway().isOurActivityPresent(json))
    }

    @Test
    fun `activity only on a mobile session counts as present`() {
        val json = """
            {"t":"SESSIONS_REPLACE","s":28,"op":0,"d":[
              {"status":"idle","session_id":"all","client_info":{"os":"unknown","client":"unknown"},
               "activities":[{"type":4,"name":"Custom Status"}]},
              {"status":"idle","session_id":"abc","client_info":{"os":"android","client":"mobile"},
               "activities":[{"type":2,"name":"$trackName","application_id":"$appId"}]}
            ]}
        """.trimIndent()
        assertTrue(gateway().isOurActivityPresent(json))
    }

    @Test
    fun `session list without our activity means it was dropped`() {
        // This is the payload from the failing log: only desktop sessions and a
        // custom status, no android session holding the listening activity.
        val json = """
            {"t":"SESSIONS_REPLACE","s":28,"op":0,"d":[
              {"status":"idle","session_id":"all","client_info":{"os":"unknown","client":"unknown"},
               "activities":[{"type":4,"name":"Custom Status"}]},
              {"status":"idle","session_id":"714b","client_info":{"os":"windows","client":"desktop"},
               "activities":[]},
              {"status":"idle","session_id":"97f2","client_info":{"os":"osx","client":"desktop"},
               "activities":[{"type":4,"name":"Custom Status"}]}
            ]}
        """.trimIndent()
        assertFalse(gateway().isOurActivityPresent(json))
    }

    @Test
    fun `custom status with the same name is not our rich presence`() {
        val json = """
            {"t":"SESSIONS_REPLACE","s":1,"op":0,"d":[
              {"status":"idle","session_id":"all","client_info":{"os":"unknown","client":"unknown"},
               "activities":[{"type":4,"name":"$trackName"}]}
            ]}
        """.trimIndent()
        assertFalse(gateway().isOurActivityPresent(json))
    }

    @Test
    fun `cleared presence never reports itself as present`() {
        val gateway = DiscordWebSocketImpl("token", NoOpLogger)
        val json = """
            {"t":"SESSIONS_REPLACE","s":1,"op":0,"d":[
              {"status":"online","session_id":"all","client_info":{"os":"unknown","client":"unknown"},
               "activities":[]}
            ]}
        """.trimIndent()
        assertFalse(gateway.isOurActivityPresent(json))
    }
}
