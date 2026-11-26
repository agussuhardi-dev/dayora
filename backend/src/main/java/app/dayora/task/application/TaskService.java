package app.dayora.task.application;

import app.dayora.task.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional public class TaskService {
  private final TaskRepository repository; private final JdbcTemplate jdbc; private final app.dayora.realtime.AccountEvents events;
  public TaskService(TaskRepository repository,JdbcTemplate jdbc,app.dayora.realtime.AccountEvents events){this.repository=repository;this.jdbc=jdbc;this.events=events;}
  public Task create(UUID owner,String title,String notes,Priority priority,LocalDate dueDate){return create(owner,UUID.randomUUID(),title,notes,priority,dueDate);}
  public Task create(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate){return create(owner,id,title,notes,priority,dueDate,null,null,null);}
  public Task create(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate,UUID workspace,TaskStatus status,Instant completedAt){return create(owner,id,title,notes,priority,dueDate,workspace,status,completedAt,null,false);}
  public Task createIdempotent(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate,UUID workspace,TaskStatus status,Instant completedAt){return create(owner,id,title,notes,priority,dueDate,workspace,status,completedAt,null,true);}
  private Task create(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate,UUID workspace,TaskStatus status,Instant completedAt,UUID statusId,boolean idempotent){lock(owner);UUID w=workspaces().resolve(owner,workspace);var selected=selectStatus(owner,w,statusId,status,completedAt,null);TaskStatus st=selected.status();Task candidate=new Task(id,owner,title,notes,priority==null?Priority.NONE:priority,dueDate,completion(st,completedAt),1,Instant.now(),Instant.now(),null,w,st,selected.id());if(idempotent){var existing=repository.find(owner,id);if(existing.isPresent()){if(!same(existing.get(),candidate,completedAt!=null))throw new IdempotencyConflict();return existing.get();}}return saveLocked(owner,candidate,0,"CREATE");}
  private static boolean same(Task a,Task b,boolean compareCompletedAt){return Objects.equals(a.title(),b.title())&&Objects.equals(a.notes(),b.notes())&&a.priority()==b.priority()&&Objects.equals(a.dueDate(),b.dueDate())&&(!compareCompletedAt||Objects.equals(a.completedAt(),b.completedAt()))&&Objects.equals(a.workspaceId(),b.workspaceId())&&Objects.equals(a.statusId(),b.statusId());}
  public app.dayora.workspace.WorkspaceService workspaces(){return new app.dayora.workspace.WorkspaceService(jdbc);}
  @Transactional(readOnly=true) public Page<Task> list(UUID owner,Pageable pageable){return repository.findAll(owner,pageable);}
  @Transactional(readOnly=true) public Page<Task> list(UUID owner,UUID workspace,Pageable pageable){if(workspace==null)return list(owner,pageable);workspaces().require(owner,workspace);return repository.findAll(owner,workspace,pageable);}
  @Transactional(readOnly=true) public Task get(UUID owner,UUID id){return repository.find(owner,id).orElseThrow(NotFound::new);}
  public Task update(UUID owner,UUID id,long revision,String title,String notes,Priority priority,LocalDate dueDate,Instant completedAt){return update(owner,id,revision,title,notes,priority,dueDate,completedAt,null,null);}
  public Task update(UUID owner,UUID id,long revision,String title,String notes,Priority priority,LocalDate dueDate,Instant completedAt,UUID workspace,TaskStatus status){return update(owner,id,revision,title,notes,priority,dueDate,completedAt,workspace,status,null);}
  public Task update(UUID owner,UUID id,long revision,String title,String notes,Priority priority,LocalDate dueDate,Instant completedAt,UUID workspace,TaskStatus status,UUID statusId){lock(owner);Task old=get(owner,id);check(revision,old);if(workspace!=null&&!workspace.equals(old.workspaceId()))throw new IllegalArgumentException("cannot move task between workspaces");var selected=selectStatus(owner,old.workspaceId(),statusId,status,completedAt,old);TaskStatus st=selected.status();return save(owner,new Task(id,owner,title,notes,priority==null?Priority.NONE:priority,dueDate,completion(st,completedAt),old.revision(),old.createdAt(),Instant.now(),null,old.workspaceId(),st,selected.id()),revision,"UPDATE");}
  public Task create(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate,UUID workspace,TaskStatus status,Instant completedAt,UUID statusId){return create(owner,id,title,notes,priority,dueDate,workspace,status,completedAt,statusId,false);}
  public Task createIdempotent(UUID owner,UUID id,String title,String notes,Priority priority,LocalDate dueDate,UUID workspace,TaskStatus status,Instant completedAt,UUID statusId){return create(owner,id,title,notes,priority,dueDate,workspace,status,completedAt,statusId,true);}
  private record Selected(UUID id,TaskStatus status){}
  private Selected selectStatus(UUID owner,UUID workspace,UUID id,TaskStatus legacy,Instant at,Task old){
    if(id==null && legacy==null && at==null && old!=null)id=old.statusId();
    String category=switch(legacy==null?(at==null?TaskStatus.TODO:TaskStatus.DONE):legacy){case TODO->"UNSTARTED";case IN_PROGRESS->"STARTED";case DONE->"COMPLETED";case CANCELED->"CANCELED";};
    var rows=jdbc.query("select id,category,archived from task_status where owner_id=? and workspace_id=? and "+(id==null?"category=? and not archived order by is_default desc,position,id limit 1":"id=? for share"),(r,n)->{UUID sid=r.getObject(1,UUID.class);if(r.getBoolean(3)&&(old==null||!sid.equals(old.statusId())))throw new IllegalArgumentException("status archived");return new Selected(sid,switch(r.getString(2)){case "UNSTARTED"->TaskStatus.TODO;case "STARTED"->TaskStatus.IN_PROGRESS;case "COMPLETED"->TaskStatus.DONE;default->TaskStatus.CANCELED;});},owner,workspace,id==null?category:id);
    return rows.stream().findFirst().orElseThrow(()->new IllegalArgumentException("status unavailable"));
  }
  private static Instant completion(TaskStatus status,Instant at){return status==TaskStatus.DONE?(at==null?Instant.now():at):null;}
  public void delete(UUID owner,UUID id,long revision){Task old=get(owner,id);check(revision,old);save(owner,new Task(old.id(),owner,old.title(),old.notes(),old.priority(),old.dueDate(),old.completedAt(),old.revision(),old.createdAt(),Instant.now(),Instant.now(),old.workspaceId(),old.status(),old.statusId()),revision,"DELETE");}
  public void lock(UUID owner){ events.lock(owner); }
  public Optional<Task> find(UUID owner,UUID id){return repository.find(owner,id);}
  private Task save(UUID owner,Task task,long expected,String operation){lock(owner);return saveLocked(owner,task,expected,operation);}
  private Task saveLocked(UUID owner,Task task,long expected,String operation){Task saved=repository.save(task,expected);events.invalidate(owner);return saved;}
  private static void check(long expected,Task task){if(expected!=task.revision())throw new Stale();}
  public static class NotFound extends RuntimeException{} public static class Stale extends RuntimeException{} public static class IdempotencyConflict extends RuntimeException{}
}
