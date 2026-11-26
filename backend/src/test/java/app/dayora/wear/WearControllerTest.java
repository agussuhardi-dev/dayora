package app.dayora.wear;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import app.dayora.focus.application.FocusService;
import app.dayora.focus.domain.FocusSession;
import app.dayora.realtime.AccountEvents;
import java.security.Principal;
import java.sql.ResultSet;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.ObjectMapper;

class WearControllerTest {
  final UUID owner=UUID.randomUUID(), operation=UUID.randomUUID();
  final Principal principal=() -> owner.toString();
  final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  final FocusService focus=mock(FocusService.class);
  final WearController controller=new WearController(jdbc,focus,mock(AccountEvents.class),new ObjectMapper());
  WearController.Command command(FocusSession.Mode mode,Integer focus,Integer shortBreak,Integer longBreak) {
    return new WearController.Command(operation,"watch","start",null,mode,focus,shortBreak,longBreak);
  }
  void paired() {
    when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(owner),eq("watch"))).thenReturn(true);
  }
  void settings(String value) {
    when(jdbc.query(anyString(),any(ResultSetExtractor.class),eq(owner))).thenAnswer(call -> {
      ResultSet rs=mock(ResultSet.class);
      when(rs.next()).thenReturn(value!=null);
      when(rs.getString(1)).thenReturn(value);
      return ((ResultSetExtractor<?>)call.getArgument(1)).extractData(rs);
    });
  }
  @Test void startsWithPersistedDurations() {
    paired(); settings("{\"mode\":\"POMODORO\",\"focusMinutes\":3,\"shortBreakMinutes\":1,\"longBreakMinutes\":2}");
    controller.command(command(null,null,null,null),principal);
    verify(focus).start(owner,null,null,FocusSession.Mode.POMODORO,null,3,1,2);
  }
  @Test void explicitFieldsOverrideSavedSettingsIndividually() {
    paired(); settings("{\"mode\":\"POMODORO\",\"focusMinutes\":3,\"shortBreakMinutes\":1,\"longBreakMinutes\":2}");
    controller.command(command(FocusSession.Mode.NORMAL,7,null,null),principal);
    verify(focus).start(owner,null,null,FocusSession.Mode.NORMAL,86400L,7,1,2);
  }
  @Test void missingAccountSettingsUseAccountDefaults() {
    paired(); settings(null);
    controller.command(command(null,null,null,null),principal);
    verify(focus).start(owner,null,null,FocusSession.Mode.POMODORO,null,25,5,15);
  }
  @Test void fullySpecifiedConfigNeedsNoSettingsRead() {
    paired();
    controller.command(command(FocusSession.Mode.POMODORO,3,1,2),principal);
    verify(focus).start(owner,null,null,FocusSession.Mode.POMODORO,null,3,1,2);
    verify(jdbc,never()).query(anyString(),any(ResultSetExtractor.class),eq(owner));
  }
  @Test void legacyRetryReturnsStoredResponse() {
    paired();
    when(jdbc.queryForList(anyString(),eq(owner),eq(operation))).thenReturn(List.of(Map.of("payload_hash",WearController.hash("watch\nstart\n"),"response","stored")));
    assertEquals("stored",controller.command(command(null,null,null,null),principal));
    verifyNoInteractions(focus);
  }
  @Test void suppliedFieldsChangeHash() {
    String legacy=WearController.hash("watch\nstart\n");
    assertEquals(legacy,WearController.payloadHash(command(null,null,null,null)));
    for(var input:List.of(command(FocusSession.Mode.NORMAL,null,null,null),command(null,3,null,null),command(null,null,1,null),command(null,null,null,2)))
      assertNotEquals(legacy,WearController.payloadHash(input));
    assertNotEquals(WearController.payloadHash(command(null,3,null,null)),WearController.payloadHash(command(null,4,null,null)));
  }
  @Test void hashesTokensWithoutStoringSecrets() {
    assertEquals(64, WearController.hash("single-use-token").length());
    assertEquals(WearController.hash("single-use-token"), WearController.hash("single-use-token"));
    assertNotEquals(WearController.hash("single-use-token"), WearController.hash("another-token"));
  }
}
