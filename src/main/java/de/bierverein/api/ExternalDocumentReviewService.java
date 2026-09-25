package de.bierverein.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Optional manually triggered OpenAI-compatible API. No network connection by default. */
@Service
public class ExternalDocumentReviewService {
 private final String endpoint,key,model;
 private final ObjectMapper json;
 private final DocumentExternalReviewAuditRepository audits;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
   .followRedirects(HttpClient.Redirect.NEVER).build();
 public ExternalDocumentReviewService(
   @Value("${app.document-ai.endpoint:}") String endpoint,
   @Value("${app.document-ai.key:}") String key,
   @Value("${app.document-ai.model:}") String model,
   ObjectMapper json,DocumentExternalReviewAuditRepository audits){
  this.endpoint=endpoint;this.key=key;this.model=model;this.json=json;this.audits=audits;
 }
 public boolean enabled(){
  try{
   URI uri=URI.create(endpoint);String host=uri.getHost();
   return "https".equalsIgnoreCase(uri.getScheme())&&uri.getUserInfo()==null&&uri.getPort()==-1
    &&host!=null&&!host.isBlank()&&!host.equalsIgnoreCase("localhost")
    &&!host.endsWith(".local")&&!host.matches("(?i)(?:127|10|192|169)\\..*")
    &&!key.isBlank()&&!model.isBlank();
  }catch(Exception ex){return false;}
 }
 public String review(Long documentId,int version,String user,String approvedExcerpt){
  if(!enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
    "Kein externer Dienst konfiguriert; lokale Erkennung bleibt nutzbar.");
  if(approvedExcerpt==null||approvedExcerpt.isBlank()||approvedExcerpt.length()>6000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte höchstens 6.000 geprüfte Zeichen eingeben.");
  // The operator manually selects and redacts the submitted excerpt. NEVER read original files here.
  try{
   String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
     .digest(approvedExcerpt.getBytes(StandardCharsets.UTF_8)));
   var audit=audits.save(new DocumentExternalReviewAudit(documentId,version,user,fingerprint,URI.create(endpoint).getHost()));
   String prompt="Erstelle ausschließlich Vorschläge zur Kategorisierung von nicht vertraulichen technischen Dokumenten. "+
     "Nenne gegebenenfalls Dokumententyp, Hersteller, Seriennummer und Prüfdaten. "+
     "Erfinde keine Werte. Werte sind nicht bestätigt. Antworte auf Deutsch.";
   var request=Map.of("model",model,"messages",List.of(
     Map.of("role","system","content",prompt),
     Map.of("role","user","content",approvedExcerpt)),"temperature",0);
   HttpRequest http=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(18))
     .header("Authorization","Bearer "+key).header("Content-Type","application/json")
     .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build();
   try{
    var response=client.send(http,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    audit.result=(response.statusCode()==200?"OK":"FAILED");audits.save(audit);
    if(response.statusCode()!=200)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Externe Auswertung fehlgeschlagen");
    String answer=json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
    return answer.length()>4000?answer.substring(0,4000):answer;
   }catch(Exception e){
    audit.result="FAILED";audits.save(audit);
    if(e instanceof InterruptedException)Thread.currentThread().interrupt();
    if(e instanceof ResponseStatusException r)throw r;
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Externer Dienst antwortet nicht.");
   }
  }catch(ResponseStatusException r){throw r;}
   catch(Exception e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Externe Auswertung nicht verfügbar");}
 }
}
