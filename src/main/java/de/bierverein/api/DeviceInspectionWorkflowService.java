package de.bierverein.api;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Shared write path for standalone inspections, delegated cycle tasks and group appointments. */
@Service
public class DeviceInspectionWorkflowService {
 private final DeviceRepository devices;
 private final DeviceInspectionRepository inspections;
 private final DeviceCycleTaskRepository cycles;
 private final DeviceInspectionTaskRepository sessionTasks;
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 public DeviceInspectionWorkflowService(DeviceRepository devices,DeviceInspectionRepository inspections,
  DeviceCycleTaskRepository cycles,DeviceInspectionTaskRepository sessionTasks){
  this.devices=devices;this.inspections=inspections;this.cycles=cycles;this.sessionTasks=sessionTasks;
 }
 public static String checkedSignature(String data){
  if(data==null||!data.startsWith("data:image/png;base64,")||data.length()>100000)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte mit dem Finger oder Stift unterschreiben.");
  try{
   byte[] bytes=Base64.getDecoder().decode(data.substring("data:image/png;base64,".length()));
   byte[] png={(byte)137,80,78,71,13,10,26,10};
   if(bytes.length<100||bytes.length>75000)throw new IllegalArgumentException("length");
   for(int i=0;i<png.length;i++)if(bytes[i]!=png[i])throw new IllegalArgumentException("png");
  }catch(IllegalArgumentException ex){
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültige PNG-Unterschrift.",ex);
  }
  return data;
 }
 @Transactional
 public DeviceInspection record(Device device,String result,String note,String inspector,String signature,
   String type,Long cycleTaskId,Long sessionTaskId,Long reportId){
  if(device==null||!device.isActive())throw new ResponseStatusException(HttpStatus.CONFLICT,"Gerät ist archiviert.");
  if(!Set.of("BESTANDEN","MIT_MANGEL","NICHT_BESTANDEN","NICHT_PRUEFBAR").contains(result))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiges Prüfergebnis.");
  String comment=note==null?"":note.trim();
  if(comment.length()>1000||(!"BESTANDEN".equals(result)&&comment.isBlank()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bei einem Mangel ist eine kurze Bemerkung erforderlich.");
  if("BESTANDEN".equals(result)&&(device.getInspectionIntervalMonths()==null||device.getInspectionIntervalMonths()<1))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Vor Abschluss ein gültiges Prüfintervall hinterlegen.");
  checkedSignature(signature);
  LocalDate today=LocalDate.now(ZONE);
  DeviceInspection inspection=new DeviceInspection();
  inspection.setDevice(device);
  inspection.setInspectionDate(today);
  inspection.setInspectionType(type);
  inspection.setResult(result);
  inspection.setInspector(inspector);
  inspection.setNotes(comment.isEmpty()?null:comment);
  inspection.setDefects("BESTANDEN".equals(result)?null:comment);
  inspection.setSignatureData(signature);
  inspection.setSignedAt(Instant.now());
  inspection.setCycleTaskId(cycleTaskId);
  inspection.setSessionTaskId(sessionTaskId);
  inspection.setSessionReportId(reportId);
  inspection.setDeviceNameSnapshot(device.getName());
  inspection.setInventorySnapshot(device.getInventoryNumber());
  inspection.setLocationSnapshot(device.getCompartment()==null?device.getLocation():
    device.getCompartment().getVehicle().getName()+" / "+device.getCompartment().getName());
  LocalDate originalDue=device.getNextInspectionDate();
  if("BESTANDEN".equals(result)){
   LocalDate next=today.plusMonths(device.getInspectionIntervalMonths());
   inspection.setNextInspectionDate(next);
   device.setNextInspectionDate(next);
   device.setOperationalStatus("OK");
  }else{
   inspection.setNextInspectionDate(originalDue);
   device.setOperationalStatus("NICHT_PRUEFBAR".equals(result)?"NOT_INSPECTABLE":"DEFECTIVE");
  }
  device.setLastInspectionDate(today);
  device.setOperationalStatusAt(Instant.now());
  device.setOperationalStatusNote(comment.isBlank()?null:comment);
  DeviceInspection saved=inspections.save(inspection);
  devices.save(device);
  // A signature completes this *cycle* in both views. Do not close a future cycle.
  for(DeviceCycleTask cycle:cycles.findByDeviceIdAndStatusOrderByDueOnAsc(device.getId(),"OPEN")){
   if(cycleTaskId!=null&&cycleTaskId.equals(cycle.getId())||
      cycle.getDueOn().isAfter(today.minusYears(2))&&!cycle.getDueOn().isAfter(today.plusDays(30))&&
      (originalDue==null||!cycle.getDueOn().isAfter(originalDue))){
    cycle.complete(result,comment.isBlank()?null:comment,inspector);
    cycle.setInspectionId(saved.getId());
    cycles.save(cycle);
   }
  }
  // An individual proof can satisfy a matching appointment without a second inspection.
  LocalDate upper="BESTANDEN".equals(result)?device.getNextInspectionDate():today.plusDays(30);
  for(DeviceInspectionTask pending:sessionTasks.findByDeviceIdAndStatus(device.getId(),"PENDING")){
   if(!pending.getOccurrenceDate().isBefore(today)&&pending.getOccurrenceDate().isBefore(upper)){
    pending.setInspectionId(saved.getId());
    pending.setStatus("BESTANDEN".equals(result)?"INSPECTED":
      "NICHT_PRUEFBAR".equals(result)?"NOT_INSPECTABLE":"DEFECTIVE");
    pending.setNote(comment.isBlank()?null:comment);
    pending.setUpdatedBy(inspector);
    pending.setUpdatedAt(Instant.now());
    sessionTasks.save(pending);
   }
  }
  return saved;
 }
}
