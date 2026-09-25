package de.bierverein.api;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PrimaryRoleSyncService primaryRoles;

    public AuthController(AppUserRepository users, MemberRepository members,
                          PasswordEncoder encoder, JwtService jwt, PrimaryRoleSyncService primaryRoles) {
        this.users = users;
        this.members = members;
        this.encoder = encoder;
        this.jwt = jwt;
        this.primaryRoles = primaryRoles;
    }

    @PostConstruct
    void seedAdmin() {
        if (!users.existsByUsername("admin")) {
            AppUser u = new AppUser();
            u.setUsername("admin");
            u.setPasswordHash(encoder.encode("admin123!"));
            u.setRole(Role.ADMIN);
            u.setEnabled(true);
            u.setRegistrationApproved(true);
            users.save(u);
        }
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public LoginResponse login(@RequestBody LoginRequest req) {
        if (req == null || req.username() == null || req.password() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Benutzername und Passwort sind erforderlich");
        }

        AppUser u = users.findByUsernameWithMember(req.username().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Ungültige Zugangsdaten"));

        if (!u.isRegistrationApproved()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Deine Registrierung wurde noch nicht durch den Administrator freigeschaltet.");
        }

        if (!u.isEnabled() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Ungültige Zugangsdaten");
        }

        Member member = u.getMember();
        Long memberId = member != null ? member.getId() : null;
        String memberName = member != null ? member.getName() : null;

        return new LoginResponse(
                jwt.create(u),
                u.getUsername(),
                u.getRole().name(),
                memberId,
                memberName
        );
    }

    @PostMapping("/register")
    @Transactional
    public RegistrationResponse register(@RequestBody RegistrationRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Registrierungsdaten fehlen");
        }

        String first = trim(req.firstName());
        String last = trim(req.lastName());
        String email = normalizeEmail(req.email());

        if (first == null || last == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vorname und Nachname sind erforderlich");
        }

        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Bitte eine gültige E-Mail-Adresse eingeben");
        }

        if (req.password() == null || req.password().length() < 8) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Das Passwort muss mindestens 8 Zeichen lang sein");
        }

        if (users.findByUsernameIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Für diese E-Mail-Adresse existiert bereits ein Login");
        }

        if (users.findAll().stream().anyMatch(x ->
                x.getMember() != null
                        && x.getMember().getEmail() != null
                        && x.getMember().getEmail().equalsIgnoreCase(email))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Diese E-Mail-Adresse ist bereits bei einem Mitglied hinterlegt");
        }

        Member m = new Member();
        m.setFirstName(first);
        m.setLastName(last);
        m.setName(first + " " + last);
        m.setEmail(email);
        m.setActive(false);
        m = members.save(m);

        AppUser u = new AppUser();
        u.setUsername(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.MEMBER);
        u.setEnabled(false);
        u.setRegistrationApproved(false);
        u.setMember(m);

        m.setUser(u);
        users.save(u);
        primaryRoles.sync(u, null);

        return new RegistrationResponse(
                "Registrierung erfolgreich. Nach der Freischaltung durch den Administrator kannst du dich anmelden.");
    }

    public record RegistrationRequest(
            String firstName,
            String lastName,
            String email,
            String password
    ) {}

    public record RegistrationResponse(String message) {}

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(
            String token,
            String username,
            String role,
            Long memberId,
            String memberName
    ) {}
}
