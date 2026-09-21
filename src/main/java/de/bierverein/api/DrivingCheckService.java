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
 public DrivingCheckService(FireMemberQualificationRepository qualifications,FireQualificationCheckRepository checks,
    AppUserRepository users,FireQualificationPermissions permission,
    @Value("${app.jwt.secret}") String pepper) {
  this.qualifications=qualifications;this.checks=checks;this.users=users;this.permission=permission;this.pepper=pepper;
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
 public record ScanInput(String recognizedName,String recognizedNumber){}
 public record ManualInput(Boolean confirmed){}
 public record CheckView(Long id,Long memberId,Long qualificationId,Instant checkedAt,
                         Long checkedByUserId,String method,String result){}
 private CheckView view(FireQualificationCheck check){
  return new CheckView(check.id,check.memberId,check.qualificationId,check.checkedAt,
   check.checkedByUserId,check.method,check.result);
 }
 private FireMemberQualification license(Long id){
  FireMemberQualification q=qualifications.findById(id)
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
  if(input==null||input.recognizedName()==null||input.recognizedNumber()==null)
   throw bad("Erkennung von Name und Führerscheinnummer ist erforderlich");
  String name=norm(input.recognizedName()),expected=norm(user.getMember().getName());
  if(expected.length()<4||!expected.equals(name)||
   !MessageDigest.isEqual(q.licenseNumberMac.getBytes(StandardCharsets.US_ASCII),
                         fingerprint(input.recognizedNumber()).getBytes(StandardCharsets.US_ASCII)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Abgleich fehlgeschlagen: Wehrleitung zur manuellen Kontrolle kontaktieren");
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
