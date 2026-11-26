package app.dayora.task;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.workspace.WorkspaceService;
import app.dayora.task.application.TaskService;
import app.dayora.task.domain.Priority;
import java.security.Principal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional @EnabledIfEnvironmentVariable(named="DAYORA_POSTGRES_TEST",matches="true")
class TaskStatusIntegrationTest {
 @Autowired JdbcTemplate db; @Autowired AccountService accounts; @Autowired WorkspaceService workspaces; @Autowired TaskService tasks; @Autowired TaskStatusController statuses;
 UUID owner(){String n="status-"+UUID.randomUUID();accounts.create(n,"integration-password","UTC",Account.Role.USER);return accounts.byUsername(n).id();}
 Principal principal(UUID id){return ()->id.toString();}
 @Test void seedsDefaultsAndSwitches(){UUID o=owner(),w=workspaces.create(o,"W").id();var p=principal(o);var all=statuses.list(w,p);assertEquals(4,all.size());assertEquals(1,all.stream().filter(TaskStatusController.Status::isDefault).count());var custom=statuses.create(w,new TaskStatusController.Input("Backlog","UNSTARTED","#aAbBcC",true),p);assertTrue(custom.isDefault());assertEquals(1,statuses.list(w,p).stream().filter(TaskStatusController.Status::isDefault).count());assertEquals(custom.id(),tasks.create(o,UUID.randomUUID(),"Task",null,Priority.NONE,null,w,null,null).statusId());var original=all.stream().filter(TaskStatusController.Status::isDefault).findFirst().orElseThrow();statuses.patch(w,original.id(),new TaskStatusController.Input(null,null,null,true),p);assertEquals(original.id(),statuses.list(w,p).stream().filter(TaskStatusController.Status::isDefault).findFirst().orElseThrow().id());}
 @Test void customCompletionAndArchiveRevisions(){UUID o=owner(),w=workspaces.create(o,"W").id();var p=principal(o);var custom=statuses.create(w,new TaskStatusController.Input("Verified","COMPLETED","#123456",false),p);var t=tasks.create(o,UUID.randomUUID(),"Task",null,Priority.NONE,null,w,null,null,custom.id());assertNotNull(t.completedAt());assertEquals("DONE",t.status().name());var target=statuses.list(w,p).stream().filter(TaskStatusController.Status::isDefault).findFirst().orElseThrow();statuses.archive(w,custom.id(),new TaskStatusController.Archive(target.id()),p);assertEquals(target.id(),db.queryForObject("select status_id from task where id=?",UUID.class,t.id()));assertEquals(t.revision()+1,db.queryForObject("select revision from task where id=?",Long.class,t.id()));assertEquals("TODO",db.queryForObject("select status from task where id=?",String.class,t.id()));assertNull(db.queryForObject("select completed_at from task where id=?",Object.class,t.id()));assertTrue(db.queryForObject("select payload->>'statusId'=? and (payload->>'revision')::bigint=? from sync_entity where entity_id=? and entity_type='TASK'",Boolean.class,target.id().toString(),t.revision()+1,t.id()));}
 @Test void rejectsBoundariesAndInvalidInputs(){UUID o=owner(),w=workspaces.create(o,"W").id(),other=workspaces.create(o,"Other").id();var p=principal(o);var s=statuses.list(w,p).getFirst();assertThrows(IllegalArgumentException.class,()->tasks.create(o,UUID.randomUUID(),"Task",null,Priority.NONE,null,other,null,null,s.id()));assertThrows(IllegalArgumentException.class,()->statuses.list(w,principal(owner())));assertThrows(IllegalArgumentException.class,()->statuses.create(w,new TaskStatusController.Input("Invalid",null,null,false),p));assertThrows(IllegalArgumentException.class,()->statuses.create(w,new TaskStatusController.Input("Invalid","UNSTARTED","red",false),p));assertThrows(IllegalArgumentException.class,()->statuses.patch(w,s.id(),new TaskStatusController.Input(null,null,"red",null),p));}
 @Test void lastCategoryDefaultAndSelfArchiveGuard(){UUID o=owner(),w=workspaces.create(o,"W").id();var p=principal(o);var done=statuses.list(w,p).stream().filter(s->s.category().equals("COMPLETED")).findFirst().orElseThrow();assertThrows(IllegalArgumentException.class,()->statuses.archive(w,done.id(),null,p));assertThrows(IllegalArgumentException.class,()->statuses.delete(w,done.id(),p));assertThrows(IllegalArgumentException.class,()->statuses.patch(w,done.id(),new TaskStatusController.Input(null,"STARTED",null,null),p));var first=statuses.list(w,p).stream().filter(TaskStatusController.Status::isDefault).findFirst().orElseThrow();assertThrows(IllegalArgumentException.class,()->statuses.patch(w,first.id(),new TaskStatusController.Input(null,null,null,false),p));var custom=statuses.create(w,new TaskStatusController.Input("Review","STARTED",null,false),p);assertThrows(IllegalArgumentException.class,()->statuses.archive(w,custom.id(),new TaskStatusController.Archive(custom.id()),p));}
 @Test void softDeletedTasksPreventDeletion(){UUID o=owner(),w=workspaces.create(o,"W").id();var p=principal(o);var custom=statuses.create(w,new TaskStatusController.Input("Review","STARTED",null,false),p);var t=tasks.create(o,UUID.randomUUID(),"Task",null,Priority.NONE,null,w,null,null,custom.id());tasks.delete(o,t.id(),t.revision());assertThrows(IllegalArgumentException.class,()->statuses.delete(w,custom.id(),p));}
}
