package de.bierverein.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VersionController {
    @Value("${app.version:1.0.0}") private String currentVersion;
    @Value("${app.github.owner:kesselbuettner-dot}") private String owner;
    @Value("${app.github.repository:ffh_Verwaltung}") private String repository;

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient client = RestClient.builder()
            .defaultHeader("Accept", "application/vnd.github+json")
            .defaultHeader("User-Agent", "FFH-Verwaltung")
            .build();

    @GetMapping("/version")
    public Map<String,Object> version() {
        return Map.of("version", currentVersion, "repository", owner + "/" + repository);
    }

    @GetMapping("/admin/update-check")
    public ResponseEntity<Map<String,Object>> updateCheck() {
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("currentVersion", currentVersion);
        try {
            String body = client.get()
                    .uri("https://api.github.com/repos/{owner}/{repo}/releases/latest", owner, repository)
                    .retrieve().body(String.class);
            JsonNode release = mapper.readTree(body);
            String tag = release.path("tag_name").asText("");
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;
            r.put("latestVersion", latest);
            r.put("updateAvailable", isNewer(latest, currentVersion));
            r.put("name", release.path("name").asText(""));
            r.put("body", release.path("body").asText(""));
            r.put("htmlUrl", release.path("html_url").asText(""));
        } catch (Exception e) {
            r.put("updateAvailable", false);
            r.put("error", "GitHub-Version konnte nicht geprüft werden.");
        }
        return ResponseEntity.ok(r);
    }

    private boolean isNewer(String latest, String current) {
        try {
            String[] a=latest.split("\\."); String[] b=current.split("\\.");
            for(int i=0;i<Math.max(a.length,b.length);i++){
                int av=i<a.length?Integer.parseInt(a[i].replaceAll("[^0-9].*","")):0;
                int bv=i<b.length?Integer.parseInt(b[i].replaceAll("[^0-9].*","")):0;
                if(av!=bv) return av>bv;
            }
        } catch(Exception ignored){}
        return false;
    }
}