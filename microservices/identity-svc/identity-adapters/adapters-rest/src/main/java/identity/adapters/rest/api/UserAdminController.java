package identity.adapters.rest.api;

import identity.application.UserAdministrationService;
import identity.application.UserAdministrationService.DuplicateUserException;
import identity.application.UserAdministrationService.UserNotFoundException;
import identity.application.UserAdministrationService.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** P1-F1-S2 user CRUD admin API. Responses never carry password material. */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdministrationService users;

    public UserAdminController(UserAdministrationService users) {
        this.users = users;
    }

    public record CreateUserRequest(String userLoginId, String password, String partyId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestBody CreateUserRequest request) {
        return users.view(users.create(request.userLoginId(), request.password(), request.partyId()).userLoginId());
    }

    @GetMapping("/{userLoginId}")
    public UserView get(@PathVariable String userLoginId) {
        return users.view(userLoginId);
    }

    @ExceptionHandler(DuplicateUserException.class)
    public ResponseEntity<String> conflict(DuplicateUserException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> missing(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
}
