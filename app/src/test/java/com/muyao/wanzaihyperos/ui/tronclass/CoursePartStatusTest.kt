package com.muyao.wanzaihyperos.ui.tronclass

import com.muyao.wanzaihyperos.data.tronclass.CourseDetailPart
import com.muyao.wanzaihyperos.data.tronclass.TronCourse
import com.muyao.wanzaihyperos.data.tronclass.TronCourseDetailBundle
import com.muyao.wanzaihyperos.net.HzcuException
import org.junit.Assert.*
import org.junit.Test

class CoursePartStatusTest {
    private val empty = TronCourseDetailBundle(TronCourse(17, "Synthetic", "", "", ""),
        null, null, null, emptyList(), emptyList())

    @Test fun `pending is unknown rather than confirmed empty`() {
        val bundle = empty.copy(pendingParts = setOf(CourseDetailPart.HOMEWORK))
        assertTrue(coursePartStatus(bundle, CourseDetailPart.HOMEWORK)!!.contains("尚待确认"))
        assertTrue(bundle.isUnconfirmed(CourseDetailPart.HOMEWORK))
    }

    @Test fun `failure cannot become an empty result`() {
        val bundle = empty.copy(partErrors = mapOf(CourseDetailPart.HOMEWORK to HzcuException.Parse("synthetic")))
        assertTrue(coursePartStatus(bundle, CourseDetailPart.HOMEWORK)!!.contains("读取失败"))
        assertTrue(bundle.isUnconfirmed(CourseDetailPart.HOMEWORK))
    }

    @Test fun `only settled successful part allows confirmed empty wording`() {
        assertNull(coursePartStatus(empty, CourseDetailPart.HOMEWORK))
        assertFalse(empty.isUnconfirmed(CourseDetailPart.HOMEWORK))
    }
}
