package de.bierverein.api;
import org.springframework.core.io.*; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import org.springframework.web.server.ResponseStatusException; import org.springframework.transaction.annotation.Transactional;
import java.io.*; import java.net.URLEncoder; import java.nio.charset.StandardCharsets; import java.nio.file.*; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/training")
public class TrainingDocumentController {
 private static final long MAX_FILE_SIZE=20L*1024*1024;
 private static final Set<String> ALLOWED=Set.of("application/pdf","image/jpeg","image/png","image/webp","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.openxmlformats-officedocument.presentationml.presentation","text/plain");
 private final TrainingDocumentRepository documents; private final Path storage=Paths.get("/app/data/training-documents");
 public TrainingDocumentController(TrainingDocumentRepository d){documents=d;}
 @GetMapping @PreAuthorize("@trainingPermissionGuard.allowed(authentication,'training.documents','read')") public List<DocumentDto> all(){return documents.findAll().stream().sorted(Comparator.comparing(TrainingDocument::getUploadedAt,Comparator.nullsLast(Comparator.reverseOrder()))).map(this::dto).toList();}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("@trainingPermissionGuard.allowed(authentication,'training.documents','write')") @Transactional
 public DocumentDto upload(@RequestParam String title,@RequestParam(required=false,defaultValue="") String description,@RequestPart("file") MultipartFile file,org.springframework.security.core.Authentication auth)throws IOException{
  if(title==null||title.isBlank())throw bad("Titel ist erforderlich"); if(file==null||file.isEmpty())throw bad("Bitte eine Datei auswählen"); if(file.getSize()>MAX_FILE_SIZE)throw bad("Die Datei darf maximal 20 MB groß sein");
  String type=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT); if(!ALLOWED.contains(type))throw bad("Dateityp nicht erlaubt. PDF, Bilder, Office-Dateien oder TXT verwenden.");
  Files.createDirectories(storage); String original=Paths.get(Optional.ofNullable(file.getOriginalFilename()).orElse("datei")).getFileName().toString(); String ext=""; int dot=original.lastIndexOf('.'); if(dot>=0)ext=original.substring(dot).replaceAll("[^A-Za-z0-9.]","");
  String stored=UUID.randomUUID()+ext; Files.copy(file.getInputStream(),storage.resolve(stored),StandardCopyOption.REPLACE_EXISTING);
  TrainingDocument d=new TrainingDocument(); d.setTitle(title.trim()); d.setDescription(description==null?null:description.trim()); d.setOriginalFileName(original); d.setStoredFileName(stored); d.setContentType(type); d.setFileSize(file.getSize()); d.setUploadedAt(Instant.now()); d.setUploadedBy(auth.getName());
  return dto(documents.save(d));
 }
 @GetMapping("/{id}/file") @PreAuthorize("@trainingPermissionGuard.allowed(authentication,'training.documents','read')") public ResponseEntity<Resource> download(@PathVariable Long id)throws IOException{
  TrainingDocument d=documents.findById(id).orElseThrow(()->notFound("Dokument nicht gefunden")); Path p=storage.resolve(d.getStoredFileName()).normalize(); if(!p.startsWith(storage.normalize())||!Files.exists(p))throw notFound("Datei nicht gefunden");
  Resource resource=new InputStreamResource(Files.newInputStream(p)); String encoded=URLEncoder.encode(d.getOriginalFileName(),StandardCharsets.UTF_8).replace("+","%20");
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.getContentType())).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename*=UTF-8''"+encoded).contentLength(Files.size(p)).body(resource);
 }
 @DeleteMapping("/{id}") @PreAuthorize("@trainingPermissionGuard.allowed(authentication,'training.documents','delete')") @Transactional public ResponseEntity<Void> delete(@PathVariable Long id)throws IOException{
  TrainingDocument d=documents.findById(id).orElseThrow(()->notFound("Dokument nicht gefunden")); Files.deleteIfExists(storage.resolve(d.getStoredFileName()).normalize()); documents.delete(d); return ResponseEntity.noContent().build();
 }
 private DocumentDto dto(TrainingDocument d){return new DocumentDto(d.getId(),d.getTitle(),d.getDescription(),d.getOriginalFileName(),d.getContentType(),d.getFileSize(),d.getUploadedAt(),d.getUploadedBy());}
 private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);} private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}
 public record DocumentDto(Long id,String title,String description,String fileName,String contentType,long fileSize,Instant uploadedAt,String uploadedBy){}
}
