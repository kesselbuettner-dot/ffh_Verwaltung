package de.bierverein.api;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/fire/qualifications/driving")
public class DrivingCheckController {
 private final DrivingCheckService driving;
 public DrivingCheckController(DrivingCheckService driving){this.driving=driving;}
 @PostMapping("/my/{id}/scan")
 public DrivingCheckService.CheckView scan(@PathVariable Long id,@RequestBody DrivingCheckService.ScanInput request,Authentication auth){
  return driving.autoScan(id,request,auth);
 }
 @PostMapping("/{id}/manual")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.write')")
 public DrivingCheckService.CheckView manual(@PathVariable Long id,@RequestBody DrivingCheckService.ManualInput request,Authentication auth){
  return driving.manual(id,request,auth);
 }
 @GetMapping("/checks")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.read')")
 @Transactional(readOnly=true)
 public List<DrivingCheckService.CheckView> history(){return driving.history();}
}
