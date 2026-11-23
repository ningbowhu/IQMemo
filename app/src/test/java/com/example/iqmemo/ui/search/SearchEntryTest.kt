package com.example.iqmemo.ui.search

import org.junit.Assert.*
import org.junit.Test

class SearchEntryTest {
    private val page = SearchEntry(SearchEntry.Kind.NOTEBOOK, 1, "出差安排", "第一行\n周三领取资料，Meeting at 10:00。", 1)
    @Test fun titleBodyMiddleAndEnglishIgnoreCaseMatchWithoutSubmission() {
        assertTrue(page.matches("出差"))
        assertTrue(page.matches("领取资料"))
        assertTrue(page.matches("meeting"))
        assertTrue(page.matches("  10:00  "))
        assertFalse(page.matches("不存在"))
    }
    @Test fun blankAndSqlWildcardCharactersAreLiteral() {
        assertFalse(page.matches("  \n"))
        assertFalse(page.matches("%"))
        assertFalse(page.matches("_"))
        assertFalse(page.matches("' OR 1=1"))
    }
    @Test fun matchingPassageIsVisibleDeepInsideLongMemo() {
        val memo = SearchEntry(SearchEntry.Kind.MEMO, 1, "", "前文".repeat(400) + "检索目标" + "后文".repeat(400), 1)
        assertTrue(memo.matches("检索目标"))
        assertTrue(memo.excerpt("检索目标").contains("检索目标"))
        assertTrue(memo.excerpt("检索目标").startsWith("…"))
        assertTrue(memo.excerpt("检索目标").length <= 181)
    }
    @Test fun titleOnlyPageCanBeFoundWithEmptyBody() {
        assertTrue(page.copy(content = "").matches("安排"))
        assertEquals("", page.copy(content = "").excerpt("安排"))
    }
}
