package app.dayora.workspace;

import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/workspaces") public class WorkspaceController {
  private final WorkspaceService service;
  public WorkspaceController(WorkspaceService service) { this.service=service; }
  record Input(String name) {}
  @GetMapping List<WorkspaceService.Workspace> list(Principal p) { return service.list(UUID.fromString(p.getName())); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) WorkspaceService.Workspace create(@RequestBody Input input,Principal p) { return service.create(UUID.fromString(p.getName()),input.name()); }
}
