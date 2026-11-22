package com.muyao.wanzaihyperos.nav

import com.muyao.wanzaihyperos.data.portal.PortalParsers
import com.muyao.wanzaihyperos.net.HzcuEndpoints
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsArticleNavigationTest {
    private val articleId = "zUWxtTFge0gDZn3fZ-bTuQ"
    private val articleUrl = "https://mp.weixin.qq.com/s/$articleId"

    @Test fun portalNewsArticleReachesRestorableSecondaryRoute() {
        val payload = Json.parseToJsonElement("""
            {"code":200,"data":[{"id":"338558","title":"校园新闻",
            "createdate":"2026-10-28 16:54:10","linktitle":"$articleUrl",
            "blink":"http://ccpo.hzcu.edu.cn/art/2026/10/28/art_101_338558.html"}]}
        """) as JsonObject
        val news = PortalParsers.parseNewsList(payload).single()
        val target = WebTarget.fromUrl(HzcuEndpoints.portalOpenUrl(news.openUrl!!)!!)
        assertEquals(WebTarget.Kind.DIRECT, target.kind)
        assertEquals(articleUrl, target.address)
        val route = Route.Web(0, "校园新闻", target)
        val restored = Json.decodeFromString(Route.Web.serializer(), Json.encodeToString(Route.Web.serializer(), route))
        assertEquals(target, restored.target)
    }

    @Test fun publicArticleExceptionDoesNotAuthorizeOtherOpaquePathsOrEncodedPaths() {
        listOf(
            "https://example.org/s/$articleId",
            "https://mp.weixin.qq.com.example.org/s/$articleId",
            "https://mp.weixin.qq.com/private/$articleId",
            "$articleUrl/extra",
            "https://mp.weixin.qq.com/s/${articleId}x",
            "https://mp.weixin.qq.com/s/%7AUWxtTFge0gDZn3fZ-bTuQ",
        ).forEach { raw -> assertEquals(raw, WebTarget.Kind.BLOCKED, WebTarget.fromUrl(raw).kind) }
    }

    @Test fun publicArticleStillRejectsCredentialQueriesFragmentsAndAuthorities() {
        listOf(
            "$articleUrl?token=synthetic-secret",
            "$articleUrl?code=synthetic-secret",
            "$articleUrl?x=Ab9Cd8Ef7Gh6Ij5Kl4Mn3Op2",
            "$articleUrl#token=synthetic-secret",
            "https://user:secret@mp.weixin.qq.com/s/$articleId",
            "https://mp.weixin.qq.com:8443/s/$articleId",
        ).forEach { raw -> assertEquals(raw, WebTarget.Kind.BLOCKED, WebTarget.fromUrl(raw).kind) }
        assertEquals("$articleUrl?scene=21", WebTarget.fromUrl("$articleUrl?scene=21").address)
    }
}
