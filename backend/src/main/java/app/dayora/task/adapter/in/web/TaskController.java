package app.dayora.task.adapter.in.web;

import app.dayora.task.application.TaskService;
import app.dayora.task.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@org.springframework.validation.annotation.Validated
@RestController @RequestMapping("/api/tasks")
public class TaskController {
  private final TaskService service;
  public TaskController(TaskService service) { this.service = service; }
  record Input(@NotBlank @Size(max=240) String title, @Size(max=10000) String notes, Priority priority, LocalDate dueDate, Instant completedAt, UUID workspaceId, TaskStatus status, UUID statusId) {}
  record Output(UUID id, String title, String notes, Priority priority, LocalDate dueDate, Instant completedAt, long revision, UUID workspaceId, TaskStatus status, UUID statusId) { static Output of(Task t) { return new Output(t.id(),t.title(),t.notes(),t.priority(),t.dueDate(),t.completedAt(),t.revision(),t.workspaceId(),t.status(),t.statusId()); } }
  private UUID owner(Principal a) { return UUID.fromString(a.getName()); }
  @PostMapping ResponseEntity<Output> create(@Valid @RequestBody Input i, @RequestHeader(value="Idempotency-Key",required=false) UUID key, Principal a) { return ResponseEntity.status(201).body(Output.of(key==null?service.create(owner(a),UUID.randomUUID(),i.title(),i.notes(),i.priority(),i.dueDate(),i.workspaceId(),i.status(),i.completedAt(),i.statusId()):service.createIdempotent(owner(a),key,i.title(),i.notes(),i.priority(),i.dueDate(),i.workspaceId(),i.status(),i.completedAt(),i.statusId()))); }
  @GetMapping Page<Output> list(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size, @RequestParam(required=false) UUID workspaceId, Principal a) { return service.list(owner(a),workspaceId,PageRequest.of(page,Math.min(size,100))).map(Output::of); }
  @GetMapping("/{id}") Output get(@PathVariable UUID id, Principal a) { return Output.of(service.get(owner(a),id)); }
  @PutMapping("/{id}") Output update(@PathVariable UUID id,@RequestParam long baseRevision,@Valid @RequestBody Input i,Principal a) { return Output.of(service.update(owner(a),id,baseRevision,i.title(),i.notes(),i.priority(),i.dueDate(),i.completedAt(),i.workspaceId(),i.status(),i.statusId())); }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID id,@RequestParam long baseRevision,Principal a) { service.delete(owner(a),id,baseRevision); }
}
