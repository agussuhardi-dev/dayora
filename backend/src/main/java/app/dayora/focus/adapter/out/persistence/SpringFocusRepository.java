package app.dayora.focus.adapter.out.persistence;

import app.dayora.focus.application.FocusRepository;
import app.dayora.focus.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SpringFocusRepository implements FocusRepository {
  private final EntityManager em;
  private final JdbcTemplate jdbc;
  public SpringFocusRepository(EntityManager em, JdbcTemplate jdbc) { this.em=em; this.jdbc=jdbc; }

  public Optional<FocusSession> find(UUID owner, UUID id) { return entity(owner,id,false).map(FocusJpaEntity::toDomain); }
  public Optional<FocusSession> findActive(UUID owner) {
    return em.createQuery("from FocusJpaEntity where ownerId=:owner and state in :states order by startedAt desc", FocusJpaEntity.class)
        .setParameter("owner", owner).setParameter("states", Set.of(FocusSession.State.RUNNING, FocusSession.State.PAUSED))
        .setMaxResults(1).getResultStream().findFirst().map(FocusJpaEntity::toDomain);
  }
  public List<FocusSession> findRunningPomodoros() {
    return em.createQuery("from FocusJpaEntity where mode=:mode and state=:state order by runningSince", FocusJpaEntity.class)
        .setParameter("mode", FocusSession.Mode.POMODORO).setParameter("state", FocusSession.State.RUNNING)
        .getResultStream().map(FocusJpaEntity::toDomain).toList();
  }
  public List<FocusSession> findHistory(UUID owner) {
    return findHistory(owner, Integer.MAX_VALUE);
  }
  public List<FocusSession> findHistory(UUID owner, int limit) {
    return em.createQuery("from FocusJpaEntity where ownerId=:owner order by startedAt desc, id desc", FocusJpaEntity.class)
        .setParameter("owner", owner).setMaxResults(limit).getResultStream().map(FocusJpaEntity::toDomain).toList();
  }
  public List<FocusSession> findHistorySince(UUID owner, java.time.Instant since) {
    return em.createQuery("from FocusJpaEntity where ownerId=:owner and startedAt>=:since order by startedAt desc, id desc", FocusJpaEntity.class)
        .setParameter("owner", owner).setParameter("since", since).getResultStream().map(FocusJpaEntity::toDomain).toList();
  }
  public Optional<FocusSession> lock(UUID owner, UUID id) { return entity(owner,id,true).map(FocusJpaEntity::toDomain); }
  private Optional<FocusJpaEntity> entity(UUID owner, UUID id, boolean lock) {
    var query = em.createQuery("select s from FocusJpaEntity s where s.ownerId=:owner and s.id=:id", FocusJpaEntity.class)
        .setParameter("owner",owner).setParameter("id",id);
    if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
    return query.getResultStream().findFirst();
  }
  public FocusSession save(FocusSession session) {
    var existing = entity(session.ownerId(),session.id(),false);
    if (existing.isPresent()) existing.get().apply(session);
    else em.persist(new FocusJpaEntity(session));
    em.flush();
    return session;
  }
  public boolean ownsTask(UUID owner, UUID task) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from task where owner_id=? and id=? and deleted_at is null)",Boolean.class,owner,task));
  }
  public boolean taskInWorkspace(UUID owner, UUID task, UUID workspace) {
    return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from task where owner_id=? and id=? and workspace_id=? and deleted_at is null)",Boolean.class,owner,task,workspace));
  }
  public UUID workspaceOfTask(UUID owner, UUID task) {
    return jdbc.query("select workspace_id from task where owner_id=? and id=? and deleted_at is null",(r,n)->r.getObject(1,UUID.class),owner,task).stream().findFirst().orElseThrow(app.dayora.focus.application.FocusService.NotFound::new);
  }
  public void createTimeEntry(TimeEntry e) {
    jdbc.update("insert into time_entry(id,owner_id,workspace_id,focus_session_id,task_id,duration_seconds,started_at,ended_at) values(?,?,?,?,?,?,?,?)",
        e.id(),e.ownerId(),e.workspaceId(),e.focusSessionId(),e.taskId(),e.durationSeconds(),Timestamp.from(e.startedAt()),Timestamp.from(e.endedAt()));
  }
}
