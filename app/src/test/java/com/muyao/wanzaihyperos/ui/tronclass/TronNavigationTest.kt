package com.muyao.wanzaihyperos.ui.tronclass

import com.muyao.wanzaihyperos.data.tronclass.TronCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TronNavigationTest {
    private val course = TronCourse(17, "Synthetic course", "", "", "")

    @Test fun `nested homework returns through course to originating schedule`() {
        val coursePage = TronSub.Course(course)
        val homework = TronSub.Homework(course.id, course.name)
        val detail = TronSub.HomeworkDetail(course.id, 71, course.name, "Synthetic homework")
        var stack = TronBackStack().open(TronSub.Schedule).open(coursePage).open(homework).open(detail)
        assertEquals(detail, stack.current)
        stack = stack.back()
        assertEquals(homework, stack.current)
        stack = stack.back()
        assertEquals(coursePage, stack.current)
        stack = stack.back()
        assertEquals(TronSub.Schedule, stack.current)
        assertEquals(TronSub.None, stack.back().current)
    }

    @Test fun `duplicate destination does not insert another back step`() {
        val page = TronSub.Course(course)
        val stack = TronBackStack().open(page)
        assertSame(stack, stack.open(page))
        assertEquals(TronSub.None, stack.back().current)
    }

    @Test fun `legacy none navigation pops only the current destination`() {
        val stack = TronBackStack().open(TronSub.Schedule).open(TronSub.Course(course))
        assertEquals(TronSub.Schedule, stack.open(TronSub.None).current)
    }

    @Test fun `scanner system and toolbar back both return to tron home`() {
        val stack = TronBackStack().open(TronSub.Scanner)
        assertEquals(TronSub.Scanner, stack.current)
        assertEquals(TronSub.None, stack.back().current)
        assertEquals(TronSub.None, stack.open(TronSub.None).current)
    }

    @Test fun `recognized scan replaces camera so back cannot rescan the same code`() {
        val stack = TronBackStack().open(TronSub.Scanner).open(TronSub.Rollcall)
        assertEquals(listOf(TronSub.Rollcall), stack.entries)
        assertSame(stack, stack.open(TronSub.Rollcall))
        assertEquals(TronSub.None, stack.back().current)
    }

    @Test fun `recognized scan retains destinations below camera`() {
        val stack = TronBackStack().open(TronSub.Schedule)
            .open(TronSub.Scanner).open(TronSub.Rollcall)
        assertEquals(listOf(TronSub.Schedule, TronSub.Rollcall), stack.entries)
        assertEquals(TronSub.Schedule, stack.back().current)
    }

    @Test fun `members returns to the same course and then its originating schedule`() {
        val stack = TronBackStack().open(TronSub.Schedule)
            .open(TronSub.Course(course)).open(TronSub.Members(course))
        assertEquals(TronSub.Course(course), stack.back().current)
        assertEquals(TronSub.Schedule, stack.back().back().current)
        assertEquals(TronSub.None, TronBackStack().open(TronSub.Resources).back().current)
    }

    @Test fun `saved page state follows stable course identity rather than changing titles`() {
        assertEquals(TronSub.Course(course).savedStateKey(),
            TronSub.Course(course.copy(name = "Renamed course")).savedStateKey())
        assertEquals(TronSub.HomeworkDetail(17, 71, "Before", "Before").savedStateKey(),
            TronSub.HomeworkDetail(17, 71, "After", "After").savedStateKey())
        val keys = listOf(TronSub.None, TronSub.Scanner, TronSub.Rollcall, TronSub.Schedule,
            TronSub.Resources, TronSub.Course(course), TronSub.Members(course),
            TronSub.Homework(course.id, course.name),
            TronSub.HomeworkDetail(17, 71, "", ""), TronSub.HomeworkDetail(18, 71, "", ""),
            TronSub.Course(course.copy(id = 18))).map { it.savedStateKey() }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun `empty back is stable and a new root has no retained destinations`() {
        val root = TronBackStack()
        assertSame(root, root.back())
        assertTrue(root.entries.isEmpty())
        val nested = root.open(TronSub.Rollcall)
        assertEquals(TronSub.Rollcall, nested.current)
        assertEquals(TronSub.None, TronBackStack().current)
    }
}
