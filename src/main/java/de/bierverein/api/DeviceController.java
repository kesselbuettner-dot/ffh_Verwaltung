package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceRepository devices;
    private final DeviceInspectionRepository inspections;

    public DeviceController(DeviceRepository devices, DeviceInspectionRepository inspections){
        this.devices=devices; this.inspections=inspections;
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

    @PostMapping("/{id}/inspections")
    @Transactional
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    public InspectionDto inspect(@PathVariable Long id,@RequestBody InspectionRequest r){
        Device d=find(id);
        validateInspection(r);

        LocalDate next = r.nextInspectionDate();
        Integer intervalMonths = r.inspectionIntervalMonths()!=null
            ? r.inspectionIntervalMonths()
            : d.getInspectionIntervalMonths();
        if(next==null && intervalMonths!=null){
            next = calculateNextInspection(r.inspectionDate(), intervalMonths);
        }

        DeviceInspection i=new DeviceInspection();
        i.setDevice(d);
        i.setInspectionDate(r.inspectionDate());
        i.setNextInspectionDate(next);
        i.setInspectionType(clean(r.inspectionType()));
        i.setResult(clean(r.result()));
        i.setInspector(clean(r.inspector()));
        i.setDefects(clean(r.defects()));
        i.setMeasures(clean(r.measures()));
        i.setNotes(clean(r.notes()));

        if(next!=null) d.setNextInspectionDate(next);
        d.setLastInspectionDate(r.inspectionDate());
        devices.save(d);
        return inspection(inspections.save(i));
    }

    @GetMapping("/{id}/inspections")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public List<InspectionDto> inspections(@PathVariable Long id){
        find(id);
        return inspections.findByDeviceIdOrderByInspectionDateDesc(id).stream().map(this::inspection).toList();
    }

    private void apply(Device d,DeviceRequest r,boolean creating){
        if(r==null || r.name()==null || r.name().isBlank())
            throw bad("Gerätebezeichnung ist erforderlich");
        if(r.inspectionIntervalMonths()!=null && r.inspectionIntervalMonths()<1)
            throw bad("Prüfintervall muss mindestens 1 Monat betragen");
        if(r.nextInspectionDate()!=null && r.nextInspectionDate().isBefore(LocalDate.now()))
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
        d.setNextInspectionDate(r.nextInspectionDate());
        d.setInspectionIntervalMonths(r.inspectionIntervalMonths());
        d.setResponsibleUsername(clean(r.responsibleUsername()));
        d.setNotes(clean(r.notes()));
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
            d.getCategory(),d.getLocation(),d.getPurchaseDate(),d.getLastInspectionDate(),d.getNextInspectionDate(),d.getInspectionIntervalMonths(),d.getResponsibleUsername(),d.isActive(),status(d));
    }
    private InspectionDto inspection(DeviceInspection i){
        return new InspectionDto(i.getId(),i.getInspectionDate(),i.getNextInspectionDate(),i.getInspectionType(),i.getResult(),i.getInspector(),i.getDefects(),i.getMeasures(),i.getNotes());
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
        String location,LocalDate purchaseDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String responsibleUsername,String notes,Boolean active){}
    public record DeviceDto(Long id,String name,String inventoryNumber,String barcode,String serialNumber,String manufacturer,String model,String category,String location,
        LocalDate purchaseDate,LocalDate lastInspectionDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String responsibleUsername,boolean active,String status){}
    public record DeviceDetailDto(DeviceDto device,List<InspectionDto> inspections){}
    public record InspectionRequest(LocalDate inspectionDate,LocalDate nextInspectionDate,Integer inspectionIntervalMonths,String inspectionType,String result,String inspector,String defects,String measures,String notes){}
    public record InspectionDto(Long id,LocalDate inspectionDate,LocalDate nextInspectionDate,String inspectionType,String result,String inspector,String defects,String measures,String notes){}
}