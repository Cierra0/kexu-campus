package com.kexu.campus.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * 全部数据存一个 JSON 文件：简单、可导出、无数据库迁移负担。
 * 写盘使用「临时文件 + rename」保证原子性。
 */
class CampusRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val file = File(appContext.filesDir, "campus.json")
    // 单线程写入，保证写盘顺序与内存状态一致
    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    private val _data = MutableStateFlow(load())
    val data: StateFlow<CampusData> = _data.asStateFlow()

    private fun load(): CampusData = runCatching {
        if (file.exists()) {
            json.decodeFromString(CampusData.serializer(), file.readText())
        } else {
            CampusData()
        }
    }.getOrElse { CampusData() }

    private fun persist(value: CampusData) {
        runCatching {
            val tmp = File(file.parentFile, "campus.json.tmp")
            tmp.writeText(json.encodeToString(CampusData.serializer(), value))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    fun update(transform: (CampusData) -> CampusData) {
        val next = transform(_data.value)
        _data.value = next
        scope.launch { persist(next) }
    }

    fun updateSettings(transform: (Settings) -> Settings) =
        update { it.copy(settings = transform(it.settings)) }

    fun upsertCourse(course: Course) = update { data ->
        val index = data.courses.indexOfFirst { it.id == course.id }
        val list = data.courses.toMutableList()
        if (index >= 0) list[index] = course else list.add(course)
        data.copy(courses = list)
    }

    fun deleteCourse(id: String) = update { data ->
        data.copy(courses = data.courses.filterNot { it.id == id })
    }

    fun addCourses(courses: List<Course>) = update { data ->
        val settings = withStartDateIfNeeded(data.settings, courses)
        data.copy(courses = data.courses + courses, settings = settings)
    }

    /** 教务同步：只替换上一次从教务同步来的课程，保留手动/导入/分享的课程。 */
    fun replaceSyncedCourses(courses: List<Course>) = update { data ->
        val syncedSources = setOf("jwglxt", "zhengfang", "qiangzhi")
        val settings = withStartDateIfNeeded(data.settings, courses)
        data.copy(
            courses = data.courses.filterNot { it.source in syncedSources } + courses,
            settings = settings
        )
    }

    /** 没设开学日期时，把本周周一设为第 1 周，避免导入了课程但课表页显示“先设置学期”。 */
    private fun withStartDateIfNeeded(settings: Settings, courses: List<Course>): Settings {
        if (settings.startDate.isNotBlank() || courses.isEmpty()) return settings
        val monday = LocalDate.now().with(DayOfWeek.MONDAY).toString()
        return settings.copy(startDate = monday, totalWeeks = maxOf(settings.totalWeeks, 20))
    }

    /** 教务同步成绩：按「学期+课程名」合并，保留已有 id。 */
    fun upsertGrades(grades: List<Grade>) = update { data ->
        val list = data.grades.toMutableList()
        for (grade in grades) {
            val index = list.indexOfFirst {
                it.termName == grade.termName && it.courseName == grade.courseName
            }
            if (index >= 0) list[index] = grade.copy(id = list[index].id) else list.add(grade)
        }
        data.copy(grades = list)
    }

    fun upsertGrade(grade: Grade) = update { data ->
        val index = data.grades.indexOfFirst { it.id == grade.id }
        val list = data.grades.toMutableList()
        if (index >= 0) list[index] = grade else list.add(grade)
        data.copy(grades = list)
    }

    fun deleteGrade(id: String) = update { data ->
        data.copy(grades = data.grades.filterNot { it.id == id })
    }

    fun upsertLedgerEntry(entry: LedgerEntry) = update { data ->
        val index = data.ledger.indexOfFirst { it.id == entry.id }
        val list = data.ledger.toMutableList()
        if (index >= 0) list[index] = entry else list.add(entry)
        data.copy(ledger = list)
    }

    fun deleteLedgerEntry(id: String) = update { data ->
        data.copy(ledger = data.ledger.filterNot { it.id == id })
    }

    fun addLockRecord(record: LockRecord) = update { data ->
        data.copy(lockRecords = (data.lockRecords + record).takeLast(200))
    }

    fun addFriend(friend: Friend) = update { data ->
        data.copy(friends = data.friends.filterNot { it.id == friend.id } + friend)
    }

    fun deleteFriend(id: String) = update { data ->
        data.copy(friends = data.friends.filterNot { it.id == id })
    }

    fun snapshotJson(): String = json.encodeToString(CampusData.serializer(), _data.value)

    fun importBackup(text: String): Boolean = runCatching {
        val parsed = json.decodeFromString(CampusData.serializer(), text)
        update { parsed }
        true
    }.getOrDefault(false)

    fun importCoursesAndSettings(courses: List<Course>, settings: Settings?) = update { data ->
        data.copy(courses = data.courses + courses, settings = settings ?: data.settings)
    }

    fun clearAll() = update { CampusData(settings = it.settings.copy(startDate = "")) }

    /** 载入示例数据，方便第一次打开就能看到效果。 */
    fun seedDemo() {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(5)
        val courses = listOf(
            Course(
                name = "高等数学", teacher = "李老师", credits = 5.0, colorIndex = 0,
                sessions = listOf(
                    Session(1, 1, 2, "1-16", "教1-201"),
                    Session(3, 3, 4, "1-16", "教1-201"),
                    Session(5, 5, 6, "1-16", "教1-201")
                )
            ),
            Course(
                name = "大学英语", teacher = "王老师", credits = 3.0, colorIndex = 1,
                sessions = listOf(
                    Session(2, 3, 4, "1-16", "外语楼-305"),
                    Session(4, 1, 2, "1-16", "外语楼-305")
                )
            ),
            Course(
                name = "数据结构", teacher = "陈老师", credits = 3.5, colorIndex = 2,
                sessions = listOf(
                    Session(2, 1, 2, "1-16", "信工楼-402"),
                    Session(4, 5, 6, "1-16", "信工楼-402")
                )
            ),
            Course(
                name = "电路分析", teacher = "赵老师", credits = 4.0, colorIndex = 3,
                sessions = listOf(
                    Session(1, 5, 6, "1-16", "实验楼-101"),
                    Session(3, 1, 2, "1-16", "实验楼-101")
                )
            ),
            Course(
                name = "体育（篮球）", teacher = "刘老师", credits = 1.0, colorIndex = 4,
                sessions = listOf(Session(5, 3, 4, "1-16", "体育馆"))
            ),
            Course(
                name = "中国近现代史纲要", teacher = "周老师", credits = 3.0, colorIndex = 5,
                sessions = listOf(Session(3, 7, 8, "1-16", "人文楼-201"))
            )
        )
        val grades = listOf(
            Grade(termName = "2025-2026-1", courseName = "高等数学", credit = 5.0, score = 88.0, category = "必修"),
            Grade(termName = "2025-2026-1", courseName = "大学英语", credit = 3.0, score = 92.0, category = "必修"),
            Grade(termName = "2025-2026-1", courseName = "程序设计基础", credit = 3.0, score = 85.0, category = "必修")
        )
        val today = LocalDate.now().toString()
        val ledger = listOf(
            LedgerEntry(type = 0, amount = 15.0, category = "餐饮", note = "食堂午饭", date = today),
            LedgerEntry(type = 0, amount = 6.0, category = "交通", note = "校车", date = today),
            LedgerEntry(type = 1, amount = 1500.0, category = "生活费", note = "本月生活费", date = today)
        )
        update {
            it.copy(
                settings = it.settings.copy(
                    startDate = monday.toString(),
                    termName = "2026-2027-1",
                    totalWeeks = 20,
                    schoolName = it.settings.schoolName.ifBlank { "东华理工大学" }
                ),
                courses = it.courses + courses,
                grades = it.grades + grades,
                ledger = it.ledger + ledger
            )
        }
    }
}
