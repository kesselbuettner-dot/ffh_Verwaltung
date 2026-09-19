package de.bierverein.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/admin/update-check")
@PreAuthorize("hasRole('ADMIN')")
public class UpdateCheckController {
    private final ObjectMapper mapper;
    private final String currentVersion;
    private final String repository;
    private final String updaterUrl;
    private final String updaterToken;

    public UpdateCheckController(ObjectMapper mapper,
            @Value("${app.version:1.1.0}") String currentVersion,
            @Value("${app.github.repository:kesselbuettner-dot/ffh_Verwaltung}") String repository,
            @Value("${app.updater.url:http://updater:8091}") String updaterUrl,
            @Value("${app.updater.token:}") String updaterToken) {
        this.mapper = mapper;
        this.currentVersion = currentVersion;
        this.repository = repository;
        this.updaterUrl = updaterUrl;
        this.updaterToken = updaterToken;
    }

    @GetMapping
    public UpdateDto check() {
        try {
            JsonNode release = RestClient.builder().baseUrl("https://api.github.com")
                    .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .defaultHeader("X-GitHub-Api-Version", "2026-03-10")
                    .build().get().uri("/repos/{owner}/{repo}/releases/latest",
                            repository.substring(0, repository.indexOf('/')),
                            repository.substring(repository.indexOf('/') + 1))
                    .retrieve().body(JsonNode.class);
            String remoteVersion = release.path("tag_name").asText("");
            String remoteCommit = release.path("target_commitish").asText("");
            boolean available = compareVersions(remoteVersion, currentVersion) > 0;
            return new UpdateDto(currentVersion, remoteVersion, remoteCommit, available,
                    available ? "Eine neue veröffentlichte Version ist auf GitHub vorhanden." : "Die installierte Version ist aktuell.",
                    "https://github.com/" + repository, updaterToken != null && !updaterToken.isBlank());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub konnte nicht geprüft werden: " + safeMessage(e));
        }
    }

    @PostMapping("/install")
    public ResponseEntity<UpdateStartDto> install() {
        if (updaterToken == null || updaterToken.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Server-Update ist nicht konfiguriert.");
        UpdateDto check = check();
        if (!check.updateAvailable())
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new UpdateStartDto(false, "Kein neues Release verfügbar."));
        try {
            RestClient.builder().baseUrl(updaterUrl).defaultHeader("X-Updater-Token", updaterToken)
                    .build().post().uri("/update").retrieve().toBodilessEntity();
            return ResponseEntity.accepted().body(new UpdateStartDto(true, "Update wurde auf dem Server gestartet. Die Anwendung wird gleich neu gestartet."));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Update-Server konnte nicht erreicht werden: " + safeMessage(e));
        }
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    private int compareVersions(String a, String b) {
        try {
            String[] x = a.replaceFirst("^v", "").split("\\.");
            String[] y = b.replaceFirst("^v", "").split("\\.");
            for (int i = 0; i < Math.max(x.length, y.length); i++) {
                int xi = i < x.length ? Integer.parseInt(x[i].replaceAll("\\D.*", "")) : 0;
                int yi = i < y.length ? Integer.parseInt(y[i].replaceAll("\\D.*", "")) : 0;
                if (xi != yi) return Integer.compare(xi, yi);
            }
        } catch (Exception ignored) {}
        return a.compareToIgnoreCase(b);
    }

    public record UpdateDto(String currentVersion, String remoteVersion, String remoteCommit, boolean updateAvailable, String message, String repositoryUrl, boolean installAvailable) {}
    public record UpdateStartDto(boolean started, String message) {}
}