package app.dayora.sync;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import app.dayora.workspace.WorkspaceService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SyncIntegrationTest {
 @Autowired SyncService sync; @Autowired AccountService accounts; @Autowired WorkspaceService workspaces; @Autowired ObjectMapper json;
 private UUID owner(){return accounts.create("sync-"+UUID.randomUUID(),"integration-password","UTC",Account.Role.USER).id();}
 @Test void bootstrapPushIdempotencyConflictDeleteAndCursor(){
  UUID owner=owner(),device=UUID.randomUUID(),id=UUID.randomUUID(),workspace=workspaces.personal(owner); sync.bootstrap(owner,device);
  var payload=json.createObjectNode().put("title","offline").put("workspaceId",workspace.toString()).put("priority","NONE").put("status","TODO");
  var op=new SyncService.Operation(UUID.randomUUID(),"TASK",id,0,"UPSERT",payload); var request=new SyncService.Push(device,List.of(op));
  var first=sync.push(owner,request); assertEquals(1,first.results().getFirst().revision());
  assertEquals(first.results(),sync.push(owner,request).results());
  var changed=payload.deepCopy().put("title","changed");
  assertThrows(SyncConflict.class,()->sync.push(owner,new SyncService.Push(device,List.of(new SyncService.Operation(UUID.randomUUID(),"TASK",id,0,"UPSERT",changed)))));
  var deleted=sync.push(owner,new SyncService.Push(device,List.of(new SyncService.Operation(UUID.randomUUID(),"TASK",id,1,"DELETE",json.createObjectNode()))));
  assertTrue(deleted.results().getFirst().deleted()); assertFalse(sync.pull(owner,device,first.cursor(),100).changes().isEmpty());
  assertThrows(IllegalArgumentException.class,()->sync.pull(owner,UUID.randomUUID(),0,100));
 }
 @Test void deviceCannotCrossAccounts(){UUID owner=owner(),other=owner(),device=UUID.randomUUID();sync.bootstrap(owner,device);assertThrows(IllegalArgumentException.class,()->sync.pull(other,device,0,10));}
}
