package app.dayora.task.application;

import app.dayora.task.domain.Task;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskRepository {
  Task save(Task task, long expectedRevision);
  Optional<Task> find(UUID ownerId, UUID id);
  Page<Task> findAll(UUID ownerId, Pageable pageable);
  default Page<Task> findAll(UUID ownerId, UUID workspaceId, Pageable pageable) { throw new UnsupportedOperationException(); }
}
