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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {
    private static final String DEFAULT_ORDER =
            "dashboard,members,theke,shopping,purchase,inventory,articles,devices,drivebook,devicebook,material,events,firewehr,training,finance,documents,calendar,donations,admin,admin-members,admin-users,admin-settings";
    private static final String DEFAULT_WIDGETS = "messages,dates,stats,stock,finance,quick,offers,status,system";
    private static final String DEFAULT_MESSAGE_ROLES = "ADMIN,VORSTAND";
    private static final Set<String> DASHBOARD_WIDGET_KEYS = Set.of(
            "messages", "dates", "stats", "stock", "finance", "quick", "offers", "status", "system");

    private final AppSettingsRepository settings;

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
        s.setDashboardWidgets(normalizeWidgets(request.dashboardWidgets(), s.getDashboardWidgets() == null ? DEFAULT_WIDGETS : s.getDashboardWidgets()));
        s.setDashboardWidgetOrder(normalizeWidgetOrder(request.dashboardWidgetOrder(),
                s.getDashboardWidgetOrder() == null ? DEFAULT_WIDGETS : s.getDashboardWidgetOrder()));
        s.setDashboardWidgetRoles(normalizeWidgetRoles(request.dashboardWidgetRoles(), s.getDashboardWidgetRoles()));
        s.setMessageEditorRoles(normalizeRoles(request.messageEditorRoles(), s.getMessageEditorRoles() == null ? DEFAULT_MESSAGE_ROLES : s.getMessageEditorRoles()));
        if(request.organizationName()!=null)s.setOrganizationName(optional(request.organizationName(),160));
        if(request.street()!=null)s.setStreet(optional(request.street(),160));
        if(request.postalCode()!=null)s.setPostalCode(optional(request.postalCode(),20));
        if(request.city()!=null)s.setCity(optional(request.city(),120));
        if(request.federalState()!=null)s.setFederalState(normalizeState(request.federalState()));
        if(request.contactEmail()!=null)s.setContactEmail(optional(request.contactEmail(),160));
        if(request.contactPhone()!=null)s.setContactPhone(optional(request.contactPhone(),60));
        if(request.legalRepresentative()!=null)s.setLegalRepresentative(optional(request.legalRepresentative(),160));
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
                split(s.getDashboardWidgets() == null ? DEFAULT_WIDGETS : s.getDashboardWidgets()),
                split(s.getDashboardWidgetOrder() == null ? DEFAULT_WIDGETS : s.getDashboardWidgetOrder()),
                parseWidgetRoles(s.getDashboardWidgetRoles()),
                split(s.getMessageEditorRoles() == null ? DEFAULT_MESSAGE_ROLES : s.getMessageEditorRoles()),
                s.getLogoData() != null && s.getLogoData().length > 0,
                s.getOrganizationName(),s.getStreet(),s.getPostalCode(),s.getCity(),
                s.getFederalState()==null?"SN":s.getFederalState(),s.getContactEmail(),s.getContactPhone(),s.getLegalRepresentative(),
                "Eric Kessel-Büttner","© Eric Kessel-Büttner – Alle Rechte vorbehalten. Nutzung, Vervielfältigung, Veränderung oder Weitergabe nur mit ausdrücklicher schriftlicher Genehmigung des Urhebers."
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

    private String normalizeRoles(List<String> values, String fallback) {
        if (values == null) return fallback;
        String result = values.stream().filter(v -> v != null && !v.isBlank())
                .map(String::trim).map(String::toUpperCase)
                .filter(v -> { try { Role.valueOf(v); return true; } catch (Exception e) { return false; } })
                .distinct().reduce((a, b) -> a + "," + b).orElse("");
        return result.contains("ADMIN") ? result : (result.isBlank() ? "ADMIN" : "ADMIN," + result);
    }

    private String normalizeWidgetOrder(List<String> values, String fallback) {
        if (values == null) return fallback;
        List<String> clean = values.stream().filter(DASHBOARD_WIDGET_KEYS::contains).distinct().toList();
        String missing = split(DEFAULT_WIDGETS).stream().filter(key -> !clean.contains(key))
                .collect(Collectors.joining(","));
        String result = String.join(",", clean);
        if (!missing.isBlank()) result = result.isBlank() ? missing : result + "," + missing;
        return result;
    }

    private String normalizeWidgets(List<String> values, String fallback) {
        if (values == null) return fallback;
        return values.stream().filter(DASHBOARD_WIDGET_KEYS::contains).distinct()
                .collect(Collectors.joining(","));
    }

    private String normalizeWidgetRoles(Map<String, List<String>> values, String fallback) {
        if (values == null) return fallback;
        Map<String, List<String>> clean = new LinkedHashMap<>();
        for (String key : split(DEFAULT_WIDGETS)) {
            if (!values.containsKey(key)) continue;
            List<String> roleValues = values.get(key) == null ? List.of() : values.get(key).stream()
                    .filter(v -> v != null && !v.isBlank()).map(String::trim).map(String::toUpperCase)
                    .filter(v -> { try { Role.valueOf(v); return true; } catch (Exception e) { return false; } })
                    .distinct().toList();
            if (!roleValues.contains("ADMIN")) {
                roleValues = new java.util.ArrayList<>(roleValues);
                roleValues.add(0, "ADMIN");
            }
            clean.put(key, roleValues);
        }
        return clean.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.join("|", e.getValue()))
                .collect(Collectors.joining(";"));
    }

    private Map<String, List<String>> parseWidgetRoles(String value) {
        Map<String, List<String>> result = defaultWidgetRoles();
        if (value == null || value.isBlank()) return result;
        for (String entry : value.split(";")) {
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || !DASHBOARD_WIDGET_KEYS.contains(pair[0])) continue;
            List<String> configured = Arrays.stream(pair[1].split("\\|"))
                    .map(String::trim).filter(v -> !v.isBlank()).distinct().toList();
            result.put(pair[0], configured);
        }
        return result;
    }

    private Map<String, List<String>> defaultWidgetRoles() {
        List<String> everyone = Arrays.stream(Role.values()).map(Enum::name).toList();
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("messages", everyone);
        defaults.put("dates", everyone);
        defaults.put("stats", everyone);
        defaults.put("stock", everyone);
        defaults.put("finance", List.of("ADMIN", "VORSTAND", "KASSENWART"));
        defaults.put("quick", everyone);
        defaults.put("offers", List.of("ADMIN", "GETRAENKEWART"));
        defaults.put("status", everyone);
        defaults.put("system", everyone);
        return defaults;
    }

    private String clean(String value, String fallback, int max) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().substring(0, Math.min(value.trim().length(), max));
    }

    private String normalizeColor(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.matches("^#[0-9a-fA-F]{6}$") ? v.toLowerCase() : fallback;
    }
    private String optional(String value,int max){if(value==null||value.isBlank())return null;String v=value.trim();return v.substring(0,Math.min(max,v.length()));}
    private String normalizeState(String value){String v=value.trim().toUpperCase();return Set.of("BW","BY","BE","BB","HB","HH","HE","MV","NI","NW","RP","SL","SN","ST","SH","TH").contains(v)?v:"SN";}

    public record SettingsDto(
            String appName,
            String primaryColor,
            String navColor,
            String accentColor,
            List<String> menuOrder,
            List<String> hiddenMenuItems,
            List<String> dashboardWidgets,
            List<String> dashboardWidgetOrder,
            Map<String, List<String>> dashboardWidgetRoles,
            List<String> messageEditorRoles,
            boolean logoAvailable,String organizationName,String street,String postalCode,String city,String federalState,
            String contactEmail,String contactPhone,String legalRepresentative,String softwareAuthor,String licenseNotice
    ) {}

    public record SettingsRequest(
            String appName,
            String primaryColor,
            String navColor,
            String accentColor,
            List<String> menuOrder,
            List<String> hiddenMenuItems,
            List<String> dashboardWidgets,
            List<String> dashboardWidgetOrder,
            Map<String, List<String>> dashboardWidgetRoles,
            List<String> messageEditorRoles,String organizationName,String street,String postalCode,String city,String federalState,
            String contactEmail,String contactPhone,String legalRepresentative
    ) {}
}
