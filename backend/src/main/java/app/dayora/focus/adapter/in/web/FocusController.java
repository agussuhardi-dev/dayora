package app.dayora.focus.adapter.in.web;

import app.dayora.focus.application.FocusService;
import app.dayora.focus.domain.FocusSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/focus")
public class FocusController {
  private final FocusService service;
  public FocusController(FocusService service) { this.service=service; }
  record Input(@Positive Long durationSeconds, UUID workspaceId, UUID taskId, FocusSession.Mode mode, Integer focusDurationMinutes, Integer shortBreakDurationMinutes, Integer longBreakDurationMinutes, Integer cyclesBeforeLongBreak) {}
  private UUID owner(Principal principal) { return UUID.fromString(principal.getName()); }

  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  FocusSession start(@Valid @RequestBody Input input, Principal principal) {
    return service.start(owner(principal),input.workspaceId(),input.taskId(),input.mode(),input.durationSeconds(),input.focusDurationMinutes(),input.shortBreakDurationMinutes(),input.longBreakDurationMinutes(),input.cyclesBeforeLongBreak());
  }
  @GetMapping("/current") ResponseEntity<FocusSession> current(Principal principal) {
    return service.current(owner(principal)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
  }
  @GetMapping("/history") List<FocusSession> history(@RequestParam(required=false) UUID workspaceId, @RequestParam(required=false) java.time.Instant since, Principal principal) {
    if (since != null && (since.isBefore(java.time.Instant.now().minusSeconds(172800)) || since.isAfter(java.time.Instant.now()))) throw new IllegalArgumentException("Invalid history start");
    return since == null ? service.history(owner(principal),workspaceId) : service.historySince(owner(principal),workspaceId,since);
  }
  @GetMapping("/{id}") FocusSession get(@PathVariable UUID id, Principal principal) { return service.get(owner(principal),id); }
  @PostMapping("/{id}/next") @ResponseStatus(HttpStatus.CREATED)
  FocusSession next(@PathVariable UUID id, Principal p) { return service.next(owner(p),id); }
  @PostMapping("/{id}/pause") FocusSession pause(@PathVariable UUID id, Principal p) { return service.transition(owner(p),id,FocusSession.Action.PAUSE); }
  @PostMapping("/{id}/resume") FocusSession resume(@PathVariable UUID id, Principal p) { return service.transition(owner(p),id,FocusSession.Action.RESUME); }
  @PostMapping("/{id}/complete") FocusSession complete(@PathVariable UUID id, Principal p) { return service.transition(owner(p),id,FocusSession.Action.COMPLETE); }
  @PostMapping("/{id}/cancel") FocusSession cancel(@PathVariable UUID id, Principal p) { return service.transition(owner(p),id,FocusSession.Action.CANCEL); }
}
