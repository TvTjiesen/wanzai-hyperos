package com.muyao.wanzaihyperos.data.portal

import com.muyao.wanzaihyperos.net.HzcuEndpoints
import com.muyao.wanzaihyperos.net.HzcuException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class PortalBroadcastParserTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private val item = PortalBroadcastItem("7", "Synthetic announcement", "", "")

    @Test fun topLevelRowsPreserveFieldsAndDeduplicateIds() {
        val rows = PortalBroadcastParser.parseList(json("""{"data":null,"rows":[
            {"id":7,"title":"Synthetic announcement","describe":"Summary","sendtime":"2026-11-16"},
            {"id":"7","title":"Duplicate"},{"id":"a-b_2","title":"Second"}]}"""))
        assertEquals(2, rows.size)
        assertEquals(item.copy(description = "Summary", sendTime = "2026-11-16"), rows.first())
    }

    @Test fun onlyExplicitEmptyRowsMeanNoAnnouncements() {
        assertTrue(PortalBroadcastParser.parseList(json("""{"rows":[]}""")).isEmpty())
        for (raw in listOf("{}", """{"data":[]}""", """{"rows":null}""", """{"rows":[null]}""")) {
            assertThrows(HzcuException.Parse::class.java) { PortalBroadcastParser.parseList(json(raw)) }
        }
    }

    @Test fun invalidIdentifiersAndMissingTitlesFailClosed() {
        for (id in listOf("../7", "7/x", "7?token=x", "", "x".repeat(129))) {
            assertNull(HzcuEndpoints.portalBroadcastDetailPath(id))
            assertThrows(HzcuException.Parse::class.java) {
                PortalBroadcastParser.parseList(json("""{"rows":[{"id":"$id","title":"Synthetic"}]}"""))
            }
        }
        for (raw in listOf("""{"rows":[{"id":true,"title":"Synthetic"}]}""", """{"rows":[{"id":7}]}""")) {
            assertThrows(HzcuException.Parse::class.java) { PortalBroadcastParser.parseList(json(raw)) }
        }
    }

    @Test fun detailRequiresStringContentInsideData() {
        assertEquals("<p>Text</p>", PortalBroadcastParser.parseDetail(json("""{"data":{"content":"<p>Text</p>"}}"""), item).content)
        assertEquals("", PortalBroadcastParser.parseDetail(json("""{"data":{"content":""}}"""), item).content)
        for (raw in listOf("{}", """{"data":null}""", """{"data":{"content":7}}""")) {
            assertThrows(HzcuException.Parse::class.java) { PortalBroadcastParser.parseDetail(json(raw), item) }
        }
    }

    @Test fun localTextRenderingDoesNotExposeScriptOrStyle() {
        val text = PortalBroadcastParser.plainText("<style>privateCss</style><p>Visible &amp; text</p><script>privateCode()</script><img src='https://synthetic.invalid/x'>")
        assertTrue(text.contains("Visible & text"))
        assertFalse(text.contains("privateCss"))
        assertFalse(text.contains("privateCode"))
        assertFalse(text.contains("synthetic.invalid"))
    }
}
