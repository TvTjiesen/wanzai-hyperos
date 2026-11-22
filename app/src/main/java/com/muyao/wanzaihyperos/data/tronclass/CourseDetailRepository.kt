package com.muyao.wanzaihyperos.data.tronclass

import com.muyao.wanzaihyperos.data.ListMeta
import com.muyao.wanzaihyperos.data.ListState
import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import com.muyao.wanzaihyperos.data.repo.StaleGuard
import com.muyao.wanzaihyperos.data.repo.catchingActive
import com.muyao.wanzaihyperos.net.HzcuException
import com.muyao.wanzaihyperos.net.TronclassClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CourseDetailPart(val label: String) {
    DETAIL("课程信息"), SCORE("成绩"), ROLLCALL("出勤统计"), ATTENDANCE("出勤记录"),
    HOMEWORK("作业"), BULLETINS("课程公告"), EXAMS("考试"), TOPICS("讨论目录"),
}

/** Independent sections retain their last result while an owner-scoped refresh progresses. */
data class TronCourseDetailBundle(
    val course: TronCourse,
    val detail: TronCourseDetail?,
    val score: TronScore?,
    val rollcallScore: TronRollcallScore?,
    val attendance: List<TronAttendance>,
    val homeworks: List<TronHomework>,
    val bulletins: List<TronBulletin> = emptyList(),
    val exams: List<TronExam> = emptyList(),
    val topics: List<TronTopicCategory> = emptyList(),
    val pendingParts: Set<CourseDetailPart> = emptySet(),
    val partErrors: Map<CourseDetailPart, HzcuException> = emptyMap(),
) {
    val hasAnyData: Boolean
        get() = detail != null || score != null || rollcallScore != null ||
            attendance.isNotEmpty() || homeworks.isNotEmpty() ||
            bulletins.isNotEmpty() || exams.isNotEmpty() || topics.isNotEmpty()

    fun isUnconfirmed(part: CourseDetailPart): Boolean = part in pendingParts || part in partErrors
}

interface CourseDetailRepository {
    val state: StateFlow<ListState<TronCourseDetailBundle>>
    /** Show this course's summary or cached sections before reading its remaining details. */
    suspend fun load(course: TronCourse)
    suspend fun refresh()
}

class LiveCourseDetailRepository(
    private val client: TronclassClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val epoch: AccountEpoch = AccountEpoch(),
) : CourseDetailRepository {
    private val guard = StaleGuard(epoch)
    private val _state = MutableStateFlow<ListState<TronCourseDetailBundle>>(ListState.FirstLoad)
    override val state: StateFlow<ListState<TronCourseDetailBundle>> = _state.asStateFlow()
    private val inFlight = Mutex()
    private var current: TronCourse? = null
    private var activeReadToken: Long? = null
    private var cachedMyId: Int? = null
    private data class CourseKey(val owner: Long, val courseId: Int)
    private val snapshots = linkedMapOf<CourseKey, ListState.Content<TronCourseDetailBundle>>()

    init {
        epoch.registerReset {
            guard.invalidate()
            current = null
            activeReadToken = null
            cachedMyId = null
            snapshots.clear()
            _state.value = ListState.FirstLoad
        }
    }

    private fun rememberCurrent() {
        val content = _state.value as? ListState.Content ?: return
        val course = content.items.firstOrNull()?.course ?: return
        val key = CourseKey(epoch.token(), course.id)
        snapshots.remove(key)
        snapshots[key] = content.copy(meta = content.meta.copy(refreshing = false, loadingMore = false))
        while (snapshots.size > 6) snapshots.remove(snapshots.keys.first())
    }

    override suspend fun load(course: TronCourse) {
        if (course.id <= 0) return
        var requestToken: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (current?.id == course.id && activeReadToken == guard.current) return@publishData
            val token = guard.begin()
            current = course
            activeReadToken = null
            val cached = snapshots[CourseKey(epoch.token(), course.id)]
            val bundle = cached?.items?.firstOrNull()?.copy(course = course) ?: TronCourseDetailBundle(
                course, null, null, null, emptyList(), emptyList(),
                pendingParts = CourseDetailPart.values().toSet(),
            )
            _state.value = ListState.Content(listOf(bundle), cached?.meta ?: ListMeta())
            val updated = cached?.meta?.updatedAtMillis
            val fresh = updated != null && clock() - updated in 0L until 60_000L &&
                cached?.meta?.stale == false && bundle.pendingParts.isEmpty() && bundle.partErrors.isEmpty()
            if (!fresh) {
                activeReadToken = token
                requestToken = token
            }
        }) return
        refresh(requestToken ?: return, forceRefresh = false)
    }

    override suspend fun refresh() {
        var requestToken: Long? = null
        if (!epoch.publishData(epoch.token()) {
            if (current == null || activeReadToken == guard.current) return@publishData
            requestToken = guard.begin()
            activeReadToken = requestToken
        }) return
        refresh(requestToken ?: return, forceRefresh = true)
    }

    private suspend fun myStudentId(token: Long, forceRefresh: Boolean): Int? {
        cachedMyId?.let { return it }
        val id = client.profileUserId(forceRefresh)
        if (!guard.publish(token) { cachedMyId = id }) throw CancellationException("Course changed")
        return id
    }

    private suspend fun refresh(token: Long, forceRefresh: Boolean) {
        try {
            inFlight.withLock {
                if (!guard.isCurrent(token)) return
                val course = current ?: return
                if (!guard.publish(token) {
                    val content = _state.value as? ListState.Content ?: return@publish
                    val bundle = content.items.firstOrNull() ?: return@publish
                    _state.value = content.copy(
                        items = listOf(bundle.copy(pendingParts = CourseDetailPart.values().toSet(), partErrors = emptyMap())),
                        meta = content.meta.copy(refreshing = true),
                    )
                }) return

                suspend fun <T> readPart(
                    part: CourseDetailPart,
                    read: suspend () -> T,
                    merge: (TronCourseDetailBundle, T) -> TronCourseDetailBundle,
                ) {
                    if (!guard.isCurrent(token)) throw CancellationException("Course changed")
                    val result = catchingActive { read() }
                    if (!guard.publish(token) {
                        val content = _state.value as? ListState.Content ?: return@publish
                        val previous = content.items.firstOrNull() ?: return@publish
                        val error = result.exceptionOrNull()?.tronPartFailure()
                        val merged = result.fold(onSuccess = { merge(previous, it) }, onFailure = { previous })
                        val errors = if (error == null) previous.partErrors - part else previous.partErrors + (part to error)
                        _state.value = content.copy(items = listOf(merged.copy(
                            pendingParts = previous.pendingParts - part, partErrors = errors,
                        )))
                        rememberCurrent()
                    }) throw CancellationException("Course changed")
                }

                val id = course.id
                // Preserve the shared QPS gate; publish each result instead of waiting for all endpoints.
                readPart(CourseDetailPart.DETAIL, { client.courseDetail(id, forceRefresh) }) { b, v -> b.copy(detail = v) }
                readPart(CourseDetailPart.SCORE, { client.courseScore(id, forceRefresh) }) { b, v -> b.copy(score = v) }
                readPart(CourseDetailPart.ROLLCALL, { client.courseRollcallScore(id, forceRefresh) }) { b, v -> b.copy(rollcallScore = v) }
                readPart(CourseDetailPart.ATTENDANCE, {
                    val me = myStudentId(token, forceRefresh)
                        ?: throw HzcuException.Parse("尚未确认出勤记录所需的个人资料")
                    client.courseAttendance(id, me, forceRefresh)
                }) { b, v -> b.copy(attendance = v) }
                readPart(CourseDetailPart.HOMEWORK, { client.homeworks(id, forceRefresh = forceRefresh) }) { b, v -> b.copy(homeworks = v) }
                readPart(CourseDetailPart.BULLETINS, { client.courseBulletins(id, forceRefresh = forceRefresh) }) { b, v -> b.copy(bulletins = v) }
                readPart(CourseDetailPart.EXAMS, { client.courseExams(id, forceRefresh) }) { b, v -> b.copy(exams = v) }
                readPart(CourseDetailPart.TOPICS, { client.courseTopicCategories(id, forceRefresh) }) { b, v -> b.copy(topics = v) }
                guard.publish(token) {
                    val content = _state.value as? ListState.Content ?: return@publish
                    val bundle = content.items.firstOrNull() ?: return@publish
                    val error = bundle.partErrors.values.firstOrNull()
                    _state.value = content.copy(meta = content.meta.copy(
                        refreshing = false, stale = error != null, lastRefreshError = error,
                        updatedAtMillis = if (error == null) clock() else content.meta.updatedAtMillis,
                    ))
                }
            }
        } finally {
            guard.publish(token) {
                activeReadToken = null
                val content = _state.value as? ListState.Content
                if (content != null) _state.value = content.copy(meta = content.meta.copy(refreshing = false))
                rememberCurrent()
            }
        }
    }
}
