package de.bierverein.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {

        AppUser user = users
                .findByUsernameWithMember(req.username())
                .orElse(null);

        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Ungültiger Benutzername oder Passwort"));
        }

        if (!user.isEnabled()) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Benutzer ist deaktiviert"));
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Ungültiger Benutzername oder Passwort"));
        }

        String token = jwtService.create(user);

        Long memberId = null;
        String memberName = null;

        if (user.getMember() != null) {
            memberId = user.getMember().getId();
            memberName = user.getMember().getName();
        }

        return ResponseEntity.ok(
                new LoginResponse(
                        token,
                        user.getUsername(),
                        user.getRole(),
                        memberId,
                        memberName
                )
        );
    }

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(
            String token,
            String username,
            Role role,
            Long memberId,
            String memberName
    ) {}
}
