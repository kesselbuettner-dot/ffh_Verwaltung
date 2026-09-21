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
 public DrivingCheckService(FireMemberQualificationRepository qualifications,FireQualificationCheckRepository checks,
    AppUserRepository users,FireQualificationPermissions permission,
    @Value("${app.jwt.secret}") String pepper,TransientLicenseOcrService ocr) {
  this.qualifications=qualifications;this.checks=checks;this.users=users;this.permission=permission;this.pepper=pepper;this.ocr=ocr;
 }
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private String norm(String value){
  return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
   .replaceAll("\\p{M}","").toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
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
  // Read photo once, transiently, via local OCR stdin. No image is written to file, DB, cache or audit.
  String raw=ocr.read(input.imageData());
  String content=norm(raw);
  String expected=norm(user.getMember().getName());
  String[] nameParts=user.getMember().getName().split("[\\s,]+");
  boolean matchesName=expected.length()>=4&&
    (content.contains(expected)||Arrays.stream(nameParts).filter(p->norm(p).length()>=2).allMatch(p->content.contains(norm(p))));
  boolean matchesNumber=false;
  // Compare only exact OCR candidates to the pre-approved HMAC reference; never disclose the reference.
  for(String line:raw.split("[\\r\\n]+")){
   // An OCR engine may insert spaces in a document number. Try bounded adjacent fragments.
   String[] words=line.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
   List<String> candidates=new ArrayList<>(Arrays.asList(words));
   for(int i=0;i<words.length;i++){
    for(int j=i+1;j<Math.min(words.length,i+3);j++)candidates.add(String.join("",Arrays.copyOfRange(words,i,j+1)));
   }
   for(String candidate:candidates){
    if(candidate.length()<5||candidate.length()>30)continue;
    String digest=fingerprint(candidate);
    if(MessageDigest.isEqual(q.licenseNumberMac.getBytes(StandardCharsets.US_ASCII),
        digest.getBytes(StandardCharsets.US_ASCII))){matchesNumber=true;break;}
   }
   if(matchesNumber)break;
  }
  if(!matchesName||!matchesNumber)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Name oder Führerscheinnummer im Foto nicht eindeutig erkannt. Bitte ein besseres Foto aufnehmen oder Wehrleitung kontaktieren.");
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
  LocalDate today=LocalDate.now();
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
