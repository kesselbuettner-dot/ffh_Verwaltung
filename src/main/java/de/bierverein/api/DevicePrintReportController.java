package de.bierverein.api;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Device audit export: historical inspection dates and location snapshots, no signature bytes. */
@RestController
@RequestMapping("/api/devices/print-report")
public class DevicePrintReportController {
    private final DeviceInspectionRepository inspections;
    public DevicePrintReportController(DeviceInspectionRepository inspections){this.inspections=inspections;}

    public record Entry(Long id,Long deviceId,String deviceName,String inventoryNumber,String location,
                        LocalDate inspectionDate,String inspectionType,String result,String inspector,
                        String notes,LocalDate nextInspectionDate,boolean signed){}
    public record AnnualReport(int year,String location,List<String> locations,List<Entry> entries){}

    @GetMapping
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    @Transactional(readOnly=true)
    public ResponseEntity<AnnualReport> annual(@RequestParam int year,
                                              @RequestParam(defaultValue="") String location){
        if(year<2000||year>2100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiges Berichtsjahr.");
        if(location.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Standortfilter zu lang.");
        List<DeviceInspection> all=inspections
                .findByInspectionDateGreaterThanEqualAndInspectionDateLessThanOrderByInspectionDateAsc(
                        LocalDate.of(year,1,1),LocalDate.of(year+1,1,1));
        List<Entry> lines=all.stream().map(i->{
            Device d=i.getDevice();
            String name=i.getDeviceNameSnapshot()!=null?i.getDeviceNameSnapshot():d.getName();
            String place=i.getLocationSnapshot()!=null?i.getLocationSnapshot():
                    d.getCompartment()!=null?d.getCompartment().getVehicle().getName()+" / "+d.getCompartment().getName():d.getLocation();
            String inventory=i.getInventorySnapshot()!=null?i.getInventorySnapshot():d.getInventoryNumber();
            return new Entry(i.getId(),d.getId(),name,inventory,
                    place==null?"Ohne Standort":place,i.getInspectionDate(),i.getInspectionType(),
                    i.getResult(),i.getInspector(),i.getNotes(),i.getNextInspectionDate(),i.getSignedAt()!=null);
        }).toList();
        List<String> locations=lines.stream().map(Entry::location).distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<Entry> selected=lines.stream().filter(i->location.isBlank()||Objects.equals(i.location(),location))
                .sorted(Comparator.comparing(Entry::location,String.CASE_INSENSITIVE_ORDER)
                  .thenComparing(Entry::inspectionDate).thenComparing(Entry::deviceName,String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AnnualReport(year,location,locations,selected));
    }
}
