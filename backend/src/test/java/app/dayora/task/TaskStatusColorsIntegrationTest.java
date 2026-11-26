package app.dayora.task;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.workspace.WorkspaceService;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TaskStatusColorsIntegrationTest {
 @Autowired AccountService accounts;
 @Autowired WorkspaceService workspaces;
 @Autowired JdbcTemplate jdbc;
 @Autowired EntityManager entityManager;
 @Test void newWorkspaceSeedsDistinctColorsAndOneDefault(){
  var owner=accounts.create("colors-"+UUID.randomUUID(),"integration-password","UTC",Account.Role.USER).id();
  entityManager.flush();
  var workspace=workspaces.personal(owner);
  var expected=Map.of("UNSTARTED","#6d5dfc","STARTED","#3b82f6","COMPLETED","#10b981","CANCELED","#ef4444");
  var statuses=jdbc.queryForList("select category,color,is_default from task_status where workspace_id=?",workspace);
  assertEquals(4,statuses.size());
  statuses.forEach(s->assertEquals(expected.get(s.get("category")),s.get("color")));
  assertEquals(1,statuses.stream().filter(s->Boolean.TRUE.equals(s.get("is_default"))).count());
 }
}
