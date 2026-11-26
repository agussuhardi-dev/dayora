package app.dayora.focus.application;

import static org.junit.jupiter.api.Assertions.*;
import app.dayora.focus.domain.*;
import app.dayora.realtime.AccountEvents;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FocusServiceTest {
  @Test void automaticPomodoroExpiryCountsFocusAndSelectsBreak() {
    var now=java.time.Instant.now(); var owner=UUID.randomUUID(); var workspace=UUID.randomUUID();
    var focus=FocusSession.start(owner,workspace,null,FocusSession.Mode.POMODORO,60,1,2,3,4,now);
    var completed=focus.autoComplete(now.plusSeconds(60));
    assertEquals(1,completed.completedFocusCount());
    assertEquals(FocusSession.State.COMPLETED,completed.state());
    var repo=Mockito.mock(FocusRepository.class); var events=Mockito.mock(AccountEvents.class);
    var service=new FocusService(repo,events,Mockito.mock(app.dayora.workspace.WorkspaceService.class));
    Mockito.when(repo.save(Mockito.any())).thenAnswer(i->i.getArgument(0));
    for(int count=1;count<=4;count++) {
      var done=new FocusSession(UUID.randomUUID(),owner,workspace,null,FocusSession.State.COMPLETED,FocusSession.Mode.POMODORO,FocusSession.Phase.FOCUS,count,60,60,1,2,3,4,now,null,now.plusSeconds(60));
      Mockito.when(repo.lock(owner,done.id())).thenReturn(Optional.of(done));
      Mockito.when(repo.findHistory(owner)).thenReturn(List.of(done));
      var pause=service.next(owner,done.id());
      assertEquals(count==4?FocusSession.Phase.LONG_BREAK:FocusSession.Phase.SHORT_BREAK,pause.phase());
      assertEquals(count==4?180:120,pause.durationSeconds());
      var breakDone=pause.autoComplete(pause.startedAt().plusSeconds(pause.durationSeconds()));
      assertEquals(count,breakDone.completedFocusCount());
      Mockito.when(repo.lock(owner,breakDone.id())).thenReturn(Optional.of(breakDone));
      Mockito.when(repo.findHistory(owner)).thenReturn(List.of(breakDone));
      var next=service.next(owner,breakDone.id());
      assertEquals(FocusSession.Phase.FOCUS,next.phase());
      assertEquals(60,next.durationSeconds());
      assertEquals(count,next.completedFocusCount());
    }
  }

  @Test void manualPomodoroFinishDoesNotCountCompletedFocus() {
    var now=java.time.Instant.now(); var owner=UUID.randomUUID(); var workspace=UUID.randomUUID();
    var focus=FocusSession.start(owner,workspace,null,FocusSession.Mode.POMODORO,60,1,2,3,4,now);
    assertEquals(0,focus.transition(FocusSession.Action.COMPLETE,now.plusSeconds(10)).completedFocusCount());
  }

  @Test void normalHonorsDurationAndRejectsBounds() {
    var repo=Mockito.mock(FocusRepository.class); var events=Mockito.mock(AccountEvents.class);
    var workspaces=Mockito.mock(app.dayora.workspace.WorkspaceService.class); var service=new FocusService(repo,events,workspaces); var owner=UUID.randomUUID();
    Mockito.when(workspaces.resolve(Mockito.eq(owner),Mockito.isNull())).thenReturn(UUID.randomUUID());
    Mockito.when(repo.findActive(owner)).thenReturn(Optional.empty());
    Mockito.when(repo.save(Mockito.any())).thenAnswer(i->i.getArgument(0));
    assertEquals(3600,service.start(owner,null,FocusSession.Mode.NORMAL,3600L,null,null,null).durationSeconds());
    assertThrows(IllegalArgumentException.class,()->service.start(owner,null,FocusSession.Mode.NORMAL,0L,null,null,null));
    assertThrows(IllegalArgumentException.class,()->service.start(owner,null,FocusSession.Mode.NORMAL,86401L,null,null,null));
  }
}
