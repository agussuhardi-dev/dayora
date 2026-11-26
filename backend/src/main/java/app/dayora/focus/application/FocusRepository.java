package app.dayora.focus.application;

import app.dayora.focus.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusRepository {
  Optional<FocusSession> find(UUID owner, UUID id);
  Optional<FocusSession> findActive(UUID owner);
  List<FocusSession> findRunningPomodoros();
  List<FocusSession> findHistory(UUID owner);
  List<FocusSession> findHistorySince(UUID owner, java.time.Instant since);
  Optional<FocusSession> lock(UUID owner, UUID id);
  FocusSession save(FocusSession session);
  void createTimeEntry(TimeEntry entry);
  boolean ownsTask(UUID owner, UUID task);
  boolean taskInWorkspace(UUID owner, UUID task, UUID workspace);
  UUID workspaceOfTask(UUID owner, UUID task);
}
