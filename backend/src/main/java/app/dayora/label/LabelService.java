package app.dayora.label;

import app.dayora.realtime.AccountEvents;
import app.dayora.task.application.TaskService;
import app.dayora.workspace.WorkspaceService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional public class LabelService {
  private final JdbcTemplate jdbc;
  private final WorkspaceService workspaces;
  private final AccountEvents events;
  public LabelService(JdbcTemplate jdbc,WorkspaceService workspaces,AccountEvents events) { this.jdbc=jdbc;this.workspaces=workspaces;this.events=events; }
  public record Label(UUID id,UUID workspaceId,String name,String color) {}
  @Transactional(readOnly=true) public List<Label> list(UUID owner,UUID workspace) {
    workspaces.require(owner,workspace);
    return jdbc.query("select id,workspace_id,name,color from label where owner_id=? and workspace_id=? order by lower(name),id",(r,n)->new Label(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),owner,workspace);
  }
  public Label create(UUID owner,UUID workspace,String name,String color) {
    if(name==null || name.isBlank() || name.strip().length()>120) throw new IllegalArgumentException("label name must be 1-120 characters");
    if(color==null || !color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("label color must be #RRGGBB");
    events.lock(owner); workspaces.require(owner,workspace);
    Label label=new Label(UUID.randomUUID(),workspace,name.strip(),color);
    jdbc.update("insert into label(id,owner_id,workspace_id,name,color) values (?,?,?,?,?)",label.id(),owner,workspace,label.name(),color);
    events.invalidate(owner); return label;
  }
  public void delete(UUID owner,UUID workspace,UUID id) {
    events.lock(owner); workspaces.require(owner,workspace);
    if(jdbc.update("delete from label where owner_id=? and workspace_id=? and id=?",owner,workspace,id)==0) throw new NoSuchElementException();
    events.invalidate(owner);
  }
  private UUID taskWorkspace(UUID owner,UUID task) {
    return jdbc.query("select workspace_id from task where owner_id=? and id=? and deleted_at is null",(r,n)->r.getObject(1,UUID.class),owner,task).stream().findFirst().orElseThrow(TaskService.NotFound::new);
  }
  @Transactional(readOnly=true) public List<Label> taskLabels(UUID owner,UUID task) {
    taskWorkspace(owner,task);
    return jdbc.query("select l.id,l.workspace_id,l.name,l.color from label l join task_label t on t.label_id=l.id where t.owner_id=? and t.task_id=? order by lower(l.name),l.id",(r,n)->new Label(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4)),owner,task);
  }
  public List<Label> assign(UUID owner,UUID task,List<UUID> ids) {
    if(ids==null || ids.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("labelIds required");
    events.lock(owner); UUID workspace=taskWorkspace(owner,task);
    Set<UUID> selected=new HashSet<>(ids);
    List<Label> labels=list(owner,workspace).stream().filter(l->selected.contains(l.id())).toList();
    if(labels.size()!=selected.size()) throw new IllegalArgumentException("label unavailable in task workspace");
    jdbc.update("delete from task_label where owner_id=? and task_id=?",owner,task);
    for(Label label:labels) jdbc.update("insert into task_label(owner_id,workspace_id,task_id,label_id) values (?,?,?,?)",owner,workspace,task,label.id());
    events.invalidate(owner); return labels;
  }
}
