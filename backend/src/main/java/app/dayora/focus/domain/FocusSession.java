package app.dayora.focus.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record FocusSession(UUID id, UUID ownerId, UUID workspaceId, UUID taskId, State state, Mode mode, Phase phase,
                           int completedFocusCount, long durationSeconds, long activeDurationSeconds,
                           int focusDurationMinutes, int shortBreakDurationMinutes, int longBreakDurationMinutes, int cyclesBeforeLongBreak,
                           Instant startedAt, Instant runningSince, Instant endedAt) {
  public enum State { RUNNING, PAUSED, COMPLETED, CANCELLED }
  public enum Mode { NORMAL, POMODORO }
  public enum Phase { FOCUS, SHORT_BREAK, LONG_BREAK }
  public enum Action { PAUSE, RESUME, COMPLETE, CANCEL }

  public FocusSession {
    if (id == null || ownerId == null || workspaceId == null || state == null || mode == null || phase == null || startedAt == null
        || cyclesBeforeLongBreak < 1 || cyclesBeforeLongBreak > 12 || durationSeconds < 1 || activeDurationSeconds < 0 || completedFocusCount < 0
        || focusDurationMinutes < 1 || focusDurationMinutes > 1440 || shortBreakDurationMinutes < 1
        || shortBreakDurationMinutes > 1440 || longBreakDurationMinutes < 1 || longBreakDurationMinutes > 1440)
      throw new IllegalArgumentException("Invalid focus session");
    if ((state == State.RUNNING) != (runningSince != null)
        || ((state == State.COMPLETED || state == State.CANCELLED) != (endedAt != null)))
      throw new IllegalArgumentException("Invalid focus timestamps");
  }

  public static FocusSession start(UUID owner, UUID workspace, UUID task, Mode mode, long duration, int focus, int shortBreak, int longBreak, int cyclesBeforeLongBreak, Instant now) {
    return new FocusSession(UUID.randomUUID(), owner, workspace, task, State.RUNNING, mode, Phase.FOCUS, 0, duration, 0, focus, shortBreak, longBreak, cyclesBeforeLongBreak, now, now, null);
  }

  public boolean expired(Instant now) { return state == State.RUNNING && activeDurationSeconds + Math.max(0, Duration.between(runningSince, now).getSeconds()) >= durationSeconds; }
  public FocusSession autoComplete(Instant now) { return finish(now, mode == Mode.POMODORO && phase == Phase.FOCUS); }

  public FocusSession transition(Action action, Instant now) {
    if (action == Action.COMPLETE && state == State.COMPLETED) return this;
    if (state == State.COMPLETED || state == State.CANCELLED || (action == Action.PAUSE && state != State.RUNNING)
        || (action == Action.RESUME && state != State.PAUSED)) throw new Conflict();
    if (action == Action.COMPLETE) return finish(now, false);
    State next = action == Action.PAUSE ? State.PAUSED : action == Action.RESUME ? State.RUNNING : State.CANCELLED;
    long active = activeDurationSeconds + (runningSince == null ? 0 : Math.max(0, Duration.between(runningSince, now).getSeconds()));
    return new FocusSession(id, ownerId, workspaceId, taskId, next, mode, phase, completedFocusCount, durationSeconds, active,
        focusDurationMinutes, shortBreakDurationMinutes, longBreakDurationMinutes, cyclesBeforeLongBreak, startedAt, next == State.RUNNING ? now : null, next == State.CANCELLED ? now : null);
  }

  private FocusSession finish(Instant now, boolean completedFocus) {
    long active = activeDurationSeconds + (runningSince == null ? 0 : Math.max(0, Duration.between(runningSince, now).getSeconds()));
    return new FocusSession(id, ownerId, workspaceId, taskId, State.COMPLETED, mode, phase, completedFocusCount + (completedFocus ? 1 : 0), durationSeconds, active,
        focusDurationMinutes, shortBreakDurationMinutes, longBreakDurationMinutes, cyclesBeforeLongBreak, startedAt, null, now);
  }
  public static class Conflict extends RuntimeException {}
}
