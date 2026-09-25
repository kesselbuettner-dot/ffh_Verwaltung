package de.bierverein.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** A private, permission-checked document vault. Files are NEVER published through /uploads/. */
@Controller
@RequestMapping("/api/document-center")
public class DocumentCenterController {
 private static final long MAX=20L*1024*1024;
 private static final Set<String> MIME=Set.of("application/pdf","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document");
 private static final Set<String> CATEGORIES=Set.of("GENERAL","DEVICE","TRAINING","VEHICLE","CLUB");
 private static final Set<String> VISIBILITY=Set.of("RESTRICTED","MEMBERS");
 private final ArchiveDocumentRepository docs;
 private final ArchiveDocumentRevisionRepository revisions;
 private final AppUserRepository users;
 private final EffectivePermissionService permissions;
 private final DocumentTextRecognitionService recognition;
 private final DocumentSuggestionsService suggestions;
 private final DeviceRepository devices;
 private final ExternalDocumentReviewService external;
 private final Path storage;
 public DocumentCenterController(ArchiveDocumentRepository d,ArchiveDocumentRevisionRepository r,AppUserRepository u,
    EffectivePermissionService p,DocumentTextRecognitionService recognition,DocumentSuggestionsService suggestions,
    DeviceRepository devices,ExternalDocumentReviewService external,@Value("${app.private-documents.path:/app/private-documents}") String dir){
  docs=d;revisions=r;users=u;permissions=p;this.recognition=recognition;this.suggestions=suggestions;this.devices=devices;this.external=external;
  storage=Paths.get(dir).toAbsolutePath().normalize();
 }
 public record Input(String title,String description,String category,String visibility,LocalDate expiresOn){}
 public record DocumentView(Long id,String title,String description,String category,String visibility,LocalDate expiresOn,
   String owner,Instant createdAt,Instant updatedAt,int version,boolean canEdit,boolean canDelete){}
 public record RevisionView(int version,String fileName,String contentType,long sizeBytes,Instant uploadedAt,
   String uploadedBy,String extractionMethod,String extractionWarning){}
 public record DeviceCandidate(Long id,String name,String inventoryNumber,String serialNumber){}
 public record AnalysisView(int version,String extractionMethod,String warning,DocumentSuggestionsService.SuggestedFields suggestions,List<DeviceCandidate> matchedDevices,String reviewedTextPreview){}
 public record Overview(boolean canWrite,boolean canDelete,List<DocumentView> documents){}
 private ResponseStatusException error(HttpStatus code,String message){return new ResponseStatusException(code,message);}
 private AppUser actor(Authentication auth){
  if(auth==null)throw error(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich");
  AppUser u=users.findByUsernameWithMember(auth.getName()).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Unbekannter Benutzer"));
  if(!u.isEnabled()||!u.isRegistrationApproved())throw error(HttpStatus.FORBIDDEN,"Konto nicht freigeschaltet");
  return u;
 }
 private boolean board(AppUser u){return u.getRole()==Role.ADMIN||u.getRole()==Role.VORSTAND;}
 private boolean allowed(AppUser u,String action){return board(u)||permissions.hasPermission(u.getId(),"documents."+action);}
 private void require(AppUser u,String action){if(!allowed(u,action))throw error(HttpStatus.FORBIDDEN,"Keine Berechtigung für die Dokumentenverwaltung");}
 private boolean visible(AppUser u,ArchiveDocument d){return board(u)||("MEMBERS".equals(d.visibility)&&allowed(u,"read"));}
 private ArchiveDocument accessible(Long id,AppUser u){require(u,"read");ArchiveDocument d=docs.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Dokument unbekannt"));if(d.archived||!visible(u,d))throw error(HttpStatus.NOT_FOUND,"Dokument nicht verfügbar");return d;}
 private void requireManage(AppUser u,ArchiveDocument d,String operation){
  require(u,operation);if(!board(u)&&!"MEMBERS".equals(d.visibility))throw error(HttpStatus.FORBIDDEN,"Vertrauliche Dokumente sind dem Vorstand vorbehalten");
 }
 private void validate(Input input,AppUser u){
  if(input==null||input.title()==null||input.title().isBlank()||input.title().length()>160)throw error(HttpStatus.BAD_REQUEST,"Titel erforderlich (max. 160 Zeichen)");
  if(input.description()!=null&&input.description().length()>2000)throw error(HttpStatus.BAD_REQUEST,"Beschreibung zu lang");
  if(!CATEGORIES.contains(input.category()))throw error(HttpStatus.BAD_REQUEST,"Ungültige Kategorie");
  if(!VISIBILITY.contains(input.visibility()))throw error(HttpStatus.BAD_REQUEST,"Ungültige Sichtbarkeit");
  if(!board(u)&&!"MEMBERS".equals(input.visibility()))throw error(HttpStatus.FORBIDDEN,"Vertrauliche Dokumente dürfen nur Vorstand oder Administration verwalten");
 }
 private DocumentView view(ArchiveDocument d,AppUser u){
  boolean edit=allowed(u,"write")&&(board(u)||"MEMBERS".equals(d.visibility));
  boolean delete=allowed(u,"delete")&&(board(u)||"MEMBERS".equals(d.visibility));
  return new DocumentView(d.id,d.title,d.description,d.category,d.visibility,d.expiresOn,d.owner,d.createdAt,d.updatedAt,d.currentVersion,edit,delete);
 }
 @GetMapping @ResponseBody @Transactional(readOnly=true)
 public Overview list(@RequestParam(defaultValue="") String query,Authentication auth){
  AppUser u=actor(auth);require(u,"read");
  String q=query.trim().toLowerCase(Locale.GERMAN);
  if(q.length()>120)throw error(HttpStatus.BAD_REQUEST,"Suchbegriff zu lang");
  List<DocumentView> out=new ArrayList<>();
  for(ArchiveDocument d:docs.findByArchivedFalseOrderByUpdatedAtDesc()){
   if(!visible(u,d))continue;
   if(!q.isBlank()){
    boolean meta=(d.title+" "+Objects.toString(d.description,"")+" "+d.category).toLowerCase(Locale.GERMAN).contains(q);
    // Search all retained revisions, not only the latest title or filename.
    boolean body=!meta&&revisions.findByDocumentIdOrderByVersionNumberDesc(d.id).stream()
      .anyMatch(v->(v.originalName+" "+Objects.toString(v.extractedText,"")).toLowerCase(Locale.GERMAN).contains(q));
    if(!meta&&!body)continue;
   }
   out.add(view(d,u));
  }
  return new Overview(allowed(u,"write"),allowed(u,"delete"),out);
 }
 @GetMapping("/{id}/versions") @ResponseBody @Transactional(readOnly=true)
 public List<RevisionView> versions(@PathVariable Long id,Authentication auth){
  accessible(id,actor(auth));
  return revisions.findByDocumentIdOrderByVersionNumberDesc(id).stream()
    .map(v->new RevisionView(v.versionNumber,v.originalName,v.contentType,v.sizeBytes,v.uploadedAt,v.uploadedBy,v.extractionMethod,v.extractionWarning)).toList();
 }
 /** Read-only suggestions. Requires document read rights, and does not change a device or inspection. */
 @GetMapping("/{id}/versions/{version}/analysis") @ResponseBody @Transactional(readOnly=true)
 public AnalysisView analysis(@PathVariable Long id,@PathVariable int version,Authentication auth){
  AppUser u=actor(auth);accessible(id,u);
  ArchiveDocumentRevision rev=revisions.findByDocumentIdAndVersionNumber(id,version)
    .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Version unbekannt"));
  var fields=suggestions.suggest(rev.extractedText,rev.originalName);
  List<DeviceCandidate> matches=List.of();
  if(board(u)||permissions.hasPermission(u.getId(),"fire.devices.read")){
   var found=new LinkedHashMap<Long,Device>();
   if(fields.serialNumber()!=null)devices.findFirstBySerialNumberAndActiveTrue(fields.serialNumber().value())
     .ifPresent(d->found.put(d.getId(),d));
   // Conservative: do not match vague free text. Inventory numbers are suggested in a later release.
   matches=found.values().stream().map(d->new DeviceCandidate(d.getId(),d.getName(),d.getInventoryNumber(),d.getSerialNumber())).toList();
  }
  return new AnalysisView(version,rev.extractionMethod,rev.extractionWarning,fields,matches,board(u)?Objects.toString(rev.extractedText,"").substring(0,Math.min(6000,Objects.toString(rev.extractedText,"").length())):null);
 }
 /** Only board users can submit a specifically reviewed, manually redacted excerpt.
  * Confidential documents are categorically blocked even if a checkbox was sent. */
 public record ExternalReviewRequest(boolean approvedNonConfidential,String reviewedExcerpt){}
 public record ExternalReviewResponse(String suggestion){}
 @GetMapping("/{id}/versions/{version}/external-status") @ResponseBody @Transactional(readOnly=true)
 public Map<String,Boolean> externalStatus(@PathVariable Long id,@PathVariable int version,Authentication auth){
  AppUser u=actor(auth);ArchiveDocument d=accessible(id,u);
  ArchiveDocumentRevision rev=revisions.findByDocumentIdAndVersionNumber(id,version).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Version unbekannt"));
  return Map.of("available",board(u)&&"MEMBERS".equals(d.visibility)&&external.enabled()&&!"PENDING".equals(rev.extractionMethod));
 }
 @PostMapping("/{id}/versions/{version}/external-review") @ResponseBody
 public ExternalReviewResponse externalReview(@PathVariable Long id,@PathVariable int version,
   @RequestBody ExternalReviewRequest request,Authentication auth){
  AppUser u=actor(auth);if(!board(u))throw error(HttpStatus.FORBIDDEN,"Nur Vorstand oder Administration dürfen externe Auswertungen freigeben.");
  ArchiveDocument d=accessible(id,u);
  if(!"MEMBERS".equals(d.visibility))throw error(HttpStatus.FORBIDDEN,"Vertrauliche Dokumente dürfen niemals extern übertragen werden.");
  revisions.findByDocumentIdAndVersionNumber(id,version).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Version unbekannt"));
  if(request==null||!request.approvedNonConfidential())
   throw error(HttpStatus.BAD_REQUEST,"Ausdrückliche Bestätigung für nicht vertrauliche Inhalte erforderlich.");
  return new ExternalReviewResponse(external.review(id,version,u.getUsername(),request.reviewedExcerpt()));
 }
 private String detected(byte[] data,String original,String declared){
  String name=original.toLowerCase(Locale.ROOT);
  if(name.endsWith(".pdf")&&data.length>4&&new String(data,0,Math.min(5,data.length),StandardCharsets.ISO_8859_1).startsWith("%PDF-"))return "application/pdf";
  if(name.endsWith(".docx")&&data.length>3&&data[0]==0x50&&data[1]==0x4b)return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  if((name.endsWith(".jpg")||name.endsWith(".jpeg"))&&data.length>3&&(data[0]&255)==255&&(data[1]&255)==216&&(data[2]&255)==255)return "image/jpeg";
  if(name.endsWith(".png")&&data.length>7&&(data[0]&255)==137&&data[1]==80&&data[2]==78&&data[3]==71)return "image/png";
  if(name.endsWith(".txt")&&"text/plain".equalsIgnoreCase(declared)&&!new String(data,StandardCharsets.UTF_8).contains("\uFFFD"))return "text/plain";
  throw error(HttpStatus.BAD_REQUEST,"Datei muss PDF, DOCX, JPG, PNG oder UTF-8 TXT sein");
 }
 private String index(byte[] data,String mime){
  try{
   String result=switch(mime){
    case "application/pdf" -> {try(var pdf=Loader.loadPDF(data)){yield new PDFTextStripper().getText(pdf);}}
    case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {try(var doc=new XWPFDocument(new ByteArrayInputStream(data));var extractor=new XWPFWordExtractor(doc)){yield extractor.getText();}}
    case "text/plain" -> new String(data,StandardCharsets.UTF_8);
    default -> "";
   };
   return result.length()>21000?result.substring(0,21000):result;
  }catch(Exception e){return "";} // A scan without selectable text stays searchable by title/metadata.
 }
 private ArchiveDocumentRevision store(Long id,int version,MultipartFile file,String username)throws IOException{
  if(file==null||file.isEmpty()||file.getSize()>MAX)throw error(HttpStatus.BAD_REQUEST,"Datei erforderlich (max. 20 MB)");
  String original=Paths.get(Objects.toString(file.getOriginalFilename(),"datei")).getFileName().toString();
  if(original.length()>250)original=original.substring(original.length()-250);
  byte[] data=file.getBytes();if(data.length>MAX)throw error(HttpStatus.BAD_REQUEST,"Datei zu groß");
  String type=detected(data,original,Objects.toString(file.getContentType(),""));
  Files.createDirectories(storage);
  String stored=UUID.randomUUID().toString();
  Files.write(storage.resolve(stored),data,StandardOpenOption.CREATE_NEW);
  ArchiveDocumentRevision rev=new ArchiveDocumentRevision(id,version,original,stored,type,data.length,"",username);
  rev.extractionMethod="PENDING";rev.extractionWarning="Lokale Texterkennung läuft im Hintergrund.";
  return revisions.save(rev);
 }
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseBody @Transactional
 public DocumentView create(@RequestPart("metadata") Input input,@RequestPart("file") MultipartFile file,Authentication auth)throws IOException{
  AppUser u=actor(auth);require(u,"write");validate(input,u);
  ArchiveDocument d=docs.save(new ArchiveDocument(input.title().trim(),Objects.toString(input.description(),"").trim(),input.visibility(),input.category(),input.expiresOn(),u.getUsername()));
  store(d.id,1,file,u.getUsername());d.currentVersion=1;return view(docs.save(d),u);
 }
 @PostMapping(path="/{id}/versions",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseBody @Transactional
 public DocumentView addVersion(@PathVariable Long id,@RequestPart("file") MultipartFile file,Authentication auth)throws IOException{
  AppUser u=actor(auth);ArchiveDocument d=docs.findLocked(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Dokument unbekannt"));
  if(d.archived)throw error(HttpStatus.NOT_FOUND,"Dokument archiviert");
  requireManage(u,d,"write");
  store(id,d.currentVersion+1,file,u.getUsername());d.currentVersion++;d.updatedAt=Instant.now();return view(docs.save(d),u);
 }
 @PutMapping("/{id}") @ResponseBody @Transactional
 public DocumentView edit(@PathVariable Long id,@RequestBody Input input,Authentication auth){
  AppUser u=actor(auth);ArchiveDocument d=docs.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Dokument unbekannt"));
  if(d.archived)throw error(HttpStatus.NOT_FOUND,"Dokument archiviert");
  requireManage(u,d,"write");validate(input,u);
  d.title=input.title().trim();d.description=Objects.toString(input.description(),"").trim();d.category=input.category();d.visibility=input.visibility();d.expiresOn=input.expiresOn();d.updatedAt=Instant.now();
  return view(docs.save(d),u);
 }
 @GetMapping("/{id}/versions/{version}/file") @Transactional(readOnly=true)
 public ResponseEntity<Resource> file(@PathVariable Long id,@PathVariable int version,Authentication auth)throws IOException{
  accessible(id,actor(auth));
  ArchiveDocumentRevision rev=revisions.findByDocumentIdAndVersionNumber(id,version).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Version unbekannt"));
  Path target=storage.resolve(rev.storageName).normalize();
  if(!target.startsWith(storage)||!Files.isRegularFile(target))throw error(HttpStatus.NOT_FOUND,"Datei fehlt");
  MediaType media=MediaType.parseMediaType(rev.contentType);
  org.springframework.http.ContentDisposition disposition=org.springframework.http.ContentDisposition.builder(media.equals(MediaType.APPLICATION_PDF)?"inline":"attachment")
    .filename(rev.originalName,StandardCharsets.UTF_8).build();
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
    .contentType(media).header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
    .body(new FileSystemResource(target));
 }
 @DeleteMapping("/{id}") @ResponseBody @Transactional
 public ResponseEntity<Void> archive(@PathVariable Long id,Authentication auth){
  AppUser u=actor(auth);ArchiveDocument d=docs.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Dokument unbekannt"));
  requireManage(u,d,"delete");d.archived=true;d.updatedAt=Instant.now();docs.save(d);
  return ResponseEntity.noContent().build(); // Retain versions for audit/backup, never silently remove history.
 }
}
