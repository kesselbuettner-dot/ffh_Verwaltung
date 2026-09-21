package de.bierverein.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/update-check")
@PreAuthorize("hasRole('ADMIN')")
public class UpdateCheckController {
    private final String currentCommit;
    private final String repository;
    private final String updaterUrl;
    private final String updaterToken;

    public UpdateCheckController(
            @Value("${app.git.commit:unknown}") String currentCommit,
            @Value("${app.github.repository:kesselbuettner-dot/ffh_Verwaltung}") String repository,
            @Value("${app.updater.url:http://updater:8091}") String updaterUrl,
            @Value("${app.updater.token:}") String updaterToken) {
        this.currentCommit = currentCommit == null ? "unknown" : currentCommit.trim();
        this.repository = repository;
        this.updaterUrl = updaterUrl;
        this.updaterToken = updaterToken;
    }

    @GetMapping
    public UpdateDto check() {
        try {
            String[] parts = repository.split("/", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9_.-]+")
                    || !parts[1].matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalStateException("GitHub-Repository ist nicht gültig.");
            }

            JsonNode commit = RestClient.builder()
                    .baseUrl("https://api.github.com")
                    .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                    .build()
                    .get()
                    .uri("/repos/{owner}/{repo}/commits/main", parts[0], parts[1])
                    .retrieve()
                    .body(JsonNode.class);
            String latest = commit == null ? "" : commit.path("sha").asText("");
            if (!latest.matches("[0-9a-fA-F]{40}")) {
                throw new IllegalStateException("GitHub hat keinen gültigen Commit geliefert.");
            }
            boolean currentKnown = currentCommit.matches("[0-9a-fA-F]{40}");
            boolean available = false;
            String message = "Installierter Commit unbekannt. Bei lokaler Installation APP_GIT_COMMIT beim Docker-Build setzen.";
            if (currentKnown && currentCommit.equalsIgnoreCase(latest)) {
                message = "Installierter Commit entspricht GitHub main.";
            } else if (currentKnown) {
                JsonNode comparison = RestClient.builder()
                        .baseUrl("https://api.github.com")
                        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .build()
                        .get()
                        .uri("/repos/{owner}/{repo}/compare/{base}...{head}",
                                parts[0], parts[1], currentCommit, latest)
                        .retrieve()
                        .body(JsonNode.class);
                String status = comparison == null ? "" : comparison.path("status").asText("");
                available = "ahead".equals(status) && comparison.path("ahead_by").asInt(0) > 0;
                message = available
                        ? "GitHub main enthält neuere Commits als die installierte Version."
                        : "Installierter Commit weicht von main ab oder liegt vor main. Kein automatisches Update.";
            }
            boolean installAvailable = available && updaterToken != null && !updaterToken.isBlank();
            return new UpdateDto(currentKnown ? currentCommit.substring(0,7) : "unbekannt",
                    latest.substring(0,7), latest, available, message,
                    "https://github.com/" + repository, installAvailable);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "GitHub konnte nicht geprüft werden: " + safeMessage(e), e);
        }
    }

    @GetMapping("/status")
    public JsonNode status() {
        try {
            return RestClient.builder().baseUrl(updaterUrl)
                    .defaultHeader("X-Updater-Token", updaterToken)
                    .build().get().uri("/health").retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Update-Status nicht erreichbar: " + safeMessage(e), e);
        }
    }

    @PostMapping("/install")
    public ResponseEntity<UpdateStartDto> install() {
        if (updaterToken == null || updaterToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Server-Update ist nicht konfiguriert.");
        }
        UpdateDto check = check();
        if (!check.updateAvailable()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new UpdateStartDto(false, check.message()));
        }
        try {
            RestClient.builder().baseUrl(updaterUrl)
                    .defaultHeader("X-Updater-Token", updaterToken)
                    .build().post().uri("/update").retrieve().toBodilessEntity();
            return ResponseEntity.accepted()
                    .body(new UpdateStartDto(true, "Server-Update gestartet. Installationsstatus wird geprüft."));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Updater konnte nicht gestartet werden: " + safeMessage(e), e);
        }
    }

    private String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    public record UpdateDto(String currentVersion, String remoteVersion,
            String remoteCommit, boolean updateAvailable, String message,
            String repositoryUrl, boolean installAvailable) {}
    public record UpdateStartDto(boolean started, String message) {}
}
