package com.example.iqmemo.utils

import com.example.iqmemo.data.database.TaskMetadataEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class QuickDatePreset { UNDATED, TODAY, TOMORROW, CUSTOM }

object TaskDateUtils {
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)
    private val shortDateFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年 M月", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun formatTaskMeta(task: TaskMetadataEntity): String {
        val schedule = when (val day = task.dueEpochDay) {
            null -> "无日期待办"
            else -> buildString {
                append(LocalDate.ofEpochDay(day).format(dateFormatter))
                task.dueMinuteOfDay?.let {
                    append(' ')
                    append(LocalTime.of(it / 60, it % 60).format(timeFormatter))
                }
            }
        }
        return when {
            task.isCompleted -> "已完成 · $schedule"
            isOverdue(task) -> "已逾期 · $schedule"
            else -> schedule
        }
    }

    fun formatEditorDate(epochDay: Long?): String = epochDay?.let {
        LocalDate.ofEpochDay(it).format(dateFormatter)
    } ?: "设置日期"

    fun formatEditorTime(minuteOfDay: Int?): String = minuteOfDay?.let {
        LocalTime.of(it / 60, it % 60).format(timeFormatter)
    } ?: "设置时间"

    fun formatFilterDate(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)

    fun formatQuickTarget(epochDay: Long?): String = epochDay?.let {
        LocalDate.ofEpochDay(it).format(dateFormatter)
    } ?: "无日期"

    fun classifyQuickDate(
        epochDay: Long?,
        today: LocalDate = LocalDate.now()
    ): QuickDatePreset = when (epochDay) {
        null -> QuickDatePreset.UNDATED
        today.toEpochDay() -> QuickDatePreset.TODAY
        today.plusDays(1).toEpochDay() -> QuickDatePreset.TOMORROW
        else -> QuickDatePreset.CUSTOM
    }

    fun resolveQuickDate(
        preset: QuickDatePreset,
        today: LocalDate = LocalDate.now(),
        customEpochDay: Long? = null
    ): Long? = when (preset) {
        QuickDatePreset.UNDATED -> null
        QuickDatePreset.TODAY -> today.toEpochDay()
        QuickDatePreset.TOMORROW -> today.plusDays(1).toEpochDay()
        QuickDatePreset.CUSTOM -> customEpochDay
    }

    fun formatMonth(month: YearMonth): String = month.format(monthFormatter)

    fun isOverdue(task: TaskMetadataEntity, nowDate: LocalDate = LocalDate.now()): Boolean {
        if (task.isCompleted) return false
        val dueDay = task.dueEpochDay ?: return false
        if (dueDay < nowDate.toEpochDay()) return true
        if (dueDay > nowDate.toEpochDay()) return false
        val dueMinute = task.dueMinuteOfDay ?: return false
        val now = LocalTime.now()
        return dueMinute < now.hour * 60 + now.minute
    }
}
