package de.bierverein.api;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DrivingCheckService {
 private final FireMemberQualificationRepository qualifications;
 private final FireQualificationCheckRepository checks;
 private final AppUserRepository users;
 private final FireQualificationPermissions permission;
 private final String pepper;
 private final TransientLicenseOcrService ocr;
 private final java.util.concurrent.ConcurrentHashMap<Long,AttemptWindow> attempts=new java.util.concurrent.ConcurrentHashMap<>();
 private record AttemptWindow(Instant started,int count){}
 private void checkOcrRateLimit(Long uid){
  // Per-account CPU and reference-guessing protection, no document values in keys or logs.
  attempts.compute(uid,(key,window)->{
   Instant now=Instant.now();
   if(window==null||window.started().plusSeconds(900).isBefore(now))return new AttemptWindow(now,1);
   if(window.count()>=6)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
     "Zu viele Prüfversuche. Bitte 15 Minuten warten oder Wehrleitung kontaktieren.");
   return new AttemptWindow(window.started(),window.count()+1);
  });
  if(attempts.size()>2000)attempts.entrySet().removeIf(e->e.getValue().started().plusSeconds(900).isBefore(Instant.now()));
 }
 public DrivingCheckService(FireMemberQualificationRepository qualifications,FireQualificationCheckRepository checks,
    AppUserRepository users,FireQualificationPermissions permission,
    @Value("${app.jwt.secret}") String pepper,TransientLicenseOcrService ocr) {
  this.qualifications=qualifications;this.checks=checks;this.users=users;this.permission=permission;this.pepper=pepper;this.ocr=ocr;
 }
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private String norm(String value){
  // Never store or return the original document number. This is only a transient comparison key.
  return Normalizer.normalize((value==null?"":value).replace("ß","SS").replace("ẞ","SS"),Normalizer.Form.NFD)
   .replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
 }
 private String nameKey(String value){
  // German ID spellings: MÜLLER, MUELLER and MULLER must match the same member name.
  return norm(value).replace("AE","A").replace("OE","O").replace("UE","U");
 }
 private boolean matchesName(String ocrText,String memberName){
  String recognized=nameKey(ocrText);
  String expected=nameKey(memberName);
  if(expected.length()<4)return false;
  if(recognized.contains(expected))return true;
  // OCR places first and last name on separate lines; additional middle names need not be printed.
  String[] parts=memberName.trim().split("[\\s,]+");
  if(parts.length<2)return false;
  String first=nameKey(parts[0]),last=nameKey(parts[parts.length-1]);
  return first.length()>=2&&last.length()>=2&&recognized.contains(first)&&recognized.contains(last);
 }
 private boolean matchesNumber(String ocrText,String savedReferenceMac){
  if(savedReferenceMac==null||!savedReferenceMac.matches("[0-9a-f]{64}"))return false;
  byte[] reference=savedReferenceMac.getBytes(StandardCharsets.US_ASCII);
  // Tesseract may insert spaces, hyphens, or short line-internal breaks in a document number.
  // Only compare exact normalized strings (never OCR-confusable substitutions).
  for(String line:ocrText.split("[\\r\\n]+")){
   String[] words=Arrays.stream(line.split("[^\\p{L}\\p{N}]+"))
    .map(this::norm).filter(x->!x.isEmpty()).toArray(String[]::new);
   for(int start=0;start<words.length;start++){
    StringBuilder candidate=new StringBuilder();
    for(int end=start;end<words.length&&end<start+6;end++){
     candidate.append(words[end]);
     if(candidate.length()>30)break;
     if(candidate.length()<5)continue;
     byte[] digest=fingerprint(candidate.toString()).getBytes(StandardCharsets.US_ASCII);
     if(MessageDigest.isEqual(reference,digest))return true;
    }
   }
  }
  return false;
 }
 public String fingerprint(String number){
  String normalized=norm(number);
  if(normalized.length()<5||normalized.length()>30)throw bad("Führerscheinnummer unvollständig");
  try{
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
   return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
  }catch(Exception e){throw new IllegalStateException("Abgleich derzeit nicht verfügbar",e);}
 }
 public List<FireMemberQualification> own(Authentication auth){
  Long uid=permission.userId(auth);if(uid==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  AppUser user=users.findById(uid).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(!user.isEnabled()||!user.isRegistrationApproved()||user.getMember()==null)return List.of();
  return qualifications.findByMemberId(user.getMember().getId()).stream()
   .filter(q->q.active&&"DRIVERS_LICENSE".equals(q.type.code)).toList();
 }
 /** Only camera bytes. Browser-provided OCR strings are never accepted as a positive attestation. */
 public record ScanInput(String imageData){}
 public record ManualInput(Boolean confirmed){}
 public record CheckView(Long id,Long memberId,Long qualificationId,Instant checkedAt,
                         Long checkedByUserId,String method,String result){}
 private CheckView view(FireQualificationCheck check){
  return new CheckView(check.id,check.memberId,check.qualificationId,check.checkedAt,
   check.checkedByUserId,check.method,check.result);
 }
 private FireMemberQualification license(Long id){
  FireMemberQualification q=qualifications.findByIdForUpdate(id)
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Qualifikation nicht gefunden"));
  if(!q.active||!"DRIVERS_LICENSE".equals(q.type.code))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Keine aktive Führerscheinqualifikation");
  return q;
 }
 @Transactional
 public CheckView autoScan(Long id,ScanInput input,Authentication auth){
  Long userId=permission.userId(auth);if(userId==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  AppUser user=users.findById(userId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  FireMemberQualification q=license(id);
  if(!user.isEnabled()||!user.isRegistrationApproved()||user.getMember()==null
   ||!q.memberId.equals(user.getMember().getId()))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur eigene Führerscheinkontrolle erlaubt");
  if(q.licenseNumberMac==null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Führerscheinnummer muss zuvor durch die Wehrleitung als Referenz hinterlegt werden.");
  LocalDate today=LocalDate.now(ZoneId.of("Europe/Berlin"));
  LocalDate due=q.lastCheckedOn!=null&&q.type.intervalMonths>0
    ?q.lastCheckedOn.plusMonths(q.type.intervalMonths):q.nextDueOn;
  if(!q.type.tracked||due==null||today.isBefore(due.minusDays(q.type.warningDays)))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Prüfauftrag ist noch nicht fällig oder hat keinen Termin.");
  if(input==null||input.imageData()==null)throw bad("Ein Foto zur aktuellen Kontrolle ist erforderlich");
  checkOcrRateLimit(userId);
  // Read photo once, transiently, via local OCR stdin. No image is written to file, DB, cache or audit.
  String raw=ocr.read(input.imageData());
  boolean nameMatches=matchesName(raw,user.getMember().getName());
  boolean numberMatches=matchesNumber(raw,q.licenseNumberMac);
  if(!nameMatches||!numberMatches)
   throw new ResponseStatusException(HttpStatus.CONFLICT,
     "Foto wurde übertragen, aber der geschützte Abgleich von Name und Führerscheinnummer ist fehlgeschlagen. "+
     "Bitte Führerschein vollständig, scharf und ohne Spiegelung fotografieren. "+
     "Falls die Angaben gut lesbar sind, soll die Wehrleitung die hinterlegte Referenznummer prüfen und gegebenenfalls neu hinterlegen.");
  // Positive status refers to the DATA MATCH. OCR cannot independently verify document authenticity.
  return finish(q,userId,"AUTO_OCR_MATCH");
 }
 @Transactional
 public CheckView manual(Long id,ManualInput input,Authentication auth){
  if(input==null||!Boolean.TRUE.equals(input.confirmed()))
   throw bad("Führerscheinprüfung ausdrücklich bestätigen");
  Long checker=permission.userId(auth);if(checker==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  return finish(license(id),checker,"MANUAL");
 }
 private CheckView finish(FireMemberQualification q,Long checker,String method){
  LocalDate today=LocalDate.now(ZoneId.of("Europe/Berlin"));
  if(today.equals(q.lastCheckedOn))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Diese Qualifikation wurde heute bereits positiv gebucht");
  q.lastCheckedOn=today;
  q.nextDueOn=today.plusMonths(Math.max(1,q.type.intervalMonths));
  qualifications.save(q);
  return view(checks.save(new FireQualificationCheck(q.memberId,q.id,checker,method,today.toString())));
 }
 @Transactional(readOnly=true)
 public List<CheckView> history(){
  return checks.findAllByOrderByCheckedAtDesc().stream().map(this::view).toList();
 }
}
