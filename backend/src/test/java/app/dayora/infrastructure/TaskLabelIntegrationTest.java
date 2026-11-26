package app.dayora.infrastructure;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.label.LabelService;
import app.dayora.task.application.TaskService;
import app.dayora.task.domain.Priority;
import app.dayora.workspace.WorkspaceService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="DAYORA_POSTGRES_TEST",matches="true")
class TaskLabelIntegrationTest {
  @Autowired LabelService labels;
  @Autowired TaskService tasks;
  @Autowired WorkspaceService workspaces;
  @Autowired AccountService accounts;
  private UUID owner() {
    String name="labels-"+UUID.randomUUID(); accounts.create(name,"integration-password","UTC",Account.Role.USER); return accounts.byUsername(name).id();
  }
  @Test void labelsAreWorkspaceScopedAndAssignmentsFollowTaskLifetime() {
    UUID owner=owner(),workspace=workspaces.create(owner,"Work").id();
    UUID task=tasks.create(owner,UUID.randomUUID(),"Task",null,Priority.NONE,null,workspace,null,null).id();
    var label=labels.create(owner,workspace,"  Urgent ","#aAbBcC");
    assertEquals("Urgent",label.name()); assertEquals(List.of(label),labels.assign(owner,task,List.of(label.id())));
    assertEquals(List.of(label),labels.taskLabels(owner,task));
    assertThrows(IllegalArgumentException.class,()->labels.assign(owner,task,List.of(UUID.randomUUID())));
    labels.delete(owner,workspace,label.id()); assertTrue(labels.taskLabels(owner,task).isEmpty());
    tasks.delete(owner,task,1);
    assertThrows(TaskService.NotFound.class,()->labels.assign(owner,task,List.of()));
  }
  @Test void ownershipAndWorkspaceBoundariesRejected() {
    UUID owner=owner(),other=owner(),workspace=workspaces.create(owner,"Work").id();
    UUID task=tasks.create(owner,UUID.randomUUID(),"Task",null,Priority.NONE,null,workspace,null,null).id();
    var elsewhere=labels.create(owner,workspaces.personal(owner),"Urgent","#112233");
    assertThrows(IllegalArgumentException.class,()->labels.assign(owner,task,List.of(elsewhere.id())));
    assertThrows(IllegalArgumentException.class,()->labels.list(other,workspace));
    assertThrows(IllegalArgumentException.class,()->labels.create(other,workspace,"Urgent","#112233"));
    assertThrows(IllegalArgumentException.class,()->labels.delete(other,workspace,elsewhere.id()));
    assertThrows(TaskService.NotFound.class,()->labels.taskLabels(other,task));
    assertThrows(TaskService.NotFound.class,()->labels.assign(other,task,List.of()));
  }
  @Test void invalidInputsRejectedAndEmptyAssignmentClears() {
    UUID owner=owner(),workspace=workspaces.personal(owner);
    UUID task=tasks.create(owner,"Task",null,Priority.NONE,null).id();
    var label=labels.create(owner,workspace,"Urgent","#112233");
    assertEquals(List.of(label),labels.assign(owner,task,List.of(label.id(),label.id())));
    assertTrue(labels.assign(owner,task,List.of()).isEmpty());
    assertThrows(IllegalArgumentException.class,()->labels.create(owner,workspace," ","#112233"));
    assertThrows(IllegalArgumentException.class,()->labels.create(owner,workspace,"x".repeat(121),"#112233"));
    assertThrows(IllegalArgumentException.class,()->labels.create(owner,workspace,"Urgent","red"));
    assertThrows(IllegalArgumentException.class,()->labels.assign(owner,task,null));
    assertThrows(IllegalArgumentException.class,()->labels.assign(owner,task,Arrays.asList((UUID)null)));
  }
  @Test void duplicateNormalizedNamesRejected() {
    UUID owner=owner(),workspace=workspaces.create(owner,"Work").id();
    labels.create(owner,workspace,"urgent","#112233");
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->labels.create(owner,workspace,"URGENT","#445566"));
  }
}
