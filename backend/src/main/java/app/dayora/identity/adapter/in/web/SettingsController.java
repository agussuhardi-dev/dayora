package app.dayora.identity.adapter.in.web;

import app.dayora.realtime.AccountEvents;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {
  public static void validateSettings(JsonNode settings) { SettingsService.validate(settings); }
  private final SettingsService service;
  SettingsController(SettingsService service) { this.service = service; }
  record Input(Long revision, JsonNode settings) {}
  @GetMapping SettingsService.State get(Principal principal) { return service.get(UUID.fromString(principal.getName())); }
  @PutMapping SettingsService.State put(@RequestBody Input input, Principal principal) {
    if (input == null || input.revision() == null || input.revision() < 0 || input.settings() == null)
      throw new IllegalArgumentException("invalid settings request");
    return service.put(UUID.fromString(principal.getName()), input.revision(), input.settings());
  }
}

@Service
class SettingsService {
  static final String DEFAULT = """
      {"schemaVersion":1,"pomodoro":{"focusMinutes":25,"shortBreakMinutes":5,"longBreakMinutes":15,"cyclesBeforeLongBreak":4,"mode":"POMODORO"},"sound":{"enabled":true,"breakTicking":true},"appearance":{"theme":"system","accent":"#7561d8"}}
      """;
  record State(long revision, JsonNode settings) {}
  static class Stale extends RuntimeException {}
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final AccountEvents events;
  SettingsService(JdbcTemplate jdbc, ObjectMapper json, AccountEvents events) {
    this.jdbc = jdbc; this.json = json; this.events = events;
  }
  @Transactional(readOnly = true) State get(UUID owner) {
    return jdbc.query("select revision, settings::text from account_settings where owner_id=?",
        rs -> rs.next() ? new State(rs.getLong(1), normalize(json.readTree(rs.getString(2)))) : new State(0, json.readTree(DEFAULT)), owner);
  }
  @Transactional State put(UUID owner, long revision, JsonNode settings) {
    validate(settings);
    String encoded = json.writeValueAsString(settings);
    if (encoded.getBytes(StandardCharsets.UTF_8).length > 16_384) throw new IllegalArgumentException("settings too large");
    validate(settings);
    events.lock(owner); // ponytail: owner lock serializes first writes; split only if settings throughput demands it.
    if (get(owner).revision() != revision) throw new Stale();
    long next = revision + 1;
    jdbc.update("insert into account_settings(owner_id,revision,settings) values (?,?,cast(? as jsonb)) on conflict (owner_id) do update set revision=excluded.revision,settings=excluded.settings,updated_at=current_timestamp", owner, next, encoded);
    State state = new State(next, settings);
    events.invalidate(owner);
    return state;
  }
  static JsonNode normalize(JsonNode n) {
    if (n != null && n.path("pomodoro").isObject() && !n.path("pomodoro").has("cyclesBeforeLongBreak"))
      ((tools.jackson.databind.node.ObjectNode)n.get("pomodoro")).put("cyclesBeforeLongBreak", 4);
    return n;
  }
  static void validate(JsonNode n) {
    normalize(n);
    if (!n.isObject() || !integer(n.get("schemaVersion"), 1, 1)) bad();
    JsonNode p = object(n, "pomodoro"), s = object(n, "sound"), a = object(n, "appearance");
    for (String key : new String[]{"focusMinutes", "shortBreakMinutes", "longBreakMinutes"})
      if (!integer(p.get(key), 1, 240)) bad();
    if (p.get("cyclesBeforeLongBreak") != null && !integer(p.get("cyclesBeforeLongBreak"), 1, 12)) bad();
    if (!stringOneOf(p.get("mode"), "POMODORO", "NORMAL")) bad();
    if (!bool(s.get("enabled")) || !bool(s.get("breakTicking"))) bad();
    if (!stringOneOf(a.get("theme"), "system", "light", "dark")) bad();
    JsonNode accent = a.get("accent");
    if (accent == null || !accent.isTextual() || !accent.asText().matches("#[0-9a-fA-F]{6}")) bad();
  }
  private static JsonNode object(JsonNode n, String key) {
    JsonNode value = n.get(key); if (value == null || !value.isObject()) throw new IllegalArgumentException("invalid settings"); return value;
  }
  private static boolean integer(JsonNode n, int min, int max) { return n != null && n.isIntegralNumber() && n.asLong() >= min && n.asLong() <= max; }
  private static boolean bool(JsonNode n) { return n != null && n.isBoolean(); }
  private static boolean stringOneOf(JsonNode n, String... values) {
    if (n == null || !n.isTextual()) return false;
    for (String value : values) if (value.equals(n.asText())) return true;
    return false;
  }
  private static void bad() { throw new IllegalArgumentException("invalid settings"); }
}
