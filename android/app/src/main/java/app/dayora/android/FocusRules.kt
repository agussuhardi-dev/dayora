package app.dayora.android

fun keepBackgroundService(focusActive:Boolean,alwaysBackground:Boolean)=focusActive||alwaysBackground

fun eligibleFocusTask(id: String, selectedId: String, status: String, completed: Boolean, pending: Boolean): Boolean =
    id.isNotBlank() && id == selectedId && (status.isEmpty() || status == "IN_PROGRESS") && !completed && !pending

data class FocusConfirmation(val sessionId: String, val state: String, val action: String) {
    fun matches(id: String?, currentState: String?): Boolean =
        sessionId == id && state == currentState && when (action) {
            "pause" -> state == "RUNNING"
            "complete" -> state == "RUNNING" || state == "PAUSED"
            else -> false
        }
}
