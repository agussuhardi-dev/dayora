package app.dayora.task.adapter.out.persistence;

import app.dayora.task.domain.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "task")
class TaskJpaEntity {
  @Id UUID id;
  @Column(name = "owner_id", nullable = false) UUID ownerId;
  @Column(name = "workspace_id", nullable = false) UUID workspaceId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) TaskStatus status;
  @Column(name="status_id",nullable=false) UUID statusId;
  @Column(nullable = false, length = 240) String title;
  @Column(columnDefinition = "text") String notes;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) Priority priority;
  @Column(name = "due_date") LocalDate dueDate;
  @Column(name = "completed_at") Instant completedAt;
  @Version @Column(nullable = false) long revision;
  @Column(name = "created_at", nullable = false) Instant createdAt;
  @Column(name = "updated_at", nullable = false) Instant updatedAt;
  @Column(name = "deleted_at") Instant deletedAt;
  protected TaskJpaEntity() {}
  TaskJpaEntity(Task t) { id = t.id(); ownerId = t.ownerId(); workspaceId=t.workspaceId(); status=t.status(); apply(t); revision = t.revision(); createdAt = t.createdAt(); }
  void apply(Task t) { statusId=t.statusId(); title = t.title(); notes = t.notes(); priority = t.priority(); dueDate = t.dueDate(); completedAt = t.completedAt(); status=t.status(); updatedAt = t.updatedAt(); deletedAt = t.deletedAt(); }
  Task toDomain() { return new Task(id, ownerId, title, notes, priority, dueDate, completedAt, revision, createdAt, updatedAt, deletedAt, workspaceId,status,statusId); }
}
