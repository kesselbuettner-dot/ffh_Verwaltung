package de.bierverein.api;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Generates one inspection task per device/cycle, initially in Gerätewart's shared inbox.
 * A delegated task is additionally visible to exactly the selected member.
 */
@Service
public class DeviceCycleTaskService {
 private final DeviceRepository devices;
 private final DeviceCycleTaskRepository tasks;
 private final AppUserRepository users;
 private final ManagedUserRoleRepository assignments;
 private final DeviceInspectionRepository inspections;
 private final ZoneId zone=ZoneId.of("Europe/Berlin");
 static final int WARNING_DAYS=30;
 public DeviceCycleTaskService(DeviceRepository devices,DeviceCycleTaskRepository tasks,
  AppUserRepository users,ManagedUserRoleRepository assignments,DeviceInspectionRepository inspections){
  this.devices=devices;this.tasks=tasks;this.users=users;this.assignments=assignments;this.inspections=inspections;
 }
 private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
 public AppUser actor(Authentication authentication){
  if(!(authentication instanceof JwtAuthenticationToken jwt))throw error(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich");
  Object claim=jwt.getToken().getClaim("userId");
  if(!(claim instanceof Number n))throw error(HttpStatus.UNAUTHORIZED,"Benutzerkennung fehlt");
  AppUser user=users.findById(n.longValue()).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Benutzer unbekannt"));
  if(!user.isEnabled()||!user.isRegistrationApproved())throw error(HttpStatus.FORBIDDEN,"Konto nicht freigeschaltet");
  return user;
 }
 public boolean manager(AppUser user){
  if(user.getRole()==Role.ADMIN||user.getRole()==Role.GERATEWART)return true;
  return assignments.findByUserId(user.getId()).stream()
   .anyMatch(a->Set.of("ADMIN","GERATEWART").contains(a.getRole().getCode()));
 }
 private void requireManager(AppUser user){
  if(!manager(user))throw error(HttpStatus.FORBIDDEN,"Nur die Gerätewart-Rolle darf Prüfaufgaben weitergeben");
 }
 public record TaskView(Long id,Long deviceId,String deviceName,String inventoryNumber,String location,
    LocalDate dueOn,Integer intervalMonths,String status,Long assignedUserId,String assignedName,
    String result,String note,Instant completedAt,String completedBy){}
 public record PersonView(Long id,String name){}
 public record AssignInput(Long userId){}
 public record FinishInput(String result,String note){}
 private String assignedName(Long id){
  if(id==null)return "Gerätewart (Rollenpostfach)";
  return users.findById(id).map(u->u.getMember()!=null?u.getMember().getName():u.getUsername()).orElse("Benutzer nicht mehr vorhanden");
 }
 private TaskView view(DeviceCycleTask task){
  Device d=task.getDevice();
  return new TaskView(task.getId(),d.getId(),d.getName(),d.getInventoryNumber(),d.getLocation(),
   task.getDueOn(),d.getInspectionIntervalMonths(),task.getStatus(),task.getAssignedUserId(),
   assignedName(task.getAssignedUserId()),task.getResult(),task.getNote(),task.getCompletedAt(),task.getCompletedBy());
 }
 private LocalDate due(Device device,LocalDate today){
  Integer interval=device.getInspectionIntervalMonths();
  if(interval==null||interval<1)return null;
  if(device.getNextInspectionDate()!=null)return device.getNextInspectionDate();
  if(device.getLastInspectionDate()!=null)return device.getLastInspectionDate().plusMonths(interval);
  return today; // no recorded first inspection: task belongs in the Gerätewart inbox today.
 }
 @Transactional
 public int generateDue(){
  int added=0;
  LocalDate today=LocalDate.now(zone);
  for(Device device:devices.findByActiveTrueOrderByNameAsc()){
   Integer interval=device.getInspectionIntervalMonths();
   if(interval==null||interval<1||!device.isInspectionRequired())continue;
   LocalDate date=due(device,today);
   if(date==null||date.isAfter(today.plusDays(WARNING_DAYS)))continue;
   if(device.getLastInspectionDate()==null&&device.getNextInspectionDate()==null){
    // Without a previous date the first task must keep a stable due date across days.
    device.setNextInspectionDate(date);
    devices.save(device);
   }
   added+=tasks.insertIfAbsent(device.getId(),date);
  }
  return added;
 }
 @Transactional
 public List<TaskView> list(Authentication auth,boolean includeDone){
  AppUser user=actor(auth);
  if(manager(user))generateDue(); // also works when the worker has not yet scheduled the morning run.
  List<String> states=includeDone?List.of("OPEN","DONE"):List.of("OPEN");
  List<DeviceCycleTask> source=manager(user)?
   tasks.findByStatusInOrderByDueOnAsc(states):tasks.findByAssignedUserIdAndStatusInOrderByDueOnAsc(user.getId(),states);
  return source.stream().filter(t->t.getDevice().isActive()).map(this::view).toList();
 }
 @Transactional(readOnly=true)
 public List<PersonView> members(Authentication auth){
  requireManager(actor(auth));
  return users.findAll().stream().filter(u->u.isEnabled()&&u.isRegistrationApproved()
    &&u.getMember()!=null&&u.getMember().isActive())
   .map(u->new PersonView(u.getId(),u.getMember().getName()))
   .sorted(Comparator.comparing(PersonView::name,String.CASE_INSENSITIVE_ORDER)).toList();
 }
 @Transactional
 public TaskView assign(Long id,AssignInput input,Authentication auth){
  AppUser manager=actor(auth);requireManager(manager);
  DeviceCycleTask task=tasks.findLocked(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Prüfaufgabe nicht gefunden"));
  if(!task.getDevice().isActive()||!"OPEN".equals(task.getStatus()))
   throw error(HttpStatus.CONFLICT,"Abgeschlossene oder inaktive Prüfung kann nicht weitergegeben werden");
  if(input==null)throw error(HttpStatus.BAD_REQUEST,"Mitglied fehlt");
  if(input.userId()==null){
   task.assign(null,manager.getUsername()); // return to Gerätewart shared inbox
  }else{
   AppUser candidate=users.findById(input.userId()).orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Benutzer unbekannt"));
   if(!candidate.isEnabled()||!candidate.isRegistrationApproved()||candidate.getMember()==null||!candidate.getMember().isActive())
    throw error(HttpStatus.BAD_REQUEST,"Bitte ein aktives Mitglied mit freigegebenem Zugang wählen");
   task.assign(candidate.getId(),manager.getUsername());
  }
  return view(tasks.save(task));
 }
 @Transactional
 public TaskView finish(Long id,FinishInput input,Authentication auth){
  AppUser user=actor(auth);
  DeviceCycleTask task=tasks.findLocked(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Prüfaufgabe nicht gefunden"));
  if(!manager(user)&&!Objects.equals(task.getAssignedUserId(),user.getId()))
   throw error(HttpStatus.FORBIDDEN,"Diese Prüfaufgabe ist nicht dir zugewiesen");
  if(!"OPEN".equals(task.getStatus())||!task.getDevice().isActive())
   throw error(HttpStatus.CONFLICT,"Prüfaufgabe ist bereits abgeschlossen oder Gerät inaktiv");
  if(input==null||!Set.of("BESTANDEN","MIT_MANGEL","NICHT_BESTANDEN").contains(input.result()))
   throw error(HttpStatus.BAD_REQUEST,"Bitte gültiges Prüfergebnis wählen");
  String note=input.note()==null?"":input.note().trim();
  if(note.length()>1000)throw error(HttpStatus.BAD_REQUEST,"Bemerkung zu lang");
  if(!"BESTANDEN".equals(input.result())&&note.isBlank())
   throw error(HttpStatus.BAD_REQUEST,"Bei einem Mangel ist eine Bemerkung erforderlich");
  LocalDate date=LocalDate.now(zone);
  Device device=task.getDevice();
  if("BESTANDEN".equals(input.result())&&(device.getInspectionIntervalMonths()==null||device.getInspectionIntervalMonths()<1))
   throw error(HttpStatus.CONFLICT,"Prüfzyklus fehlt. Gerätewart muss zuerst ein gültiges Prüfintervall hinterlegen.");
  DeviceInspection inspection=new DeviceInspection();
  inspection.setDevice(device);
  inspection.setInspectionDate(date);
  inspection.setInspectionType("Zyklische Geräteprüfung · Aufgabe #"+task.getId());
  inspection.setResult(input.result());
  inspection.setInspector(user.getUsername());
  inspection.setNotes(note.isBlank()?null:note);
  if(!"BESTANDEN".equals(input.result()))inspection.setDefects(note);
  if("BESTANDEN".equals(input.result())){
   LocalDate next=date.plusMonths(device.getInspectionIntervalMonths());
   inspection.setNextInspectionDate(next);
   device.setNextInspectionDate(next);
   device.setOperationalStatus("OK");
  }else{
   inspection.setNextInspectionDate(task.getDueOn());
   device.setOperationalStatus("DEFECTIVE");
  }
  device.setLastInspectionDate(date);
  device.setOperationalStatusAt(Instant.now());
  device.setOperationalStatusNote(note.isBlank()?null:note);
  inspections.save(inspection);
  devices.save(device);
  task.complete(input.result(),note.isBlank()?null:note,user.getUsername());
  return view(tasks.save(task));
 }
}
