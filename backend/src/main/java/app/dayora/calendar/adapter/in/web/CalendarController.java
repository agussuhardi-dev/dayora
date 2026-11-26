package app.dayora.calendar.adapter.in.web;

import app.dayora.calendar.application.CalendarService;
import app.dayora.calendar.domain.CalendarEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.validation.annotation.Validated
@RestController @RequestMapping("/api/calendar") public class CalendarController {
  private final CalendarService service;
  public CalendarController(CalendarService service){this.service=service;}
  record Input(UUID taskId,@NotBlank @Size(max=240) String title,@NotNull Instant startsAt,@NotNull Instant endsAt,@NotBlank String timezone,@Size(max=64) String category,@Size(max=32) String color,UUID workspaceId) {}
  private UUID owner(Principal principal){return UUID.fromString(principal.getName());}
  @PostMapping ResponseEntity<CalendarEvent> create(@Valid @RequestBody Input input,Principal principal){return ResponseEntity.status(201).body(service.create(owner(principal),UUID.randomUUID(),input.taskId(),input.title(),input.startsAt(),input.endsAt(),input.timezone(),input.category(),input.color(),input.workspaceId()));}
  @GetMapping List<CalendarEvent> list(@RequestParam Instant from,@RequestParam Instant to,@RequestParam(required=false) UUID workspaceId,Principal principal){return service.list(owner(principal),from,to,workspaceId);}
  @GetMapping("/{id}") CalendarEvent get(@PathVariable UUID id,Principal principal){return service.get(owner(principal),id);}
  @PutMapping("/{id}") CalendarEvent update(@PathVariable UUID id,@RequestParam long baseRevision,@Valid @RequestBody Input input,Principal principal){return service.update(owner(principal),id,baseRevision,input.taskId(),input.title(),input.startsAt(),input.endsAt(),input.timezone(),input.category(),input.color(),input.workspaceId());}
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id,@RequestParam long baseRevision,Principal principal){service.delete(owner(principal),id,baseRevision);}
  @ExceptionHandler(CalendarService.NotFound.class) @ResponseStatus(HttpStatus.NOT_FOUND) void notFound(){}
  @ExceptionHandler({CalendarService.Stale.class,org.springframework.orm.ObjectOptimisticLockingFailureException.class}) @ResponseStatus(HttpStatus.CONFLICT) void conflict(){}
}
