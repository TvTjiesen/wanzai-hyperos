package com.muyao.wanzaihyperos.ui.tronclass

internal fun TronSub.savedStateKey(): String = when (this) {
    TronSub.None -> "home"
    TronSub.Rollcall -> "rollcall"
    TronSub.Scanner -> "scanner"
    TronSub.Resources -> "resources"
    is TronSub.Members -> "members:${course.id}"
    TronSub.Schedule -> "schedule"
    is TronSub.Course -> "course:${course.id}"
    is TronSub.Homework -> "homework:$courseId"
    is TronSub.HomeworkDetail -> "homework-detail:$courseId:$homeworkId"
}

internal data class TronBackStack(val entries: List<TronSub> = emptyList()) {
    val current: TronSub get() = entries.lastOrNull() ?: TronSub.None

    fun open(destination: TronSub): TronBackStack = when {
        destination == TronSub.None -> back()
        destination == current -> this
        // Recognition consumes the scanner so Back cannot reopen the same camera session.
        current == TronSub.Scanner && destination == TronSub.Rollcall ->
            copy(entries = entries.dropLast(1) + destination)
        else -> copy(entries = entries + destination)
    }

    fun back(): TronBackStack = if (entries.isEmpty()) this else copy(entries = entries.dropLast(1))
}
