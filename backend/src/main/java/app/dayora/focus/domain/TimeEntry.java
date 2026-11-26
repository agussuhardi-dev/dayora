package app.dayora.focus.domain;

import java.time.Instant;
import java.util.UUID;

public record TimeEntry(UUID id, UUID ownerId, UUID workspaceId, UUID focusSessionId, UUID taskId,
                        long durationSeconds, Instant startedAt, Instant endedAt) {
  public static TimeEntry completed(FocusSession session) {
    if (session.state() != FocusSession.State.COMPLETED) throw new IllegalArgumentException("Session not completed");
    return new TimeEntry(UUID.randomUUID(), session.ownerId(), session.workspaceId(), session.id(), session.taskId(),
        session.activeDurationSeconds(), session.startedAt(), session.endedAt());
  }
}
