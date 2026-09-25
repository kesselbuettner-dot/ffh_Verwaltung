package de.bierverein.api;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DeviceExcelImportControllerTest {
    private DeviceRepository devices;
    private FireVehicleRepository vehicles;
    private VehicleCompartmentRepository compartments;
    private DeviceExcelImportController controller;

    @BeforeEach void setup() {
        devices = mock(DeviceRepository.class);
        vehicles = mock(FireVehicleRepository.class);
        compartments = mock(VehicleCompartmentRepository.class);
        controller = new DeviceExcelImportController(devices, vehicles, compartments);
        when(devices.findAll()).thenReturn(List.of());
    }

    private MockMultipartFile sheet(String[][] data) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var tab = wb.createSheet("Geräte");
            for (int n = 0; n < data.length; n++) {
                var row = tab.createRow(n);
                for (int c = 0; c < data[n].length; c++) row.createCell(c).setCellValue(data[n][c]);
            }
            wb.write(out);
            return new MockMultipartFile("file", "geraete.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test void previewAndImportOnlyNewDevicesPreservingExistingRecords() throws Exception {
        Device old = new Device(); old.setInventoryNumber("001");
        when(devices.findAll()).thenReturn(List.of(old));
        MockMultipartFile file = sheet(new String[][]{
            {"Bezeichnung", "Inventarnummer", "Prüfpflichtig", "Nächster Prüftermin"},
            {"Altes Gerät", "001", "ja", "25.12.2027"},
            {"Neue Pumpe", "002", "ja", "25.12.2027"}
        });
        var preview = controller.preview(file);
        assertEquals(1, preview.ready()); assertEquals(1, preview.skipped()); assertEquals(0, preview.errors());
        assertEquals("VORHANDEN", preview.rows().get(0).status());
        assertEquals(1, controller.commit(file).imported());
        @SuppressWarnings("unchecked") var captured = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(devices).saveAll(captured.capture());
        List<Device> saved = (List<Device>) captured.getValue();
        assertEquals(1, saved.size()); assertEquals("002", saved.get(0).getInventoryNumber());
        assertTrue(saved.get(0).isInspectionRequired());
        assertEquals(java.time.LocalDate.of(2027, 12, 25), saved.get(0).getNextInspectionDate());
    }

    @Test void invalidRoomPreventsEntireImport() throws Exception {
        MockMultipartFile file = sheet(new String[][]{
            {"Bezeichnung", "Inventarnummer", "Fahrzeug", "Fach"},
            {"Neue Pumpe", "002", "", ""},
            {"Strahlrohr", "003", "LF20", "G1"}
        });
        var result = controller.preview(file);
        assertEquals(1, result.ready()); assertEquals(1, result.errors());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.commit(file));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(devices, never()).saveAll(anyList());
    }

    @Test void duplicateIdentifiersWithinFilePreventImport() throws Exception {
        MockMultipartFile file = sheet(new String[][]{
            {"Bezeichnung", "Inventarnummer"}, {"Erstes", "007"}, {"Zweites", "007"}
        });
        assertEquals(1, controller.preview(file).errors());
        assertThrows(ResponseStatusException.class, () -> controller.commit(file));
        verify(devices, never()).saveAll(anyList());
    }

    @Test void templateIsReadableExcelFile() throws Exception {
        byte[] bytes = controller.template().getBody();
        assertNotNull(bytes);
        try (XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertEquals("Bezeichnung", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Hinweise", wb.getSheetAt(1).getSheetName());
        }
    }
}
