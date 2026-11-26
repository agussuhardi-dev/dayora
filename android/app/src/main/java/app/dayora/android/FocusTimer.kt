package app.dayora.android

data class FocusTimer(val elapsed: Long, val remaining: Long, val progress: Float)

fun focusTimer(duration: Long, savedElapsed: Long, runningSinceMs: Long?, nowMs: Long): FocusTimer {
    val total = duration.coerceAtLeast(1)
    val elapsed = (savedElapsed.coerceAtLeast(0) + (runningSinceMs?.let { ((nowMs - it).coerceAtLeast(0) / 1000) } ?: 0)).coerceAtMost(total)
    return FocusTimer(elapsed, total - elapsed, elapsed.toFloat() / total)
}
