package app.dayora.android

fun main() {
    check(focusTimer(1500, 100, 1000, 11000) == FocusTimer(110, 1390, 110f / 1500))
    check(focusTimer(1500, 2000, null, 0) == FocusTimer(1500, 0, 1f))
    check(focusTimer(1500, 20, 5000, 1000).elapsed == 20L)
    check(focusTimer(0, -1, null, 0) == FocusTimer(0, 1, 0f))
}
