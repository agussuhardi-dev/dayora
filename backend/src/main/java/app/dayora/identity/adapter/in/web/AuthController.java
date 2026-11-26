package app.dayora.identity.adapter.in.web;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AccountService accounts;

    record RegisterRequest(@NotBlank @Size(max = 120) String username,
                           @NotBlank String password,
                           @Size(max = 64) String timezone) {}
    record RegisterResponse(UUID id, String username, String timezone) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        var account = accounts.create(request.username(), request.password(),
                request.timezone() == null ? "UTC" : request.timezone(), Account.Role.USER);
        return new RegisterResponse(account.id(), account.username(), account.timezone());
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    Map<String, String> me(Authentication authentication) {
        return Map.of("id", authentication.getName());
    }
}
