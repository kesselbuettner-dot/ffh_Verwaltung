package de.bierverein.api;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/fire/qualification-attachments")
public class FireQualificationAttachmentController {
 private final FireQualificationAttachmentRepository attachments;
 private final FireMemberQualificationRepository assigned;
 private final FireQualificationPermissions permission;
 public FireQualificationAttachmentController(FireQualificationAttachmentRepository attachments,FireMemberQualificationRepository assigned,FireQualificationPermissions permission){
  this.attachments=attachments;this.assigned=assigned;this.permission=permission;
 }
 private FireMemberQualification require(Long id,Authentication authentication){
  FireMemberQualification q=assigned.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(q.type.sensitive&&!permission.allowed(authentication,"fire.qualifications.sensitive.read"))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Keine Berechtigung für vertrauliche Dokumente");
  return q;
 }
 public record DocumentInfo(String filename,java.time.Instant uploadedAt,long size){}
 @GetMapping("/{id}/info")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public ResponseEntity<DocumentInfo> info(@PathVariable Long id,Authentication auth){
  require(id,auth);
  return attachments.findByQualificationId(id).map(a->ResponseEntity.ok(new DocumentInfo(a.filename,a.uploadedAt,a.bytes.length)))
     .orElseGet(()->ResponseEntity.noContent().build());
 }
 @GetMapping("/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public ResponseEntity<byte[]> read(@PathVariable Long id,Authentication auth){
  require(id,auth);
  FireQualificationAttachment doc=attachments.findByQualificationId(id)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Dokument nicht vorhanden"));
  return ResponseEntity.ok()
    .contentType(MediaType.APPLICATION_PDF)
    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"qualifikation-"+id+".pdf\"")
    .header(HttpHeaders.CACHE_CONTROL,"no-store, private")
    .header("X-Content-Type-Options","nosniff")
    .body(doc.bytes);
 }
 @PutMapping(value="/{id}",consumes="application/pdf")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public DocumentInfo upload(@PathVariable Long id,@RequestBody byte[] bytes,Authentication auth){
  require(id,auth);
  if(bytes==null||bytes.length<8||bytes.length>5_000_000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF muss zwischen 8 Byte und 5 MB groß sein");
  if(!Arrays.equals(Arrays.copyOf(bytes,5),"%PDF-".getBytes(StandardCharsets.US_ASCII)))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige PDF-Signatur");
  FireQualificationAttachment doc=attachments.findByQualificationId(id).orElseGet(()->new FireQualificationAttachment(id,"nachweis.pdf",bytes));
  doc.filename="nachweis.pdf";doc.bytes=bytes;doc.uploadedAt=java.time.Instant.now();
  attachments.save(doc);return new DocumentInfo(doc.filename,doc.uploadedAt,doc.bytes.length);
 }
 @DeleteMapping("/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public ResponseEntity<Void> delete(@PathVariable Long id,Authentication auth){
  require(id,auth);attachments.findByQualificationId(id).ifPresent(attachments::delete);
  return ResponseEntity.noContent().build();
 }
}
