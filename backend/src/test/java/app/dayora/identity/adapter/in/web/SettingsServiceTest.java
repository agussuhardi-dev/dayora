package app.dayora.identity.adapter.in.web;

import app.dayora.realtime.AccountEvents;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingsServiceTest {
  private final ObjectMapper json = new ObjectMapper();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final AccountEvents events = mock(AccountEvents.class);
  private final SettingsService service = new SettingsService(jdbc, json, events);

  @Test void writesPreserveUnknownFieldsAndEmitAccountEvent() {
    UUID owner = UUID.randomUUID();
    var spy = spy(service);
    var defaults = json.readTree(SettingsService.DEFAULT);
    doReturn(new SettingsService.State(0, defaults)).when(spy).get(owner);
    var settings = json.readTree(SettingsService.DEFAULT.replace("\"schemaVersion\":1", "\"future\":{\"nested\":true},\"schemaVersion\":1"));
    var saved = spy.put(owner, 0, settings);
    assertEquals(1, saved.revision());
    assertTrue(saved.settings().get("future").get("nested").asBoolean());
    verify(events).lock(owner);
    verify(events).invalidate(owner);
  }

  @Test void staleRevisionDoesNotWriteOrEmit() {
    UUID owner = UUID.randomUUID();
    var spy = spy(service);
    var settings = json.readTree(SettingsService.DEFAULT);
    doReturn(new SettingsService.State(2, settings)).when(spy).get(owner);
    assertThrows(SettingsService.Stale.class, () -> spy.put(owner, 1, settings));
    verifyNoInteractions(jdbc);
    verify(events).lock(owner);
    verifyNoMoreInteractions(events);
  }

  @Test void invalidKnownFieldsAndOversizedDocumentsAreRejectedBeforeWriting() {
    var invalid = json.readTree(SettingsService.DEFAULT.replace("\"focusMinutes\":25", "\"focusMinutes\":0"));
    assertThrows(IllegalArgumentException.class, () -> service.put(UUID.randomUUID(), 0, invalid));
    var oversized = json.readTree(SettingsService.DEFAULT.replace("\"schemaVersion\":1", "\"extra\":\"" + "x".repeat(17000) + "\",\"schemaVersion\":1"));
    assertThrows(IllegalArgumentException.class, () -> service.put(UUID.randomUUID(), 0, oversized));
    verifyNoInteractions(jdbc, events);
  }
}
