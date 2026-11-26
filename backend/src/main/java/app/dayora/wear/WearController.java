package app.dayora.wear;

import app.dayora.focus.application.FocusService;
import app.dayora.focus.domain.FocusSession;
import app.dayora.realtime.AccountEvents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Phone-authenticated relay only: session cookies and CSRF never leave the phone. */
@RestController
@RequestMapping("/api/wear")
@Transactional
public class WearController {
  private final JdbcTemplate jdbc;
  private final FocusService focus;
  private final AccountEvents events;
  private final ObjectMapper json;
  public WearController(JdbcTemplate jdbc, FocusService focus, AccountEvents events, ObjectMapper json) {
    this.jdbc=jdbc; this.focus=focus; this.events=events; this.json=json;
  }
  public record Device(@NotBlank @Size(max=120) String deviceId) {}
  public record Claim(@NotBlank @Size(max=120) String deviceId, @Pattern(regexp="[A-Za-z0-9_-]{43}") @NotNull String token) {}
  public record Command(@NotNull UUID operationId, @NotBlank @Size(max=120) String deviceId,
      @NotNull @Pattern(regexp="start|pause|resume|complete|cancel") String action, UUID sessionId,
      FocusSession.Mode mode, @Min(1) @Max(240) Integer focusMinutes,
      @Min(1) @Max(240) Integer shortBreakMinutes, @Min(1) @Max(240) Integer longBreakMinutes) {}
  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  static String payloadHash(Command input) {
    String legacy=input.deviceId()+"\n"+input.action()+"\n"+Objects.toString(input.sessionId(),"");
    if(input.mode()==null && input.focusMinutes()==null && input.shortBreakMinutes()==null && input.longBreakMinutes()==null) return hash(legacy);
    return hash(legacy+"\n"+Objects.toString(input.mode(),"")+"\n"+Objects.toString(input.focusMinutes(),"")+"\n"+Objects.toString(input.shortBreakMinutes(),"")+"\n"+Objects.toString(input.longBreakMinutes(),""));
  }
  private record Pomodoro(FocusSession.Mode mode, int focus, int shortBreak, int longBreak) {}
  private Pomodoro pomodoro(UUID owner) {
    return jdbc.query("select (settings->'pomodoro')::text from account_settings where owner_id=?", rs -> {
      if(!rs.next()) return new Pomodoro(FocusSession.Mode.POMODORO,25,5,15);
      var settings=json.readTree(rs.getString(1));
      return new Pomodoro(FocusSession.Mode.valueOf(settings.get("mode").asText()),settings.get("focusMinutes").asInt(),settings.get("shortBreakMinutes").asInt(),settings.get("longBreakMinutes").asInt());
    },owner);
  }
  private UUID owner(Principal p) { return UUID.fromString(p.getName()); }
  @PostMapping("/pairing")
  Map<String,Object> create(@Valid @RequestBody Device input, Principal p) {
    UUID owner=owner(p); events.lock(owner);
    jdbc.update("delete from wear_pairing_token where owner_id=? and (device_id=? or expires_at<CURRENT_TIMESTAMP)",owner,input.deviceId());
    byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
    String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expires=Instant.now().plusSeconds(120);
    jdbc.update("insert into wear_pairing_token(token_hash,owner_id,device_id,expires_at) values(?,?,?,?)",hash(token),owner,input.deviceId(),Timestamp.from(expires));
    return Map.of("token",token,"expiresAt",expires);
  }
  @PostMapping("/pairing/consume")
  Map<String,String> consume(@Valid @RequestBody Claim input, Principal p) {
    UUID owner=owner(p); events.lock(owner);
    int count=jdbc.update("delete from wear_pairing_token where token_hash=? and owner_id=? and device_id=? and expires_at>CURRENT_TIMESTAMP",hash(input.token()),owner,input.deviceId());
    if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Pairing token expired or consumed");
    jdbc.update("insert into wear_device(owner_id,device_id) values(?,?) on conflict(owner_id,device_id) do nothing",owner,input.deviceId());
    return Map.of("deviceId",input.deviceId());
  }
  @GetMapping("/devices")
  List<Map<String,Object>> devices(Principal p) { return jdbc.queryForList("select device_id as \"deviceId\", paired_at as \"pairedAt\" from wear_device where owner_id=? order by paired_at",owner(p)); }
  @DeleteMapping("/devices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(@PathVariable String id, Principal p) {
    UUID owner=owner(p); events.lock(owner);
    jdbc.update("delete from wear_device where owner_id=? and device_id=?",owner,id);
    jdbc.update("delete from wear_pairing_token where owner_id=? and device_id=?",owner,id);
  }
  public record WatchState(FocusSession session, int completedToday) {}
  @GetMapping("/state")
  WatchState state(Principal p, @RequestParam(defaultValue="UTC") String timezone) {
    ZoneId zone;
    try { zone=ZoneId.of(timezone); }
    catch (DateTimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid timezone"); }
    UUID owner=owner(p); events.lock(owner);
    var session=focus.current(owner)
        .filter(s -> s.state()==FocusSession.State.RUNNING || s.state()==FocusSession.State.PAUSED).orElse(null);
    var today=LocalDate.now(zone);
    int completed=jdbc.queryForObject("select count(*) from focus_session where owner_id=? and state='COMPLETED' and ended_at>=? and ended_at<?",Integer.class,owner,
        Timestamp.from(today.atStartOfDay(zone).toInstant()),Timestamp.from(today.plusDays(1).atStartOfDay(zone).toInstant()));
    return new WatchState(session,completed);
  }
  @PostMapping("/commands")
  String command(@Valid @RequestBody Command input, Principal p) {
    UUID owner=owner(p); events.lock(owner);
    if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from wear_device where owner_id=? and device_id=?)",Boolean.class,owner,input.deviceId())))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Watch not paired");
    String payloadHash=payloadHash(input);
    var previous=jdbc.queryForList("select payload_hash,response from wear_command where owner_id=? and operation_id=?",owner,input.operationId());
    if(!previous.isEmpty()) {
      if(!payloadHash.equals(previous.getFirst().get("payload_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation ID reused");
      return previous.getFirst().get("response").toString();
    }
    FocusSession result;
    if(input.action().equals("start")) {
      var saved=input.mode()==null || input.focusMinutes()==null || input.shortBreakMinutes()==null || input.longBreakMinutes()==null?pomodoro(owner):null;
      var mode=input.mode()==null?saved.mode():input.mode();
      int focusMinutes=input.focusMinutes()==null?saved.focus():input.focusMinutes();
      int shortBreak=input.shortBreakMinutes()==null?saved.shortBreak():input.shortBreakMinutes();
      int longBreak=input.longBreakMinutes()==null?saved.longBreak():input.longBreakMinutes();
      result=focus.start(owner,null,null,mode,mode==FocusSession.Mode.POMODORO?null:86400L,focusMinutes,shortBreak,longBreak);
    }
    else {
      if(input.sessionId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Session required");
      result=focus.transition(owner,input.sessionId(),FocusSession.Action.valueOf(input.action().toUpperCase(Locale.ROOT)));
    }
    String response=json.writeValueAsString(result);
    jdbc.update("insert into wear_command(owner_id,operation_id,device_id,payload_hash,response) values(?,?,?,?,?)",owner,input.operationId(),input.deviceId(),payloadHash,response);
    return response;
  }
}
