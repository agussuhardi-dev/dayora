package app.dayora.sync;

import app.dayora.calendar.application.CalendarService;
import app.dayora.task.application.TaskService;
import app.dayora.task.domain.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.*;

@RestController @RequestMapping("/api/sync")
public class SyncController {
 private final SyncService service; SyncController(SyncService service){this.service=service;}
 @GetMapping("/bootstrap") SyncService.Page bootstrap(@RequestParam UUID deviceId,Principal p){return service.bootstrap(owner(p),deviceId);}
 @GetMapping("/pull") SyncService.Page pull(@RequestParam UUID deviceId,@RequestParam long cursor,@RequestParam(defaultValue="500") int limit,Principal p){return service.pull(owner(p),deviceId,cursor,limit);}
 @PostMapping("/push") SyncService.PushResult push(@RequestBody SyncService.Push request,Principal p){return service.push(owner(p),request);}
 private static UUID owner(Principal p){return UUID.fromString(p.getName());}
}

@Service class SyncService {
 record Change(long cursor,String entityType,UUID entityId,long revision,boolean deleted,JsonNode payload){}
 record Page(long cursor,List<Change> changes,boolean hasMore){}
 record Operation(UUID operationId,String entityType,UUID entityId,long baseRevision,String action,JsonNode payload){}
 record Push(UUID deviceId,List<Operation> operations){}
 record Result(UUID operationId,String entityType,UUID entityId,long revision,boolean deleted,JsonNode payload){}
 record PushResult(long cursor,List<Result> results){}
 static class Conflict extends SyncConflict {}
 private final JdbcTemplate jdbc; private final ObjectMapper json; private final TaskService tasks; private final CalendarService calendar;
 SyncService(JdbcTemplate jdbc,ObjectMapper json,TaskService tasks,CalendarService calendar){this.jdbc=jdbc;this.json=json;this.tasks=tasks;this.calendar=calendar;}
 @Transactional Page bootstrap(UUID owner,UUID device){
  tasks.lock(owner); touch(owner,device);
  var rows=jdbc.query("select entity_type,entity_id,revision,deleted,payload::text from sync_entity where owner_id=? order by entity_type,entity_id",(r,n)->new Change(0,r.getString(1),r.getObject(2,UUID.class),r.getLong(3),r.getBoolean(4),json.readTree(r.getString(5))),owner);
  return new Page(current(owner),rows,false);
 }
 @Transactional Page pull(UUID owner,UUID device,long cursor,int limit){if(cursor<0||limit<1||limit>1000)throw new IllegalArgumentException();touchExisting(owner,device);return page(owner,cursor,limit);}
 private Page page(UUID owner,long after,int limit){
  var rows=jdbc.query("select cursor,entity_type,entity_id,revision,deleted,payload::text from sync_change where owner_id=? and cursor>? order by cursor limit ?",(r,n)->new Change(r.getLong(1),r.getString(2),r.getObject(3,UUID.class),r.getLong(4),r.getBoolean(5),json.readTree(r.getString(6))),owner,after,limit+1);
  boolean more=rows.size()>limit; if(more)rows=new ArrayList<>(rows.subList(0,limit)); long cursor=rows.stream().mapToLong(Change::cursor).max().orElse(after); if(after==0&&cursor==0)cursor=current(owner); return new Page(cursor,rows,more);
 }
 @Transactional PushResult push(UUID owner,Push push){
  if(push==null||push.deviceId()==null||push.operations()==null||push.operations().isEmpty()||push.operations().size()>100)throw new IllegalArgumentException(); touchExisting(owner,push.deviceId()); tasks.lock(owner);
  var out=new ArrayList<Result>();
  for(var op:push.operations()){
   validate(op); String encoded=json.writeValueAsString(op); var prior=jdbc.query("select request::text,response::text from sync_receipt where owner_id=? and device_id=? and operation_id=?",(r,n)->List.of(r.getString(1),r.getString(2)),owner,push.deviceId(),op.operationId()).stream().findFirst();
   if(prior.isPresent()){if(!json.readTree(prior.get().get(0)).equals(json.readTree(encoded)))throw new Conflict();out.add(json.readValue(prior.get().get(1),Result.class));continue;}
   Result saved=apply(owner,op); Result result=new Result(op.operationId(),saved.entityType(),saved.entityId(),saved.revision(),saved.deleted(),saved.payload()); jdbc.update("insert into sync_receipt values(?,?,?,cast(? as jsonb),cast(? as jsonb))",owner,push.deviceId(),op.operationId(),encoded,json.writeValueAsString(result)); out.add(result);
  }
  return new PushResult(current(owner),out);
 }
 private Result apply(UUID owner,Operation op){
  JsonNode p=op.payload();
  var revisions=jdbc.query("select revision from sync_entity where owner_id=? and entity_type=? and entity_id=?",(r,n)->r.getLong(1),owner,op.entityType(),op.entityId());
  if((revisions.isEmpty()?0:revisions.getFirst())!=op.baseRevision())throw new Conflict();
  if(op.action().equals("DELETE")&&op.baseRevision()==0)throw new Conflict();
  String notes=nullable(p,"notes"); if(notes!=null&&notes.length()>10000)throw new IllegalArgumentException();
  if(op.entityType().equals("TASK")){
   Task t;if(op.action().equals("DELETE")){tasks.delete(owner,op.entityId(),op.baseRevision());return entity(owner,"TASK",op.entityId());}
   else if(op.baseRevision()==0)t=tasks.createIdempotent(owner,op.entityId(),text(p,"title"),nullable(p,"notes"),enumValue(p,"priority",Priority.class,Priority.NONE),date(p,"dueDate"),uuid(p,"workspaceId"),enumValue(p,"status",TaskStatus.class,TaskStatus.TODO),instant(p,"completedAt"),uuid(p,"statusId"));
   else t=tasks.update(owner,op.entityId(),op.baseRevision(),text(p,"title"),nullable(p,"notes"),enumValue(p,"priority",Priority.class,Priority.NONE),date(p,"dueDate"),instant(p,"completedAt"),uuid(p,"workspaceId"),enumValue(p,"status",TaskStatus.class,null),uuid(p,"statusId"));
   return entity(owner,"TASK",t.id());
  }
  if(op.entityType().equals("WORKSPACE")){
   if(op.action().equals("DELETE")) throw new IllegalArgumentException("workspace deletion is not supported");
   String name=text(p,"name").strip(); if(name.isEmpty()||name.length()>120)throw new IllegalArgumentException();
   if(op.baseRevision()==0)jdbc.update("insert into workspace(id,owner_id,name,created_at) values(?,?,?,now())",op.entityId(),owner,name);
   else jdbc.update("update workspace set name=? where owner_id=? and id=?",name,owner,op.entityId());
   return entity(owner,"WORKSPACE",op.entityId());
  }
  if(op.entityType().equals("SETTINGS")){
   if(op.action().equals("DELETE")||!op.entityId().equals(owner))throw new IllegalArgumentException();
   JsonNode settings=p.has("settings")?p.get("settings"):p; app.dayora.identity.adapter.in.web.SettingsController.validateSettings(settings); String value=json.writeValueAsString(settings); if(value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384)throw new IllegalArgumentException();
   jdbc.update("insert into account_settings(owner_id,revision,settings) values(?,?,cast(? as jsonb)) on conflict(owner_id) do update set revision=excluded.revision,settings=excluded.settings,updated_at=now()",owner,op.baseRevision()+1,value);
   return entity(owner,"SETTINGS",owner);
  }
  if(op.entityType().equals("FOCUS_SESSION")){
   if(op.action().equals("DELETE"))throw new IllegalArgumentException("focus deletion is not supported");
   UUID workspace=uuid(p,"workspaceId"); tasks.workspaces().require(owner,workspace); UUID task=uuid(p,"taskId");
   String state=text(p,"state"),mode=text(p,"mode"),phase=text(p,"phase"); Instant started=requiredInstant(p,"startedAt"),running=instant(p,"runningSince"),ended=instant(p,"endedAt");
   long duration=longValue(p,"durationSeconds",1,86400),active=longValue(p,"activeDurationSeconds",0,Long.MAX_VALUE); int count=(int)longValue(p,"completedFocusCount",0,Integer.MAX_VALUE),focus=(int)longValue(p,"focusDurationMinutes",1,1440),shortBreak=(int)longValue(p,"shortBreakDurationMinutes",1,1440),longBreak=(int)longValue(p,"longBreakDurationMinutes",1,1440),cycles=(int)(p.has("cyclesBeforeLongBreak")?longValue(p,"cyclesBeforeLongBreak",1,12):4);
   if(!Set.of("RUNNING","PAUSED","COMPLETED","CANCELLED").contains(state)||!Set.of("NORMAL","POMODORO").contains(mode)||!Set.of("FOCUS","SHORT_BREAK","LONG_BREAK").contains(phase)||(state.equals("RUNNING"))!=(running!=null)||(Set.of("COMPLETED","CANCELLED").contains(state))!=(ended!=null)||active>duration||started.isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException();
   if(op.baseRevision()==0)jdbc.update("insert into focus_session(id,owner_id,workspace_id,task_id,state,mode,phase,completed_focus_count,duration_seconds,active_duration_seconds,focus_duration_minutes,short_break_duration_minutes,long_break_duration_minutes,cycles_before_long_break,started_at,running_since,ended_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",op.entityId(),owner,workspace,task,state,mode,phase,count,duration,active,focus,shortBreak,longBreak,cycles,timestamp(started),timestamp(running),timestamp(ended));
   else if(jdbc.update("update focus_session set task_id=?,state=?,mode=?,phase=?,completed_focus_count=?,duration_seconds=?,active_duration_seconds=?,focus_duration_minutes=?,short_break_duration_minutes=?,long_break_duration_minutes=?,started_at=?,running_since=?,ended_at=? where owner_id=? and id=? and workspace_id=? and (? or cycles_before_long_break=?)",task,state,mode,phase,count,duration,active,focus,shortBreak,longBreak,timestamp(started),timestamp(running),timestamp(ended),owner,op.entityId(),workspace,!p.has("cyclesBeforeLongBreak"),cycles)!=1) throw new IllegalArgumentException("cannot move focus session or change cycle snapshot");
   return entity(owner,"FOCUS_SESSION",op.entityId());
  }
  if(op.entityType().equals("CALENDAR_EVENT")){
   if(op.action().equals("DELETE"))calendar.delete(owner,op.entityId(),op.baseRevision());
   else if(op.baseRevision()==0)calendar.create(owner,op.entityId(),uuid(p,"taskId"),text(p,"title"),requiredInstant(p,"startsAt"),requiredInstant(p,"endsAt"),text(p,"timezone"),nullable(p,"category"),nullable(p,"color"),uuid(p,"workspaceId"));
   else calendar.update(owner,op.entityId(),op.baseRevision(),uuid(p,"taskId"),text(p,"title"),requiredInstant(p,"startsAt"),requiredInstant(p,"endsAt"),text(p,"timezone"),nullable(p,"category"),nullable(p,"color"),uuid(p,"workspaceId"));
   return entity(owner,"CALENDAR_EVENT",op.entityId());
  }
  throw new IllegalArgumentException("unsupported entity type");
 }
 private Result entity(UUID owner,String type,UUID id){return jdbc.queryForObject("select entity_type,entity_id,revision,deleted,payload::text from sync_entity where owner_id=? and entity_type=? and entity_id=?",(r,n)->new Result(null,r.getString(1),r.getObject(2,UUID.class),r.getLong(3),r.getBoolean(4),json.readTree(r.getString(5))),owner,type,id);}
 private void touch(UUID o,UUID d){jdbc.update("insert into sync_device values(?,?,now()) on conflict(owner_id,id) do update set last_seen_at=now()",o,d);}
 private void touchExisting(UUID o,UUID d){if(jdbc.update("update sync_device set last_seen_at=now() where owner_id=? and id=?",o,d)!=1)throw new IllegalArgumentException("unknown device");}
 private long current(UUID o){return jdbc.query("select coalesce(max(cursor),0) from sync_change where owner_id=?",r->{r.next();return r.getLong(1);},o);}
 private static void validate(Operation o){if(o==null||o.operationId()==null||o.entityId()==null||o.baseRevision()<0||!("UPSERT".equals(o.action())||"DELETE".equals(o.action()))||o.entityType()==null||o.payload()==null)throw new IllegalArgumentException();}
 private static String text(JsonNode p,String k){var n=p.get(k);if(n==null||!n.isTextual())throw new IllegalArgumentException();return n.asText();} private static String nullable(JsonNode p,String k){var n=p.get(k);return n==null||n.isNull()?null:n.asText();}
 private static java.sql.Timestamp timestamp(Instant i){return i==null?null:java.sql.Timestamp.from(i);}
 private static long longValue(JsonNode p,String k,long min,long max){var n=p.get(k);if(n==null||!n.isIntegralNumber()||n.asLong()<min||n.asLong()>max)throw new IllegalArgumentException();return n.asLong();}
 private static UUID uuid(JsonNode p,String k){String s=nullable(p,k);return s==null?null:UUID.fromString(s);} private static Instant instant(JsonNode p,String k){String s=nullable(p,k);return s==null?null:Instant.parse(s);} private static Instant requiredInstant(JsonNode p,String k){var i=instant(p,k);if(i==null)throw new IllegalArgumentException();return i;} private static LocalDate date(JsonNode p,String k){String s=nullable(p,k);return s==null?null:LocalDate.parse(s);} private static <T extends Enum<T>> T enumValue(JsonNode p,String k,Class<T> c,T d){String s=nullable(p,k);return s==null?d:Enum.valueOf(c,s);}
}
