package app.dayora.task.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Task(UUID id, UUID ownerId, String title, String notes, Priority priority,
                   LocalDate dueDate, Instant completedAt, long revision,
                   Instant createdAt, Instant updatedAt, Instant deletedAt, UUID workspaceId, TaskStatus status, UUID statusId) {
  public Task(UUID id,UUID ownerId,String title,String notes,Priority priority,LocalDate dueDate,Instant completedAt,long revision,Instant createdAt,Instant updatedAt,Instant deletedAt){this(id,ownerId,title,notes,priority,dueDate,completedAt,revision,createdAt,updatedAt,deletedAt,null,completedAt==null?TaskStatus.TODO:TaskStatus.DONE);}
  public Task(UUID id,UUID ownerId,String title,String notes,Priority priority,LocalDate dueDate,Instant completedAt,long revision,Instant createdAt,Instant updatedAt,Instant deletedAt,UUID workspaceId,TaskStatus status){this(id,ownerId,title,notes,priority,dueDate,completedAt,revision,createdAt,updatedAt,deletedAt,workspaceId,status,null);}
  public Task {
    if (id == null || ownerId == null) throw new IllegalArgumentException("id and ownerId required");
    if (title == null || title.isBlank() || title.length() > 240) throw new IllegalArgumentException("title must be 1-240 characters");
    if (priority == null) throw new IllegalArgumentException("priority required");
    if (revision < 1) throw new IllegalArgumentException("revision must be positive");
  }
  public static Task create(UUID ownerId, String title, String notes, Priority priority, LocalDate dueDate) {
    var now = Instant.now();
    return new Task(UUID.randomUUID(), ownerId, title, notes, priority == null ? Priority.NONE : priority, dueDate, null, 1, now, now, null);
  }
}
