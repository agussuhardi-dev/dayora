package app.dayora.calendar.application;

import app.dayora.calendar.domain.CalendarEvent;
import app.dayora.task.application.TaskService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class CalendarService {
  private final CalendarRepository repository;
  private final TaskService tasks;
  private final app.dayora.realtime.AccountEvents events;
  public CalendarService(CalendarRepository repository, TaskService tasks, app.dayora.realtime.AccountEvents events) {
    this.repository = repository; this.tasks = tasks; this.events = events;
  }
  public CalendarEvent create(UUID owner, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color) {
    return create(owner, UUID.randomUUID(), task, title, starts, ends, timezone, category, color);
  }
  public CalendarEvent create(UUID owner, UUID id, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color) {
    return create(owner,id,task,title,starts,ends,timezone,category,color,null);
  }
  public CalendarEvent create(UUID owner, UUID id, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color, UUID workspace) {
    tasks.lock(owner); UUID w=tasks.workspaces().resolve(owner,workspace); validateTask(owner,task,w);
    var now = Instant.now();
    return save(new CalendarEvent(id, owner, task, title, starts, ends, timezone, category, color, 1, now, now, null,w), 0, "CREATE");
  }
  @Transactional(readOnly=true) public CalendarEvent get(UUID owner, UUID id) { return repository.find(owner,id).orElseThrow(NotFound::new); }
  @Transactional(readOnly=true) public List<CalendarEvent> list(UUID owner, Instant from, Instant to) {
    if (from == null || to == null || !to.isAfter(from) || Duration.between(from,to).compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("range must be positive and at most seven days");
    return repository.findBetween(owner,from,to);
  }
  public CalendarEvent update(UUID owner, UUID id, long revision, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color) {
    return update(owner,id,revision,task,title,starts,ends,timezone,category,color,null);
  }
  public CalendarEvent update(UUID owner, UUID id, long revision, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color, UUID workspace) {
    tasks.lock(owner); var old = get(owner,id); check(revision,old); if(workspace!=null&&!workspace.equals(old.workspaceId()))throw new IllegalArgumentException("cannot move event between workspaces"); validateTask(owner,task,old.workspaceId());
    return save(new CalendarEvent(id, owner, task, title, starts, ends, timezone, category, color, old.revision(), old.createdAt(), Instant.now(), null,old.workspaceId()),revision,"UPDATE");
  }
  public void delete(UUID owner, UUID id, long revision) {
    tasks.lock(owner); var old = get(owner,id); check(revision,old);
    var now = Instant.now();
    save(new CalendarEvent(id,owner,old.taskId(),old.title(),old.startsAt(),old.endsAt(),old.timezone(),old.category(),old.color(),old.revision(),old.createdAt(),now,now,old.workspaceId()),revision,"DELETE");
  }
  private void validateTask(UUID owner, UUID task, UUID workspace) { if(task!=null && tasks.find(owner,task).filter(t->workspace.equals(t.workspaceId())).isEmpty()) throw new IllegalArgumentException("task unavailable in workspace"); }
  public List<CalendarEvent> list(UUID owner,Instant from,Instant to,UUID workspace){if(workspace!=null)tasks.workspaces().require(owner,workspace);return list(owner,from,to).stream().filter(e->workspace==null||workspace.equals(e.workspaceId())).toList();}
  private static void check(long revision,CalendarEvent event) { if (revision != event.revision()) throw new Stale(); }
  private CalendarEvent save(CalendarEvent event,long expected,String operation) {
    tasks.lock(event.ownerId());
    var saved = repository.save(event,expected);
    events.invalidate(event.ownerId());
    return saved;
  }
  public static class NotFound extends RuntimeException {}
  public static class Stale extends RuntimeException {}
}
