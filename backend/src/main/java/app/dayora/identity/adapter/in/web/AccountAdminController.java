package app.dayora.identity.adapter.in.web;

import app.dayora.identity.application.AccountService;
import app.dayora.identity.domain.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AccountAdminController {
    private final AccountService accounts;

    record CreateRequest(@NotBlank @Size(max = 120) String username, @NotBlank @Size(min = 12, max = 72) String password, @NotBlank @Size(max = 64) String timezone) {}
    record UserResponse(UUID id, String username, String role, boolean enabled, String timezone) {
        static UserResponse of(Account account) { return new UserResponse(account.id(), account.username(), account.role().name(), account.enabled(), account.timezone()); }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse create(@Valid @RequestBody CreateRequest request) {
        return UserResponse.of(accounts.create(request.username(), request.password(), request.timezone(), Account.Role.USER));
    }

    @PostMapping("/{id}/enable")
    UserResponse enable(@PathVariable UUID id) { return UserResponse.of(accounts.enabled(id, true)); }

    @PostMapping("/{id}/disable")
    UserResponse disable(@PathVariable UUID id) { return UserResponse.of(accounts.enabled(id, false)); }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordRequest request) { accounts.resetPassword(id, request.password()); }

    record PasswordRequest(@NotBlank @Size(min = 12, max = 72) String password) {}
}
