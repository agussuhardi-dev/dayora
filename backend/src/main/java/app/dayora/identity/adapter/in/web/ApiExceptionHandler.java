package app.dayora.identity.adapter.in.web;

import app.dayora.focus.application.FocusService;
import app.dayora.focus.domain.FocusSession;
import app.dayora.identity.application.AccountService;
import app.dayora.task.application.TaskService;
import app.dayora.sync.SyncConflict;
import jakarta.validation.ConstraintViolationException;
import java.time.DateTimeException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {
    record ErrorResponse(int status, String code, String message) {}

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class, DateTimeException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse badRequest() { return new ErrorResponse(400, "BAD_REQUEST", "Invalid request"); }

    @ExceptionHandler({NoSuchElementException.class, TaskService.NotFound.class, FocusService.NotFound.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound() { return new ErrorResponse(404, "NOT_FOUND", "Not found"); }

    @ExceptionHandler({AccountService.UsernameConflict.class, TaskService.Stale.class, TaskService.IdempotencyConflict.class,
            SettingsService.Stale.class, FocusSession.Conflict.class, SyncConflict.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse conflict() { return new ErrorResponse(409, "CONFLICT", "Conflict"); }
}
