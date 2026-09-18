package de.bierverein.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class UpdateController {

    private final RestClient client = RestClient.builder().build();

    @Value("${app.updater.url:http://updater:8090}")
    private String updaterUrl;

    @Value("${app.updater.token:}")
    private String updaterToken;

    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> update() {
        if (updaterToken == null || updaterToken.isBlank()) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Update-Dienst ist nicht konfiguriert."));
        }

        try {
            client.post()
                    .uri(updaterUrl + "/update")
                    .header("X-Updater-Token", updaterToken)
                    .retrieve()
                    .toBodilessEntity();

            return ResponseEntity.accepted()
                    .body(Map.of("message", "Update wurde gestartet."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Update konnte nicht gestartet werden."));
        }
    }
}
