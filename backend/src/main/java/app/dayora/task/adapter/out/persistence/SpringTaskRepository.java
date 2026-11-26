package app.dayora.task.adapter.out.persistence;

import app.dayora.task.application.TaskRepository;
import app.dayora.task.domain.Task;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
interface JpaTasks extends JpaRepository<TaskJpaEntity, UUID> {
  Optional<TaskJpaEntity> findByOwnerIdAndIdAndDeletedAtIsNull(UUID ownerId, UUID id);
  Page<TaskJpaEntity> findByOwnerIdAndWorkspaceIdAndDeletedAtIsNull(UUID ownerId, UUID workspaceId, Pageable pageable);
  Page<TaskJpaEntity> findByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
}

@Repository
public class SpringTaskRepository implements TaskRepository {
  private final JpaTasks db;
  private final jakarta.persistence.EntityManager entityManager;
  public SpringTaskRepository(JpaTasks db, jakarta.persistence.EntityManager entityManager) { this.db = db; this.entityManager = entityManager; }
  public Task save(Task task, long expectedRevision) {
    if (expectedRevision == 0) {
      // Persist explicitly: assigned UUID and initial revision 1 must not trigger merge.
      TaskJpaEntity created = new TaskJpaEntity(task);
      entityManager.persist(created);
      entityManager.flush();
      return created.toDomain();
    }
    TaskJpaEntity e = db.findByOwnerIdAndIdAndDeletedAtIsNull(task.ownerId(), task.id()).orElseThrow(app.dayora.task.application.TaskService.NotFound::new);
    if (e.revision != expectedRevision) throw new org.springframework.orm.ObjectOptimisticLockingFailureException(TaskJpaEntity.class, task.id());
    e.apply(task);
    entityManager.flush();
    return e.toDomain();
  }
  public Optional<Task> find(UUID owner, UUID id) { return db.findByOwnerIdAndIdAndDeletedAtIsNull(owner, id).map(TaskJpaEntity::toDomain); }
  public Page<Task> findAll(UUID owner, UUID workspace, Pageable p) { return db.findByOwnerIdAndWorkspaceIdAndDeletedAtIsNull(owner,workspace,p).map(TaskJpaEntity::toDomain); }
  public Page<Task> findAll(UUID owner, Pageable p) { return db.findByOwnerIdAndDeletedAtIsNull(owner, p).map(TaskJpaEntity::toDomain); }
}
