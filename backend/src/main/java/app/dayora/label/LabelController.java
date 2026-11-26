package app.dayora.label;

import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") public class LabelController {
  private final LabelService service;
  public LabelController(LabelService service) { this.service=service; }
  record Input(String name,String color) {}
  record Assignment(List<UUID> labelIds) {}
  private UUID owner(Principal p) { return UUID.fromString(p.getName()); }
  @GetMapping("/workspaces/{workspaceId}/labels") List<LabelService.Label> list(@PathVariable UUID workspaceId,Principal p) { return service.list(owner(p),workspaceId); }
  @PostMapping("/workspaces/{workspaceId}/labels") @ResponseStatus(HttpStatus.CREATED) LabelService.Label create(@PathVariable UUID workspaceId,@RequestBody Input input,Principal p) { return service.create(owner(p),workspaceId,input.name(),input.color()); }
  @DeleteMapping("/workspaces/{workspaceId}/labels/{labelId}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@PathVariable UUID workspaceId,@PathVariable UUID labelId,Principal p) { service.delete(owner(p),workspaceId,labelId); }
  @GetMapping("/tasks/{taskId}/labels") List<LabelService.Label> taskLabels(@PathVariable UUID taskId,Principal p) { return service.taskLabels(owner(p),taskId); }
  @PutMapping("/tasks/{taskId}/labels") List<LabelService.Label> assign(@PathVariable UUID taskId,@RequestBody Assignment input,Principal p) { return service.assign(owner(p),taskId,input.labelIds()); }
}
