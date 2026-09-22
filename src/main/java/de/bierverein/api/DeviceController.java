package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceRepository devices;
    private final DeviceInspectionRepository inspections;
    private final VehicleCompartmentRepository compartments;
    @org.springframework.beans.factory.annotation.Autowired private DeviceInspectionWorkflowService workflow;

    public DeviceController(DeviceRepository devices, DeviceInspectionRepository inspections,VehicleCompartmentRepository compartments){
        this.devices=devices; this.inspections=inspections;this.compartments=compartments;
    }

    @GetMapping
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public List<DeviceDto> list(@RequestParam(defaultValue="") String q){
        String x=q.trim().toLowerCase();
        return devices.findByActiveTrueOrderByNameAsc().stream()
            .filter(d -> x.isBlank()
                || contains(d.getName(),x) || contains(d.getInventoryNumber(),x)
                || contains(d.getBarcode(),x) || contains(d.getSerialNumber(),x)
                || contains(d.getCategory(),x))
            .map(this::dto).toList();
    }

    @GetMapping("/scan")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public DeviceDto scan(@RequestParam String value){
        String v=value==null?"":value.trim();
        if(v.isBlank()) throw bad("Scanwert fehlt");
        Device d=devices.findFirstByBarcodeAndActiveTrue(v).orElseGet(
            () -> devices.findFirstBySerialNumberAndActiveTrue(v).orElse(null));
        if(d==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Kein aktives Gerät mit Barcode oder Seriennummer gefunden");
        return dto(d);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public DeviceDetailDto detail(@PathVariable Long id){
        Device d=find(id);
        List<InspectionDto> history=inspections.findByDeviceIdOrderByInspectionDateDesc(id).stream()
            .map(i->inspection(i)).toList();
        return new DeviceDetailDto(dto(d),history);
    }

    @PostMapping
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public DeviceDto create(@RequestBody DeviceRequest r){
        Device d=new Device();
        apply(d,r,true);
        return dto(devices.save(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public DeviceDto update(@PathVariable Long id,@RequestBody DeviceRequest r){
        Device d=find(id);
        apply(d,r,false);
        return dto(devices.save(d));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id){
        Device d=find(id);
        d.setActive(false); // Retain inspection and defect history.
        devices.save(d);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/inspection-required")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public DeviceDto inspectionRequired(@PathVariable Long id,@RequestBody InspectionRequiredRequest r){
        Device d=find(id);
        d.setInspectionRequired(r!=null&&Boolean.TRUE.equals(r.required()));
        return dto(devices.save(d));
    }

    @PatchMapping("/{id}/condition")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public DeviceDto condition(@PathVariable Long id,@RequestBody ConditionRequest r){
        Device d=find(id);String status=r==null||r.status()==null?"":r.status().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("OK","DEFECTIVE","IN_REPAIR","NOT_INSPECTABLE").contains(status))throw bad("Ungültiger Gerätestatus");
        d.setOperationalStatus(status);d.setOperationalStatusAt(Instant.now());d.setOperationalStatusNote(clean(r.note()));return dto(devices.save(d));
    }

    @PostMapping("/{id}/inspections")
    @Transactional
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public InspectionDto inspect(@PathVariable Long id,@RequestBody InspectionRequest r,
        org.springframework.security.core.Authentication auth){
        Device d=find(id);
        validateInspection(r);
        if(!r.inspectionDate().equals(LocalDate.now(java.time.ZoneId.of("Europe/Berlin"))))
            throw bad("Eine neue Geräteprüfung kann nur mit dem heutigen Prüfdatum abgeschlossen werden.");
        if(r.inspectionIntervalMonths()!=null&&!r.inspectionIntervalMonths().equals(d.getInspectionIntervalMonths()))
            throw bad("Prüfintervall zuerst im Gerätestamm ändern.");
        String note=String.join("; ",java.util.stream.Stream.of(r.defects(),r.measures(),r.notes())
            .filter(x->x!=null&&!x.isBlank()).map(String::trim).toList());
        String type=clean(r.inspectionType());
        if(type==null)type="Einzelprüfung";
        if(type.length()>120)throw bad("Prüfart ist zu lang.");
        DeviceInspection signed=workflow.record(d,r.result(),note,auth.getName(),r.signatureData(),type,
            null,null,null);
        return inspection(signed);
    }

    @GetMapping("/{id}/inspections")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public List<InspectionDto> inspections(@PathVariable Long id){
        find(id);
        return inspections.findByDeviceIdOrderByInspectionDateDesc(id).stream().map(this::inspection).toList();
    }

    @GetMapping("/{id}/inspections/{inspectionId}/protocol")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public InspectionProtocol protocol(@PathVariable Long id,@PathVariable Long inspectionId){
        Device d=find(id);
        DeviceInspection i=inspections.findById(inspectionId)
          .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Prüfprotokoll nicht gefunden"));
        if(!i.getDevice().getId().equals(d.getId()))
          throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Prüfprotokoll gehört nicht zu diesem Gerät");
        return new InspectionProtocol(i.getId(),i.getDeviceNameSnapshot()==null?d.getName():i.getDeviceNameSnapshot(),
          i.getLocationSnapshot(),i.getInspectionDate(),i.getResult(),i.getDefects(),i.getMeasures(),i.getNotes(),
          i.getInspector(),i.getNextInspectionDate(),i.getSignedAt(),i.getSignatureData(),i.getSessionReportId());
    }

    private void apply(Device d,DeviceRequest r,boolean creating){
        if(r==null || r.name()==null || r.name().isBlank())
            throw bad("Gerätebezeichnung ist erforderlich");
        if(r.inspectionIntervalMonths()!=null && r.inspectionIntervalMonths()<1)
            throw bad("Prüfintervall muss mindestens 1 Monat betragen");
        if(r.nextInspectionDate()!=null && r.nextInspectionDate().isBefore(LocalDate.now()) && (creating || !r.nextInspectionDate().equals(d.getNextInspectionDate())))
            throw bad("Nächster Prüftermin darf nicht in der Vergangenheit liegen");

        d.setName(r.name().trim());
        d.setInventoryNumber(clean(r.inventoryNumber()));
        d.setBarcode(cleanDigits(r.barcode()));
        d.setSerialNumber(clean(r.serialNumber()));
        d.setManufacturer(clean(r.manufacturer()));
        d.setModel(clean(r.model()));
        d.setCategory(clean(r.category()));
        d.setLocation(clean(r.location()));
        d.setPurchaseDate(r.purchaseDate());
        if(creating || r.nextInspectionDate()!=null)d.setNextInspectionDate(r.nextInspectionDate());
        d.setInspectionIntervalMonths(r.inspectionIntervalMonths());
        d.setResponsibleUsername(clean(r.responsibleUsername()));
        d.setNotes(clean(r.notes()));
        if(r.compartmentId()!=null)d.setCompartment(compartments.findById(r.compartmentId()).orElseThrow(()->bad("Fahrzeugfach nicht gefunden")));
        else if(!creating)d.setCompartment(null);
        if(creating || r.inspectionRequired()!=null)d.setInspectionRequired(Boolean.TRUE.equals(r.inspectionRequired()));
        if(creating) d.setActive(r.active()==null || r.active());
        else if(r.active()!=null) d.setActive(r.active());
    }

    private void validateInspection(InspectionRequest r){
        if(r==null || r.inspectionDate()==null) throw bad("Prüfdatum ist erforderlich");
        if(r.inspectionDate().isAfter(LocalDate.now())) throw bad("Prüfdatum darf nicht in der Zukunft liegen");
        if(r.nextInspectionDate()!=null && r.nextInspectionDate().isBefore(r.inspectionDate()))
            throw bad("Nächster Prüftermin darf nicht vor dem Prüfdatum liegen");
        if(r.inspectionIntervalMonths()!=null && r.inspectionIntervalMonths()<1)
            throw bad("Prüfintervall muss mindestens 1 Monat betragen");
        if(r.result()!=null && !Set.of("BESTANDEN","MIT_MANGEL","NICHT_BESTANDEN").contains(r.result()))
            throw bad("Ungültiges Prüfergebnis");
        if(r.inspectionType()!=null && r.inspectionType().trim().isEmpty())
            throw bad("Prüfart darf nicht leer sein");
        if(r.inspector()!=null && r.inspector().trim().isEmpty())
            throw bad("Prüfer darf nicht leer sein");
    }

    private LocalDate calculateNextInspection(LocalDate inspectionDate,Integer months){
        return inspectionDate.plusMonths(months);
    }

    private Device find(Long id){
        return devices.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Gerät nicht gefunden"));
    }
    private DeviceDto dto(Device d){
        return new DeviceDto(d.getId(),d.getName(),d.getInventoryNumber(),d.getBarcode(),d.getSerialNumber(),d.getManufacturer(),d.getModel(),
            d.getCategory(),d.getLocation(),d.getPurchaseDate(),d.getLastInspectionDate(),d.getNextInspectionDate(),d.getInspectionIntervalMonths(),d.getResponsibleUsername(),d.isActive(),status(d),
            d.getOperationalStatus()==null?"OK":d.getOperationalStatus(),d.getOperationalStatusAt(),d.getOperationalStatusNote(),
            d.getCompartment()==null?null:d.getCompartment().getId(),d.getCompartment()==null?null:d.getCompartment().getVehicle().getId(),
            d.getCompartment()==null?null:d.getCompartment().getVehicle().getName(),d.getCompartment()==null?null:d.getCompartment().getName(),
            d.isInspectionRequired(),d.getPlacementX(),d.getPlacementY(),d.getPlacementWidth(),d.getPlacementHeight(),d.getPlacementRotation(),d.getPlacementLayer(),d.getPlacementGroupId(),d.getCompartmentElementId());
    }
    private InspectionDto inspection(DeviceInspection i){
        return new InspectionDto(i.getId(),i.getInspectionDate(),i.getNextInspectionDate(),i.getInspectionType(),i.getResult(),i.getInspector(),i.getDefects(),i.getMeasures(),i.getNotes(),i.getSignedAt()!=null,i.getSessionReportId());
    }
    private String status(Device d){
        if(d.getNextInspectionDate()==null)return "UNBEKANNT";
        LocalDate n=LocalDate.now();
        if(d.getNextInspectionDate().isBefore(n))return "UEBERFAELLIG";
        if(!d.getNextInspectionDate().isAfter(n.plusDays(30)))return "BALD";
        return "GUELTIG";
    }
    private boolean contains(String s,String q){return s!=null&&s.toLowerCase().contains(q);}
    private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
    private String cleanDigits(String s){
        if(s==null||s.isBlank())return null;
        String v=s.replaceAll("\\D","");
        return v.isBlank()?null:v;
    }
    private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}

    public record DeviceRequest(String name,String inventoryNumber,String barcode,String serialNumber,String manufacturer,String model,String category,
        String location,LocalDate purchaseDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String responsibleUsername,String notes,Boolean active,Long compartmentId,Boolean inspectionRequired){}
    public record DeviceDto(Long id,String name,String inventoryNumber,String barcode,String serialNumber,String manufacturer,String model,String category,String location,
        LocalDate purchaseDate,LocalDate lastInspectionDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String responsibleUsername,boolean active,String status,
        String operationalStatus,java.time.Instant operationalStatusAt,String operationalStatusNote,Long compartmentId,Long vehicleId,String vehicleName,String compartmentName,
        boolean inspectionRequired,int placementX,int placementY,int placementWidth,int placementHeight,int placementRotation,int placementLayer,String placementGroupId,Long compartmentElementId){}
    public record DeviceDetailDto(DeviceDto device,List<InspectionDto> inspections){}
    public record InspectionProtocol(Long id,String deviceName,String location,LocalDate inspectionDate,String result,
      String defects,String measures,String notes,String inspector,LocalDate nextInspectionDate,Instant signedAt,
      String signatureData,Long sessionReportId){}
    public record InspectionRequiredRequest(Boolean required){}
    public record ConditionRequest(String status,String note){}
    public record InspectionRequest(LocalDate inspectionDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String inspectionType,String result,String inspector,String defects,String measures,String notes,String signatureData){}
    public record InspectionDto(Long id,LocalDate inspectionDate,LocalDate nextInspectionDate,String inspectionType,String result,String inspector,String defects,String measures,String notes,boolean signed,Long reportId){}
}
