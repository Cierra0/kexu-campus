package com.kexu.campus.data

import kotlinx.serialization.Serializable
import java.util.UUID

/** 默认作息（可按学校后续在设置中替换）。 */
val DEFAULT_SECTION_TIMES: List<String> = listOf(
    "08:00-08:45", "08:55-09:40", "10:00-10:45", "10:55-11:40",
    "14:00-14:45", "14:55-15:40", "16:00-16:45", "16:55-17:40",
    "19:00-19:45", "19:55-20:40", "20:50-21:35", "21:45-22:30"
)

const val SECTION_COUNT = 12

/** 一次上课安排：星期 + 节次 + 周次 + 地点。 */
@Serializable
data class Session(
    val dayOfWeek: Int = 1,          // 1=周一 … 7=周日
    val startSection: Int = 1,
    val endSection: Int = 2,
    val weeks: String = "1-20",      // 例如 "1-8,10-16" / "1-16单"
    val location: String = "",
    val campus: String = ""
)

/** 一门课可以有多个上课时段。 */
@Serializable
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val teacher: String = "",
    val credits: Double = 0.0,
    val colorIndex: Int = 0,
    val note: String = "",
    val source: String = "custom",   // custom | imported | shared
    val sessions: List<Session> = emptyList()
)

@Serializable
data class Grade(
    val id: String = UUID.randomUUID().toString(),
    val termName: String = "",
    val courseName: String = "",
    val credit: Double = 0.0,
    val score: Double = 0.0,
    val gpa: Double? = null,
    val category: String = "必修",
    val examType: String = "正常考试",
    val createdAt: Long = System.currentTimeMillis()
)

/** 同学（课表快照）。 */
@Serializable
data class Friend(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    val courses: List<Course> = emptyList()
)

@Serializable
data class Settings(
    val schoolName: String = "东华理工大学",
    val termName: String = "",
    val startDate: String = "",       // yyyy-MM-dd，空表示未设置
    val totalWeeks: Int = 20,
    val showWeekend: Boolean = true,
    val shareName: String = "",
    val darkMode: Int = 0,            // 0 跟随系统 / 1 浅色 / 2 深色
    val sectionTimes: List<String> = DEFAULT_SECTION_TIMES,
    val monthlyBudget: Double = 0.0,   // 0 表示未设置
    val blockObserveAds: Boolean = false,
    val customBlockDomains: List<String> = emptyList(),
    val customAllowDomains: List<String> = emptyList(),
    val focusBlockedPackages: List<String> = emptyList(),
    val focusDurationMinutes: Int = 25,
    val lockAllowedPackages: List<String> = emptyList(),
    val lockDurationMinutes: Int = 60,
    val unlockDurationMinutes: Int = 5,
    val unlockMaxCount: Int = 2,
    val lockScheduleEnabled: Boolean = false,
    val lockScheduleHour: Int = 23,
    val lockScheduleMinute: Int = 0,
    val syncSchoolName: String = "",
    val syncEntryUrl: String = ""
)

/** 一条收支记录。 */
@Serializable
data class LedgerEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: Int = 0,                 // 0 支出 / 1 收入
    val amount: Double = 0.0,
    val category: String = "餐饮",
    val note: String = "",
    val date: String = ""              // yyyy-MM-dd
)

@Serializable
data class CampusData(
    val schemaVersion: Int = 3,
    val settings: Settings = Settings(),
    val courses: List<Course> = emptyList(),
    val grades: List<Grade> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val lockRecords: List<LockRecord> = emptyList()
)

/** 一次锁机的记录，用于周统计。 */
@Serializable
data class LockRecord(
    val id: String = UUID.randomUUID().toString(),
    val startAt: Long = System.currentTimeMillis(),
    val endAt: Long = System.currentTimeMillis(),
    val plannedMinutes: Int = 0,
    val actualMinutes: Int = 0,
    val usedUnlock: Int = 0,
    val endedEarly: Boolean = false
)

fun Course.isActiveIn(week: Int): Boolean = sessions.any { WeekRange.isActive(it.weeks, week) }

fun Course.daySessions(dayOfWeek: Int, week: Int): List<Session> =
    sessions.filter { it.dayOfWeek == dayOfWeek && WeekRange.isActive(it.weeks, week) }

val WEEKDAY_NAMES = listOf("一", "二", "三", "四", "五", "六", "日")
