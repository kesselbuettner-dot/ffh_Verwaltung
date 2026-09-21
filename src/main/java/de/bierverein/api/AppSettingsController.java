package de.bierverein.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {
    private static final String DEFAULT_ORDER =
            "dashboard,members,theke,shopping,purchase,inventory,articles,devices,drivebook,devicebook,material,events,firewehr,training,finance,documents,calendar,donations,admin,admin-members,admin-users,admin-settings";

    private final AppSettingsRepository settings;
    private static final ObjectMapper JSON = new ObjectMapper();

    public AppSettingsController(AppSettingsRepository settings) {
        this.settings = settings;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SettingsDto get() {
        return dto(current());
    }

    @PutMapping
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@RequestBody SettingsRequest request) {
        AppSettings s = current();
        s.setAppName(clean(request.appName(), "FFH Verwaltung", 80));
        s.setPrimaryColor(normalizeColor(request.primaryColor(), "#c51f2d"));
        s.setNavColor(normalizeColor(request.navColor(), "#071827"));
        s.setAccentColor(normalizeColor(request.accentColor(), "#1479e9"));
        s.setMenuOrder(normalizeList(request.menuOrder(), DEFAULT_ORDER));
        s.setHiddenMenuItems(normalizeList(request.hiddenMenuItems(), ""));
        if (request.menuLayout() != null) s.setMenuLayout(validateMenuLayout(request.menuLayout()));
        return dto(settings.save(s));
    }

    @PostMapping("/logo")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Bitte ein Logo auswählen.");
        }
        if (file.getSize() > 2_000_000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Das Logo darf maximal 2 MB groß sein.");
        }
        String type = file.getContentType();
        if (type == null || !List.of("image/png", "image/jpeg", "image/webp", "image/svg+xml").contains(type)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Erlaubt sind PNG, JPG, WEBP oder SVG.");
        }
        try {
            AppSettings s = current();
            s.setLogoData(file.getBytes());
            s.setLogoContentType(type);
            return dto(settings.save(s));
        } catch (Exception e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Logo konnte nicht gespeichert werden.", e);
        }
    }

    @DeleteMapping("/logo")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto deleteLogo() {
        AppSettings s = current();
        s.setLogoData(null);
        s.setLogoContentType(null);
        return dto(settings.save(s));
    }

    @GetMapping("/logo")
    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> logo() {
        AppSettings s = current();
        if (s.getLogoData() == null || s.getLogoData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(s.getLogoContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_TYPE, mediaType.toString())
                .body(s.getLogoData());
    }

    private AppSettings current() {
        return settings.findAll().stream().findFirst().orElseGet(() -> {
            AppSettings s = new AppSettings();
            s.setMenuOrder(DEFAULT_ORDER);
            return settings.save(s);
        });
    }

    private SettingsDto dto(AppSettings s) {
        return new SettingsDto(
                s.getAppName(),
                s.getPrimaryColor(),
                s.getNavColor(),
                s.getAccentColor(),
                split(s.getMenuOrder()),
                split(s.getHiddenMenuItems()),
                s.getMenuLayout() == null ? "" : s.getMenuLayout(),
                s.getLogoData() != null && s.getLogoData().length > 0
        );
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .distinct()
                .toList();
    }

    private String normalizeList(List<String> values, String fallback) {
        if (values == null) return fallback;
        return values.stream().map(v -> v == null ? "" : v.trim())
                .filter(v -> !v.isBlank()).distinct()
                .reduce((a, b) -> a + "," + b).orElse(fallback);
    }

    private String clean(String value, String fallback, int max) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().substring(0, Math.min(value.trim().length(), max));
    }

    private String normalizeColor(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.matches("^#[0-9a-fA-F]{6}$") ? v.toLowerCase() : fallback;
    }

    private String validateMenuLayout(String raw) {
        if (raw.length() > 30000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menükonfiguration ist zu groß.");
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isObject() || !node.path("groups").isArray() || !node.path("entries").isObject() || !node.path("style").isObject()) throw new IllegalArgumentException("Ungültige Struktur");
            if (node.path("groups").size() > 30 || node.path("entries").size() > 100) throw new IllegalArgumentException("Zu viele Einträge");
            return raw;
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menükonfiguration ist ungültig."); }
    }

    public record SettingsDto(
            String appName,
            String primaryColor,
            String navColor,
            String accentColor,
            List<String> menuOrder,
            List<String> hiddenMenuItems,
            String menuLayout,
            boolean logoAvailable
    ) {}

    public record SettingsRequest(
            String appName,
            String primaryColor,
            String navColor,
            String accentColor,
            List<String> menuOrder,
            List<String> hiddenMenuItems,
            String menuLayout
    ) {}
}
