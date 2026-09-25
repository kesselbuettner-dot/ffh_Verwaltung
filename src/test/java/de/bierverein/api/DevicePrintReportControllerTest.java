package de.bierverein.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class DevicePrintReportControllerTest {
    private final DeviceInspectionRepository inspections=mock(DeviceInspectionRepository.class);
    private final DevicePrintReportController controller=new DevicePrintReportController(inspections);

    private DeviceInspection entry(long id,String name,String location,LocalDate day){
        Device device=new Device();
        ReflectionTestUtils.setField(device,"id",id);
        device.setName(name);device.setLocation(location);device.setInventoryNumber("INV-"+id);
        DeviceInspection check=new DeviceInspection();
        ReflectionTestUtils.setField(check,"id",id+100);
        check.setDevice(device);check.setInspectionDate(day);
        check.setInspector("Prüfer");check.setResult("BESTANDEN");
        return check;
    }

    @Test void selectedYearAndLocationUseHistoricalSnapshotAndStableLocationSorting(){
        var older=entry(1,"Atemschutz","Andere Wache",LocalDate.of(2026,3,5));
        var newest=entry(2,"Kuppel","Halle",LocalDate.of(2026,8,1));
        newest.setLocationSnapshot("Archivstandort");
        var next=entry(3,"Leiter","Halle",LocalDate.of(2026,2,2));
        when(inspections.findByInspectionDateGreaterThanEqualAndInspectionDateLessThanOrderByInspectionDateAsc(any(),any()))
            .thenReturn(List.of(older,newest,next));
        var all=controller.annual(2026,"").getBody();
        assertNotNull(all);
        assertEquals(List.of("Andere Wache","Archivstandort","Halle"),all.locations());
        assertEquals(List.of("Andere Wache","Archivstandort","Halle"),
            all.entries().stream().map(DevicePrintReportController.Entry::location).toList());
        assertEquals("Archivstandort",controller.annual(2026,"Archivstandort").getBody().entries().get(0).location());
        assertEquals(1,controller.annual(2026,"Archivstandort").getBody().entries().size());
        verify(inspections,atLeastOnce()).findByInspectionDateGreaterThanEqualAndInspectionDateLessThanOrderByInspectionDateAsc(
            LocalDate.of(2026,1,1),LocalDate.of(2027,1,1));
    }

    @Test void invalidYearAndTooLongLocationAreRejected(){
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->controller.annual(1999,"")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,()->controller.annual(2026,"x".repeat(201))).getStatusCode());
        verifyNoInteractions(inspections);
    }

    @Test void emptyYearKeepsEmptyReport(){
        when(inspections.findByInspectionDateGreaterThanEqualAndInspectionDateLessThanOrderByInspectionDateAsc(any(),any())).thenReturn(List.of());
        var response=controller.annual(2026,"");
        assertNotNull(response.getBody());
        assertTrue(response.getBody().entries().isEmpty());
        assertEquals("no-store",response.getHeaders().getCacheControl());
    }
}
