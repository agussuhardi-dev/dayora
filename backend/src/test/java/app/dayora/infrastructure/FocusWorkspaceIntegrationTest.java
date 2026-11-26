package app.dayora.infrastructure;

import app.dayora.focus.application.FocusService;
import app.dayora.focus.domain.FocusSession;
import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.workspace.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named="DAYORA_POSTGRES_TEST", matches="true")
class FocusWorkspaceIntegrationTest {
  @Autowired FocusService focus;
  @Autowired WorkspaceService workspaces;
  @Autowired AccountService accounts;
  @Autowired JdbcTemplate jdbc;
  private UUID owner() {
    String name="focus-"+UUID.randomUUID();
    accounts.create(name,"integration-password","UTC",Account.Role.USER);
    return accounts.byUsername(name).id();
  }
  @Test void workspaceSurvivesCompletionAndPomodoroNext() {
    UUID owner=owner(),workspace=workspaces.create(owner,"Work").id();
    var session=focus.start(owner,workspace,null,FocusSession.Mode.POMODORO,null,1,1,1);
    var completed=focus.transition(owner,session.id(),FocusSession.Action.COMPLETE);
    assertEquals(workspace,completed.workspaceId());
    assertEquals(workspace,jdbc.queryForObject("select workspace_id from time_entry where focus_session_id=?",UUID.class,session.id()));
    var next=focus.next(owner,session.id());
    assertEquals(workspace,next.workspaceId());
    assertEquals(workspace,focus.get(owner,next.id()).workspaceId());
    assertEquals(2,focus.history(owner,workspace).size());
    assertTrue(focus.history(owner,workspaces.personal(owner)).isEmpty());
  }
  @Test void legacyUsesPersonal() {
    UUID owner=owner();
    assertEquals(workspaces.personal(owner),focus.start(owner,null,60).workspaceId());
    assertTrue(focus.history(owner()).isEmpty());
  }
}
