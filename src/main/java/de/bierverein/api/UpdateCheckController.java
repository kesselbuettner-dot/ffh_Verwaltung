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
    public UpdateCheckController(ObjectMapper mapper, @Value("${app.version:0.1.0}") String currentVersion, @Value("${app.github.repository:kesselbuettner-dot/ffh_verwaltung}") String repository) {
        this.mapper=mapper; this.currentVersion=currentVersion; this.repository=repository;
    }
    @GetMapping
    public UpdateDto check() {
        try {
            String url="https://api.github.com/repos/"+repository+"/contents/version.json?ref=main";
            String body=RestClient.builder().baseUrl(url).defaultHeader(HttpHeaders.ACCEPT,"application/vnd.github+json").build().get().retrieve().body(String.class);
            JsonNode file=mapper.readTree(body);
            String encoded=file.path("content").asText("").replace("\n","");
            String remoteJson=new String(java.util.Base64.getDecoder().decode(encoded),java.nio.charset.StandardCharsets.UTF_8);
            JsonNode remote=mapper.readTree(remoteJson);
            String remoteVersion=remote.path("version").asText("");
            String remoteCommit=remote.path("commit").asText("");
            boolean available=compareVersions(remoteVersion,currentVersion)>0;
            return new UpdateDto(currentVersion,remoteVersion,remoteCommit,available,available?"Eine neuere veröffentlichte App-Version ist auf GitHub vorhanden.":"Die installierte Version ist aktuell.","https://github.com/"+repository);
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"GitHub konnte nicht geprüft werden"); }
    }
    private int compareVersions(String a,String b) {
        try {
            String[] x=a.replaceFirst("^v","").split("\\\\."); String[] y=b.replaceFirst("^v","").split("\\\\.");
            for(int i=0;i<Math.max(x.length,y.length);i++){ int xi=i<x.length?Integer.parseInt(x[i].replaceAll("\\\\D.*","")):0; int yi=i<y.length?Integer.parseInt(y[i].replaceAll("\\\\D.*","")):0; if(xi!=yi)return Integer.compare(xi,yi); }
        } catch(Exception ignored) {}
        return a.compareToIgnoreCase(b);
    }
    public record UpdateDto(String currentVersion,String remoteVersion,String remoteCommit,boolean updateAvailable,String message,String repositoryUrl){}
}