package de.bierverein.api;

import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
import java.time.*;import java.util.*;

@RestController @RequestMapping("/api/device-planning")
@PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
public class DeviceInspectionPlanningController {
 private final DeviceRepository devices;private final DeviceLocationRepository locations;private final TrainingScheduleEventRepository events;private final DeviceInspectionTaskRepository tasks;private final TrainingSeriesExceptionRepository seriesExceptions;
 @org.springframework.beans.factory.annotation.Autowired private DeviceInspectionWorkflowService workflow;
 @org.springframework.beans.factory.annotation.Autowired private DeviceInspectionSessionReportRepository reports;
 @org.springframework.beans.factory.annotation.Autowired private DeviceInspectionRepository inspections;
 @org.springframework.beans.factory.annotation.Autowired private TrainingScheduleService schedule;
 public DeviceInspectionPlanningController(DeviceRepository d,DeviceLocationRepository l,TrainingScheduleEventRepository e,DeviceInspectionTaskRepository t,TrainingSeriesExceptionRepository exceptions){devices=d;locations=l;events=e;tasks=t;seriesExceptions=exceptions;}

 @GetMapping("/locations") public List<LocationDto> locations(){
  Map<String,DeviceLocation> saved=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);locations.findByActiveTrueOrderByNameAsc().forEach(x->saved.put(x.getName(),x));
  devices.findByActiveTrueOrderByNameAsc().stream().map(Device::getLocation).filter(Objects::nonNull).filter(x->!x.isBlank()).forEach(n->saved.computeIfAbsent(n,k->{DeviceLocation x=new DeviceLocation();x.setName(k);return x;}));
  return saved.values().stream().map(x->new LocationDto(x.getId(),x.getName(),x.isFireRelevant(),x.isActive())).toList();
 }
 @PostMapping("/locations") @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')") public LocationDto saveLocation(@RequestBody LocationRequest r){
  if(r==null||r.name()==null||r.name().isBlank())throw bad("Standortname ist erforderlich.");DeviceLocation x=r.id()==null?locations.findByNameIgnoreCase(r.name().trim()).orElseGet(DeviceLocation::new):locations.findById(r.id()).orElseThrow(()->notFound("Standort nicht gefunden."));
  x.setName(r.name().trim());x.setFireRelevant(Boolean.TRUE.equals(r.fireRelevant()));x.setActive(true);x=locations.save(x);return new LocationDto(x.getId(),x.getName(),x.isFireRelevant(),x.isActive());
 }
 @DeleteMapping("/locations/{id}") @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'delete')")
 public ResponseEntity<Void> deleteLocation(@PathVariable Long id){DeviceLocation x=locations.findById(id).orElseThrow(()->notFound("Standort nicht gefunden."));if(devices.findByActiveTrueOrderByNameAsc().stream().anyMatch(d->x.getName().equalsIgnoreCase(d.getLocation()==null?"":d.getLocation())))throw new ResponseStatusException(HttpStatus.CONFLICT,"Standort wird von Geräten verwendet. Bitte zuerst die Geräte umordnen.");x.setActive(false);locations.save(x);return ResponseEntity.noContent().build();}
 @GetMapping("/sessions") public List<SessionDto> sessions(){LocalDate from=LocalDate.now().minusMonths(2),to=LocalDate.now().plusYears(1);List<SessionDto> out=new ArrayList<>();for(TrainingScheduleEvent e:events.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(to,from)){if(!e.isDeviceInspection())continue;Set<LocalDate> excluded=new HashSet<>();seriesExceptions.findBySeriesEventId(e.getId()).forEach(x->excluded.add(x.getOccurrenceDate()));for(LocalDate d=e.getStartDate().isAfter(from)?e.getStartDate():from;!d.isAfter(e.getEndDate().isBefore(to)?e.getEndDate():to);d=d.plusDays(1))if(!excluded.contains(d)&&occursPattern(e,d)){List<DeviceInspectionTask> ts=tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(e.getId(),d).stream().filter(t->eligible(t.getDevice())).toList();out.add(new SessionDto(e.getId(),d,e.getTitle(),split(e.getDeviceLocations()),split(e.getDeviceCategories()),ts.size(),(int)ts.stream().filter(t->!"PENDING".equals(t.getStatus())).count(),reports.findByEventIdAndOccurrenceDate(e.getId(),d).map(DeviceInspectionSessionReport::getId).orElse(null)));}}return out.stream().sorted(Comparator.comparing(SessionDto::date)).toList();}
 @GetMapping("/sessions/{eventId}/{date}") @Transactional public SessionDetail session(@PathVariable Long eventId,@PathVariable LocalDate date){TrainingScheduleEvent e=event(eventId);if(!e.isDeviceInspection()||!occurs(e,date))throw bad("Ungültiger Geräteprüftermin.");ensureTasks(e,date);return new SessionDetail(new SessionDto(e.getId(),date,e.getTitle(),split(e.getDeviceLocations()),split(e.getDeviceCategories()),0,0,reports.findByEventIdAndOccurrenceDate(eventId,date).map(DeviceInspectionSessionReport::getId).orElse(null)),tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(eventId,date).stream().filter(t->t.getDevice().isInspectionRequired()).map(this::taskDto).toList());}
 @PutMapping("/tasks/{id}") @Transactional @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
 public TaskDto updateTask(@PathVariable Long id,@RequestBody TaskRequest r,Authentication auth){
  DeviceInspectionTask t=tasks.findById(id).orElseThrow(()->notFound("Prüfposition nicht gefunden."));
  if(reports.findByEventIdAndOccurrenceDate(t.getEventId(),t.getOccurrenceDate()).isPresent())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Das unterschriebene Prüfprotokoll kann nicht mehr geändert werden.");
  if(t.getInspectionId()!=null)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Gerät wurde bereits in einer Einzelprüfung abgeschlossen.");
  String status=r==null||r.status()==null?"":r.status().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("PENDING","INSPECTED","NOT_INSPECTABLE","DEFECTIVE").contains(status))throw bad("Ungültiger Prüfstatus.");
  String note=clean(r.note());
  if(note!=null&&note.length()>1000)throw bad("Bemerkung ist zu lang.");
  if(Set.of("NOT_INSPECTABLE","DEFECTIVE").contains(status)&&note==null)throw bad("Bitte Mangel oder Hindernis beschreiben.");
  t.setStatus(status);t.setNote(note);t.setUpdatedAt(Instant.now());t.setUpdatedBy(auth.getName());
  // Staged results are deliberately NOT inspection records until signed off.
  return taskDto(tasks.save(t));
 }
 @PostMapping("/sessions/{eventId}/{date}/complete") @Transactional
 @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
 public ReportView completeSession(@PathVariable Long eventId,@PathVariable LocalDate date,
  @RequestBody SignRequest input,Authentication auth){
  TrainingScheduleEvent e=event(eventId);
  if(!e.isDeviceInspection()||!occurs(e,date))throw bad("Ungültiger Geräteprüftermin.");
  if(date.isAfter(LocalDate.now(ZoneId.of("Europe/Berlin"))))throw bad("Zukünftige Termine dürfen nicht abgeschlossen werden.");
  DeviceInspectionWorkflowService.checkedSignature(input==null?null:input.signatureData());
  if(reports.findByEventIdAndOccurrenceDate(eventId,date).isPresent())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Der Prüftermin wurde bereits unterzeichnet.");
  ensureTasks(e,date);
  List<DeviceInspectionTask> lines=tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(eventId,date)
   .stream().filter(t->eligible(t.getDevice())).toList();
  if(lines.isEmpty())throw bad("Dieser Termin enthält keine prüfpflichtigen Geräte.");
  if(lines.stream().anyMatch(t->"PENDING".equals(t.getStatus())))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Bitte zuerst alle Geräte bearbeiten.");
  DeviceInspectionSessionReport report=reports.saveAndFlush(
   new DeviceInspectionSessionReport(eventId,date,e.getTitle(),auth.getName(),input.signatureData()));
  for(DeviceInspectionTask task:lines){
   DeviceInspection signed;
   if(task.getInspectionId()!=null){
    signed=inspections.findById(task.getInspectionId()).orElseThrow(()->notFound("Einzelprüfnachweis fehlt."));
    if(signed.getSessionReportId()!=null&&!signed.getSessionReportId().equals(report.getId()))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"Ein Gerät ist bereits einem anderen Protokoll zugeordnet.");
    signed.setSessionReportId(report.getId());
    inspections.save(signed);
   }else{
    String result=switch(task.getStatus()){
     case "INSPECTED" -> "BESTANDEN";
     case "DEFECTIVE" -> "MIT_MANGEL";
     case "NOT_INSPECTABLE" -> "NICHT_PRUEFBAR";
     default -> throw bad("Prüfposition ist noch offen.");
    };
    signed=workflow.record(task.getDevice(),result,task.getNote(),auth.getName(),input.signatureData(),
      "Sammelprüfung · "+e.getTitle(),null,task.getId(),report.getId());
    task.setInspectionId(signed.getId());
    tasks.save(task);
   }
  }
  return reportView(report);
 }
 @GetMapping("/reports")
 public List<ReportSummary> reports(){
  return reports.findAllByOrderByOccurrenceDateDesc().stream().map(r->
   new ReportSummary(r.getId(),r.getEventId(),r.getOccurrenceDate(),r.getTitle(),r.getInspector(),r.getSignedAt())).toList();
 }
 @GetMapping("/reports/{id}")
 public ReportView report(@PathVariable Long id){
  return reportView(reports.findById(id).orElseThrow(()->notFound("Prüfprotokoll nicht gefunden.")));
 }
 private ReportView reportView(DeviceInspectionSessionReport report){
  List<ReportItem> items=inspections.findBySessionReportIdOrderByIdAsc(report.getId()).stream()
   .map(i->new ReportItem(i.getId(),i.getDevice()==null?null:i.getDevice().getId(),
    i.getDeviceNameSnapshot()==null?i.getDevice().getName():i.getDeviceNameSnapshot(),
    i.getInventorySnapshot(),i.getLocationSnapshot(),i.getInspectionDate(),i.getResult(),i.getNotes(),
    i.getInspector(),i.getNextInspectionDate(),i.getSignedAt())).toList();
  return new ReportView(report.getId(),report.getEventId(),report.getOccurrenceDate(),report.getTitle(),
   report.getInspector(),report.getSignedAt(),report.getSignatureData(),items);
 }
 @DeleteMapping("/sessions/{eventId}/{date}") @Transactional
 @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'delete')")
 public ResponseEntity<Void> deleteSession(@PathVariable Long eventId,@PathVariable LocalDate date,Authentication auth){
  TrainingScheduleEvent e=event(eventId);
  if(!e.isDeviceInspection()||!occurs(e,date))throw bad("Prüftermin nicht gefunden.");
  schedule.deleteOccurrence(auth.getName(),eventId,date);
  return ResponseEntity.noContent().build();
 }
 @PostMapping("/devices/{id}/repaired") @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')") public void repaired(@PathVariable Long id){Device d=devices.findById(id).orElseThrow(()->notFound("Gerät nicht gefunden."));d.setOperationalStatus("OK");d.setOperationalStatusAt(Instant.now());d.setOperationalStatusNote("Als repariert gemeldet");devices.save(d);}
 @GetMapping("/alerts") @PreAuthorize("isAuthenticated()") public List<AlertDto> alerts(){Set<String> relevant=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);locations.findByActiveTrueOrderByNameAsc().stream().filter(DeviceLocation::isFireRelevant).map(DeviceLocation::getName).forEach(relevant::add);return devices.findByActiveTrueOrderByNameAsc().stream().filter(d->Set.of("DEFECTIVE","IN_REPAIR").contains(d.getOperationalStatus())&&(d.getCompartment()!=null&&d.getCompartment().getVehicle().isFireRelevant()||d.getLocation()!=null&&relevant.contains(d.getLocation()))).map(d->new AlertDto(d.getId(),d.getName(),displayLocation(d),d.getOperationalStatusNote(),d.getOperationalStatus())).toList();}
 @GetMapping("/summary") public Summary summary(){List<Device> all=devices.findByActiveTrueOrderByNameAsc();return new Summary((int)all.stream().filter(d->"DEFECTIVE".equals(d.getOperationalStatus())).count(),(int)all.stream().filter(d->"NOT_INSPECTABLE".equals(d.getOperationalStatus())).count());}
 private void ensureTasks(TrainingScheduleEvent e,LocalDate date){Set<String> ls=new HashSet<>(split(e.getDeviceLocations())),cs=new HashSet<>(split(e.getDeviceCategories()));Set<Long> existing=new HashSet<>();tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(e.getId(),date).forEach(t->existing.add(t.getDevice().getId()));for(Device d:devices.findByActiveTrueOrderByNameAsc())if(eligible(d)&&ls.contains(deviceLocation(d))&&cs.contains(d.getCategory())&&!existing.contains(d.getId())&&
 (d.getLastInspectionDate()==null||d.getNextInspectionDate()==null||!d.getNextInspectionDate().isAfter(date))){DeviceInspectionTask t=new DeviceInspectionTask();t.setEventId(e.getId());t.setOccurrenceDate(date);t.setDevice(d);tasks.save(t);}}
 private boolean eligible(Device d){return d.isActive()&&(d.isInspectionRequired()||d.getInspectionIntervalMonths()!=null&&d.getInspectionIntervalMonths()>0);}
 private String deviceLocation(Device d){return d.getCompartment()!=null?d.getCompartment().getVehicle().getName():d.getLocation();}
 private String displayLocation(Device d){return d.getCompartment()!=null?d.getCompartment().getVehicle().getName()+" / "+d.getCompartment().getName():d.getLocation();}
 private boolean occurs(TrainingScheduleEvent e,LocalDate d){return !seriesExceptions.existsBySeriesEventIdAndOccurrenceDate(e.getId(),d)&&occursPattern(e,d);}
 private boolean occursPattern(TrainingScheduleEvent e,LocalDate d){return !d.isBefore(e.getStartDate())&&!d.isAfter(e.getEndDate())&&(!e.isRecurring()?d.equals(e.getStartDate()):split(e.getWeekdays()).contains(d.getDayOfWeek().name())&&(!e.isLastWeekdayOfMonth()||d.plusWeeks(1).getMonth()!=d.getMonth()));}
 private TrainingScheduleEvent event(Long id){return events.findById(id).orElseThrow(()->notFound("Termin nicht gefunden."));}private List<String> split(String s){return s==null||s.isBlank()?List.of():Arrays.stream(s.split(",")).map(String::trim).filter(x->!x.isBlank()).toList();}
 private TaskDto taskDto(DeviceInspectionTask t){Device d=t.getDevice();return new TaskDto(t.getId(),d.getId(),d.getName(),d.getInventoryNumber(),displayLocation(d),d.getCategory(),t.getStatus(),t.getNote(),t.getUpdatedBy(),t.getUpdatedAt());}private String clean(String x){return x==null||x.isBlank()?null:x.trim();}
 private ResponseStatusException bad(String x){return new ResponseStatusException(HttpStatus.BAD_REQUEST,x);}private ResponseStatusException notFound(String x){return new ResponseStatusException(HttpStatus.NOT_FOUND,x);}
 public record LocationRequest(Long id,String name,Boolean fireRelevant){} public record LocationDto(Long id,String name,boolean fireRelevant,boolean active){}
 public record SessionDto(Long eventId,LocalDate date,String title,List<String> locations,List<String> categories,int total,int completed,Long reportId){} public record SessionDetail(SessionDto session,List<TaskDto> tasks){}
 public record SignRequest(String signatureData){}
 public record ReportSummary(Long id,Long eventId,LocalDate date,String title,String inspector,Instant signedAt){}
 public record ReportItem(Long inspectionId,Long deviceId,String deviceName,String inventoryNumber,String location,
  LocalDate inspectionDate,String result,String note,String inspector,LocalDate nextInspectionDate,Instant signedAt){}
 public record ReportView(Long id,Long eventId,LocalDate date,String title,String inspector,Instant signedAt,
  String signatureData,List<ReportItem> items){}
 public record TaskRequest(String status,String note){} public record TaskDto(Long id,Long deviceId,String deviceName,String inventoryNumber,String location,String category,String status,String note,String updatedBy,Instant updatedAt){}
 public record AlertDto(Long deviceId,String deviceName,String location,String note,String status){} public record Summary(int defective,int notInspectable){}
}
