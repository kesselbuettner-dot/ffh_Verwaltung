package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api/inspection-jobs")
public class InspectionJobController {
 private final InspectionJobRepository jobs;
 private final InspectionJobItemRepository items;
 private final AppUserRepository users;
 private final DeviceRepository devices;
 private final DeviceInspectionRepository inspections;
 private final DevicePermissionGuard permission;
 public InspectionJobController(InspectionJobRepository jobs,InspectionJobItemRepository items,AppUserRepository users,DeviceRepository devices,DeviceInspectionRepository inspections,DevicePermissionGuard permission){
  this.jobs=jobs;this.items=items;this.users=users;this.devices=devices;this.inspections=inspections;this.permission=permission;
 }
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private Long userId(Authentication auth){Object claim=((JwtAuthenticationToken)auth).getToken().getClaim("userId");if(!(claim instanceof Number n))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return n.longValue();}
 private boolean manager(Authentication auth){return permission.allowed(auth,"write");}
 private InspectionJob visible(Long id,Authentication auth){InspectionJob job=jobs.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!manager(auth)&&!job.assigneeId.equals(userId(auth)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return job;}
 public record Create(String title,Long assigneeId,List<Long> deviceIds){}
 public record Result(String result,String notes){}
 public record Signature(String data){}
 public record JobDto(Long id,String title,String assigneeName,String assignedBy,OffsetDateTime createdAt,OffsetDateTime completedAt,String signer,String signature,List<ItemDto> items){}
 public record ItemDto(Long id,Long deviceId,String deviceName,String inventoryNumber,String location,String result,String notes,OffsetDateTime checkedAt,String checkedBy){}
 private JobDto dto(InspectionJob j){return new JobDto(j.id,j.title,j.assigneeName,j.assignedBy,j.createdAt,j.completedAt,j.signer,j.signature,items.findByJobIdOrderByIdAsc(j.id).stream().map(i->new ItemDto(i.id,i.device.getId(),i.deviceName,i.inventoryNumber,i.location,i.result,i.notes,i.checkedAt,i.checkedBy)).toList());}
 @Transactional(readOnly=true) @GetMapping("/assignees")
 public List<Map<String,Object>> assignees(Authentication auth){if(!manager(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return users.findAll().stream().filter(u->u.isEnabled()&&u.isRegistrationApproved()&&u.getMember()!=null&&u.getMember().isActive()).map(u->Map.<String,Object>of("id",u.getId(),"name",u.getMember().getName(),"username",u.getUsername())).toList();}
 @PostMapping @Transactional
 public JobDto create(@RequestBody Create request,Authentication auth){
  if(!manager(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(request==null||request.title()==null||request.title().isBlank()||request.assigneeId()==null||request.deviceIds()==null||request.deviceIds().isEmpty())throw bad("Titel, Mitglied und Geräte sind erforderlich");
  AppUser user=users.findById(request.assigneeId()).orElseThrow(()->bad("Mitglied nicht gefunden"));
  if(!user.isEnabled()||!user.isRegistrationApproved()||user.getMember()==null||!user.getMember().isActive())throw bad("Mitglied benötigt einen freigeschalteten Zugang");
  InspectionJob job=new InspectionJob();job.title=request.title().trim();job.assigneeId=user.getId();job.assigneeName=user.getMember().getName();job.assignedBy=auth.getName();jobs.save(job);
  Set<Long> unique=new LinkedHashSet<>(request.deviceIds());if(unique.size()>300)throw bad("Maximal 300 Geräte je Prüfung");
  for(Long id:unique){Device d=devices.findById(id).orElseThrow(()->bad("Gerät nicht gefunden"));if(!d.isActive())throw bad("Inaktives Gerät ausgewählt");InspectionJobItem item=new InspectionJobItem();item.job=job;item.device=d;item.deviceName=d.getName();item.inventoryNumber=d.getInventoryNumber();item.location=d.getLocation();items.save(item);}
  return dto(job);
 }
 @Transactional(readOnly=true) @GetMapping
 public List<JobDto> list(Authentication auth){return (manager(auth)?jobs.findAllByOrderByCreatedAtDesc():jobs.findByAssigneeIdOrderByCreatedAtDesc(userId(auth))).stream().map(this::dto).toList();}
 @Transactional(readOnly=true) @GetMapping("/mine") public List<JobDto> mine(Authentication auth){return jobs.findByAssigneeIdOrderByCreatedAtDesc(userId(auth)).stream().map(this::dto).toList();}
 @Transactional(readOnly=true) @GetMapping("/{id}") public JobDto detail(@PathVariable Long id,Authentication auth){return dto(visible(id,auth));}
 @PutMapping("/{id}/items/{itemId}") @Transactional
 public JobDto result(@PathVariable Long id,@PathVariable Long itemId,@RequestBody Result request,Authentication auth){
  InspectionJob job=visible(id,auth);if(!job.assigneeId.equals(userId(auth)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(job.completedAt!=null)throw bad("Prüfung bereits abgeschlossen");
  if(request==null||!Set.of("BESTANDEN","NICHT_BESTANDEN").contains(request.result()))throw bad("Ungültiges Ergebnis");
  InspectionJobItem item=items.findByIdAndJobId(itemId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  item.result=request.result();item.notes=request.notes()==null?null:request.notes().trim();if(item.notes!=null&&item.notes.length()>2000)throw bad("Bemerkung zu lang");item.checkedAt=OffsetDateTime.now();item.checkedBy=auth.getName();items.save(item);return dto(job);
 }
 @PostMapping("/{id}/complete") @Transactional
 public JobDto complete(@PathVariable Long id,@RequestBody Signature request,Authentication auth){
  InspectionJob job=visible(id,auth);if(!job.assigneeId.equals(userId(auth)))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(job.completedAt!=null)throw bad("Bereits abgeschlossen");
  List<InspectionJobItem> rows=items.findByJobIdOrderByIdAsc(id);
  if(rows.isEmpty()||rows.stream().anyMatch(i->i.result==null))throw bad("Zuerst alle Geräte prüfen");
  if(request==null||request.data()==null||!request.data().matches("data:image/png;base64,[A-Za-z0-9+/=]+")||request.data().length()>200000)throw bad("Unterschrift fehlt oder ist ungültig");
  job.signature=request.data();job.signer=auth.getName();job.completedAt=OffsetDateTime.now();jobs.save(job);
  for(InspectionJobItem row:rows){DeviceInspection inspection=new DeviceInspection();inspection.setDevice(row.device);inspection.setInspectionDate(LocalDate.now());inspection.setInspectionType(job.title);inspection.setResult(row.result);inspection.setInspector(row.checkedBy);inspection.setNotes(row.notes);inspections.save(inspection);row.device.setLastInspectionDate(LocalDate.now());devices.save(row.device);}
  return dto(job);
 }
}
