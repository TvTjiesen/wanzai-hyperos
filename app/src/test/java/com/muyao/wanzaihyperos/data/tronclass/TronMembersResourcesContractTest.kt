package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronRequestPolicy
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TronMembersResourcesContractTest {
    private fun obj(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Malformed nonempty responses must not become Empty") } catch (_: HzcuException.Parse) { }
    }

    @Test fun membersReadOfficialEnrollmentAndUserFields() {
        val rows = TronMembersParser.parse(obj("""{"enrollments":[{"id":7,"roles":["student"],"aliases":[null,"", "助教"],"is_auditor":true,"user":{"id":8,"name":"合成成员","user_no":"synthetic","department":{"name":"合成院系"},"grade":{"name":"一年级"},"klass":{"name":"一班"}}}]}"""))
        assertEquals(1, rows.size)
        assertEquals(7, rows.single().enrollmentId)
        assertEquals("合成院系", rows.single().department)
        assertEquals(listOf("student"), rows.single().roles)
        assertEquals(listOf("助教"), rows.single().aliases)
        assertTrue(rows.single().auditor)
    }

    @Test fun onlyExplicitEmptyEnrollmentArrayIsEmpty() {
        assertTrue(TronMembersParser.parse(obj("""{"enrollments":[]}""")).isEmpty())
        listOf("{}", """{"students":[]}""", """{"enrollments":null}""", """{"enrollments":[{"id":1,"user":null}]}""").forEach { text -> rejected { TronMembersParser.parse(obj(text)) } }
    }

    @Test fun resourceQueryMatchesOfficialWireKeysAndSeparatesFolderAndFile() {
        val query = TronResourcesQuery(12, "a & 中文")
        val folder = query.conditions(true)
        val file = query.conditions(false)
        assertEquals(setOf("keyword","includeSlides","limitTypes","fileType","parentId","sourceType","sortPredicate","reverse","no-intercept"), folder.keys)
        assertEquals("MyResourcesFolder", folder.getValue("sourceType").jsonPrimitive.content)
        assertEquals(listOf("folder"), folder.getValue("limitTypes").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("MyResourcesFile", file.getValue("sourceType").jsonPrimitive.content)
        assertFalse(file.getValue("limitTypes").jsonArray.any { it.jsonPrimitive.content == "folder" })
        assertEquals(12, folder.getValue("parentId").jsonPrimitive.int)
        assertEquals("a & 中文", folder.getValue("keyword").jsonPrimitive.content)
        assertEquals("created_at", folder.getValue("sortPredicate").jsonPrimitive.content)
    }

    @Test fun personalResourcePagingUsesUploadsAndPages() {
        val data = TronResourcesParser.parse(obj("""{"uploads":[{"id":1,"name":"文件","type":"file","size":42,"created_at":"original-date"}],"page":1,"pages":2,"page_size":10}"""))
        assertTrue(data.hasMore)
        assertEquals(42L, data.uploads.single().size)
        assertEquals("original-date", data.uploads.single().createdAt)
        assertTrue(TronResourcesParser.parse(obj("""{"uploads":[],"page":1,"pages":0,"page_size":10}""")).uploads.isEmpty())
        // 真机捕获形状（2026-11-22）：空账户响应不带 page_size，缺省合法且不算结构异常。
        val empty = TronResourcesParser.parse(obj("""{"page":1,"pages":1,"uploads":[]}"""))
        assertTrue(empty.uploads.isEmpty())
        assertNull(empty.pageSize)
        assertFalse(empty.hasMore)
    }

    @Test fun invalidResourceShapeAndSharedDomainAreRejected() {
        listOf("{}", """{"resources":[],"page":1,"pages":0,"page_size":10}""", """{"uploads":[],"page":0,"pages":0,"page_size":10}""", """{"uploads":[{"id":1,"name":"x"}],"page":1,"pages":1,"page_size":10}""", """{"uploads":[],"page":1,"pages":1,"page_size":0}""").forEach { text -> rejected { TronResourcesParser.parse(obj(text)) } }
    }

    @Test fun officialFolderFirstMergePreservesEndCondition() {
        val folder = TronResource(1,"目录","folder",true,null,"")
        val files = (2..15).map { TronResource(it,"f$it","file",false,null,"") }
        val merged = TronResourcesParser.merge(TronResourcesPageData(listOf(folder),1,1,10), TronResourcesPageData(files,1,3,10))
        assertEquals(10, merged.uploads.size)
        assertEquals(folder, merged.uploads.first())
        assertTrue(merged.hasMore)
        // page_size 缺省时不做截断（上限未知），目录仍在前。
        val uncapped = TronResourcesParser.merge(TronResourcesPageData(listOf(folder),1,1,null), TronResourcesPageData(files,1,3,null))
        assertEquals(15, uncapped.uploads.size)
        assertEquals(folder, uncapped.uploads.first())
        assertNull(uncapped.pageSize)
    }

    @Test fun policyAllowsOnlyTwoExplicitContractsWithoutWideningDefault() {
        assertTrue(TronRequestPolicy.validContract("/api/course/7/enrollments?fields=id", obj("""{"enrollments":[]}""")))
        assertFalse(TronRequestPolicy.validContract("/api/course/7/enrollments", obj("{}")))
        assertTrue(TronRequestPolicy.validContract("/api/user/resources?page=1", obj("""{"uploads":[],"page":1,"pages":0,"page_size":10}""")))
        // 真机捕获形状（2026-11-22）：缺 page_size 的空响应是合法契约。
        assertTrue(TronRequestPolicy.validContract("/api/user/resources?page=1", obj("""{"page":1,"pages":1,"uploads":[]}""")))
        assertFalse(TronRequestPolicy.validContract("/api/user/resources?page=1", obj("""{"page":1,"pages":1,"uploads":[],"page_size":0}""")))
        assertFalse(TronRequestPolicy.validContract("/api/user/resources", obj("""{"uploads":[]}""")))
        assertFalse(TronRequestPolicy.validContract("/api/unknown", obj("""{"uploads":[],"page":1,"pages":0,"page_size":10}""")))
        assertFalse(TronRequestPolicy.validContract("/api/user/resources", obj("""{"code":403,"uploads":[],"page":1,"pages":0,"page_size":10}""")))
    }
}
