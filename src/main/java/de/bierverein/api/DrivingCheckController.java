package de.bierverein.api;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/fire/qualifications/driving")
public class DrivingCheckController {
 private final DrivingCheckService driving;
 private final FireDrivingReminderService reminders;
 public DrivingCheckController(DrivingCheckService driving,FireDrivingReminderService reminders){this.driving=driving;this.reminders=reminders;}
 @PostMapping("/my/{id}/scan")
 public DrivingCheckService.CheckView scan(@PathVariable Long id,@RequestBody DrivingCheckService.ScanInput request,Authentication auth){
  return driving.autoScan(id,request,auth);
 }
 @PostMapping("/{id}/manual")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.write')")
 public DrivingCheckService.CheckView manual(@PathVariable Long id,@RequestBody DrivingCheckService.ManualInput request,Authentication auth){
  return driving.manual(id,request,auth);
 }
 @PostMapping("/{id}/notify")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.write')")
 public NotifyResult notifyOne(@PathVariable Long id){
  boolean sent=reminders.notifyOne(id);
  return new NotifyResult(sent,sent?"Persönliche Erinnerung zugestellt.":"Keine Erinnerung zugestellt: noch nicht fällig, bereits erinnert oder keine Push-Anmeldung vorhanden.");
 }
 /** Return safe, actionable scan errors to the phone; never echo photo, OCR text, or reference number. */
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 public org.springframework.http.ResponseEntity<java.util.Map<String,String>> scanError(
      org.springframework.web.server.ResponseStatusException error){
  return org.springframework.http.ResponseEntity.status(error.getStatusCode())
      .cacheControl(org.springframework.http.CacheControl.noStore())
      .body(java.util.Map.of("detail",error.getReason()==null?
          "Kontrolle konnte nicht abgeschlossen werden. Bitte erneut versuchen oder Wehrleitung kontaktieren.":error.getReason()));
 }
 public record NotifyResult(boolean delivered,String message){}
 @GetMapping("/checks")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.read')")
 @Transactional(readOnly=true)
 public List<DrivingCheckService.CheckView> history(){return driving.history();}
}
