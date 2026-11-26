package app.dayora.workspace;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional public class WorkspaceService {
  private final JdbcTemplate jdbc;
  public WorkspaceService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
  public record Workspace(UUID id, UUID ownerId, String name) {}
  public UUID personal(UUID owner) {
    jdbc.update("insert into workspace(id,owner_id,name,created_at) values (?,?,?,current_timestamp) on conflict (owner_id,name) do nothing",UUID.randomUUID(),owner,"Personal");
    return jdbc.queryForObject("select id from workspace where owner_id=? and name='Personal'",UUID.class,owner);
  }
  public void require(UUID owner,UUID id) {
    if(id==null || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from workspace where owner_id=? and id=?)",Boolean.class,owner,id))) throw new IllegalArgumentException("workspace unavailable");
  }
  public UUID resolve(UUID owner,UUID id) { UUID result=id==null?personal(owner):id; require(owner,result); return result; }
  public List<Workspace> list(UUID owner) { personal(owner); return jdbc.query("select id,owner_id,name from workspace where owner_id=? order by created_at,id",(r,n)->new Workspace(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3)),owner); }
  public Workspace create(UUID owner,String name) {
    if(name==null || name.isBlank() || name.strip().length()>120) throw new IllegalArgumentException("workspace name must be 1-120 characters");
    UUID id=UUID.randomUUID(); String value=name.strip();
    jdbc.update("insert into workspace(id,owner_id,name,created_at) values (?,?,?,current_timestamp)",id,owner,value);
    return new Workspace(id,owner,value);
  }
}
