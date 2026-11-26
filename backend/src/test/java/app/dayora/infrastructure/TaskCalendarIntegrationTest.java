package app.dayora.infrastructure;

import app.dayora.calendar.application.CalendarService;
import app.dayora.focus.application.FocusService;
import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.task.application.TaskService;
import app.dayora.task.domain.TaskStatus;
import app.dayora.workspace.WorkspaceService;
import app.dayora.realtime.AccountEvents;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="DAYORA_POSTGRES_TEST", matches="true")
class TaskCalendarIntegrationTest {
  @Autowired TaskService tasks;
  @Autowired CalendarService calendar;
  @Autowired FocusService focus;
  @Autowired WorkspaceService workspaces;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  @Autowired AccountEvents events;
  private UUID owner() { return accounts.create("task-"+UUID.randomUUID(),"integration-password","UTC",Account.Role.USER).id(); }

  @Test void tasksKeepWorkspaceStatusRelationsAndRevisionConflicts() {
    UUID owner=owner(), other=owner(), workspace=workspaces.personal(owner);
    var task=tasks.create(owner,"task",null,null,null);
    assertEquals(workspace,task.workspaceId());
    assertTrue(tasks.find(other,task.id()).isEmpty());
    var done=tasks.update(owner,task.id(),task.revision(),"done",null,null,null,Instant.now());
    assertEquals(TaskStatus.DONE,done.status());
    assertThrows(TaskService.Stale.class,()->tasks.update(owner,task.id(),task.revision(),"stale",null,null,null,null));
    var start=Instant.parse("2026-11-22T10:00:00Z");
    var event=calendar.create(owner,task.id(),"event",start,start.plusSeconds(3600),"UTC",null,null);
    assertEquals(task.id(),event.taskId());
    var updated=calendar.update(owner,event.id(),event.revision(),task.id(),"updated",start,start.plusSeconds(1800),"UTC",null,null);
    assertTrue(updated.revision()>event.revision());
    assertThrows(CalendarService.Stale.class,()->calendar.delete(owner,event.id(),event.revision()));
    assertEquals(task.id(),focus.start(owner,task.id(),60).taskId());
    calendar.delete(owner,event.id(),updated.revision());
    tasks.delete(owner,task.id(),done.revision());
    assertTrue(tasks.find(owner,task.id()).isEmpty());
  }

  @Test void invalidationCounterIsMonotonicAndAccountScoped() {
    UUID owner=owner(),other=owner();
    entityManager.flush();
    events.invalidate(owner); events.invalidate(owner); events.invalidate(other);
    assertEquals(3L,jdbc.queryForObject("select next_sequence from account_event_counter where owner_id=?",Long.class,owner));
    assertEquals(2L,jdbc.queryForObject("select next_sequence from account_event_counter where owner_id=?",Long.class,other));
  }
}
