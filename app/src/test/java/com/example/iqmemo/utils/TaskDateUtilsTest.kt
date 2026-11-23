package com.example.iqmemo.utils

import com.example.iqmemo.data.database.TaskMetadataEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskDateUtilsTest {

    @Test
    fun `unfinished task before today is overdue`() {
        val today = LocalDate.of(2026, 8, 25)
        val task = TaskMetadataEntity(
            noteId = 1,
            dueEpochDay = today.minusDays(1).toEpochDay()
        )

        assertTrue(TaskDateUtils.isOverdue(task, today))
    }

    @Test
    fun `undated and completed tasks are not overdue`() {
        val today = LocalDate.of(2026, 8, 25)

        assertFalse(TaskDateUtils.isOverdue(TaskMetadataEntity(noteId = 1), today))
        assertFalse(
            TaskDateUtils.isOverdue(
                TaskMetadataEntity(
                    noteId = 2,
                    isCompleted = true,
                    dueEpochDay = today.minusDays(3).toEpochDay()
                ),
                today
            )
        )
    }

    @Test
    fun `quick date presets resolve relative to supplied today`() {
        val today = LocalDate.of(2026, 8, 25)

        assertNull(TaskDateUtils.resolveQuickDate(QuickDatePreset.UNDATED, today))
        assertEquals(
            today.toEpochDay(),
            TaskDateUtils.resolveQuickDate(QuickDatePreset.TODAY, today)
        )
        assertEquals(
            today.plusDays(1).toEpochDay(),
            TaskDateUtils.resolveQuickDate(QuickDatePreset.TOMORROW, today)
        )
    }

    @Test
    fun `quick date classification distinguishes custom dates`() {
        val today = LocalDate.of(2026, 8, 25)

        assertEquals(QuickDatePreset.UNDATED, TaskDateUtils.classifyQuickDate(null, today))
        assertEquals(
            QuickDatePreset.TODAY,
            TaskDateUtils.classifyQuickDate(today.toEpochDay(), today)
        )
        assertEquals(
            QuickDatePreset.TOMORROW,
            TaskDateUtils.classifyQuickDate(today.plusDays(1).toEpochDay(), today)
        )
        assertEquals(
            QuickDatePreset.CUSTOM,
            TaskDateUtils.classifyQuickDate(today.plusDays(8).toEpochDay(), today)
        )
    }
}
