package app.dayora.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
public class PushTokenController {
  private final PushTokenService service;
  private final FcmPushListener fcm;
  public PushTokenController(PushTokenService service, FcmPushListener fcm) { this.service = service; this.fcm = fcm; }
  record Body(@NotBlank @Size(max=4096) String token, PushTokenService.Platform platform) {}
  record Status(boolean enabled, String reason, long tokenCount) {}
  record Config(String apiKey, String authDomain, String projectId, String messagingSenderId, String appId, String vapidKey) {}
  @GetMapping("/status") Status status(Principal principal) { return new Status(fcm.enabled(),fcm.reason(),service.count(UUID.fromString(principal.getName()))); }
  @GetMapping("/config") Config config() { return fcm.webConfig(); }
  @PutMapping("/token") @ResponseStatus(HttpStatus.NO_CONTENT) void register(@Valid @RequestBody Body body, Principal principal) { service.register(UUID.fromString(principal.getName()), body.token(), body.platform()==null ? PushTokenService.Platform.ANDROID : body.platform()); }
  @DeleteMapping("/token") @ResponseStatus(HttpStatus.NO_CONTENT) void unregister(@Valid @RequestBody Body body, Principal principal) { service.unregister(UUID.fromString(principal.getName()), body.token()); }
}
