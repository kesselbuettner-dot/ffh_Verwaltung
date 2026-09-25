package de.bierverein.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExternalDocumentReviewServiceTest {
 private final DocumentExternalReviewAuditRepository audits=mock(DocumentExternalReviewAuditRepository.class);
 private ExternalDocumentReviewService service(String endpoint,String host,String key){
  return new ExternalDocumentReviewService(endpoint,key,"model",host,new ObjectMapper(),audits);
 }
 @Test void disabledByDefaultAndWithoutNetworkOrAudit(){
  var disabled=service("","","");
  assertFalse(disabled.enabled());
  assertThrows(ResponseStatusException.class,()->disabled.review(1L,1,"board","approved text"));
  verifyNoInteractions(audits);
 }
 @Test void localhostOrUnpinnedHostsCannotBeConfigured(){
  assertFalse(service("http://example.org/api","example.org","key").enabled());
  assertFalse(service("https://127.0.0.1/api","127.0.0.1","key").enabled());
  assertFalse(service("https://example.org/api","different.org","key").enabled());
  assertTrue(service("https://example.org/v1/chat/completions","example.org","key").enabled());
  verifyNoInteractions(audits);
 }
}
