package app.dayora.android

fun main() {
    check(keepBackgroundService(false, false).not())
    check(keepBackgroundService(false, true))
    check(keepBackgroundService(true, false))
    check(eligibleFocusTask("task-1", "task-1", "IN_PROGRESS", completed = false, pending = false))
    check(!eligibleFocusTask("task-1", "task-1", "TODO", completed = false, pending = false))
    check(!eligibleFocusTask("task-1", "task-1", "IN_PROGRESS", completed = false, pending = true))
    check(listOf("workspace-a", "workspace-a").filter { it == "workspace-a" }.size == 2)
    check(listOf("workspace-a", "workspace-b").filter { it == "workspace-a" } == listOf("workspace-a"))
    check(FocusConfirmation("session-1", "RUNNING", "pause").matches("session-1", "RUNNING"))
    check(!FocusConfirmation("session-1", "RUNNING", "pause").matches("session-1", "PAUSED"))
}
