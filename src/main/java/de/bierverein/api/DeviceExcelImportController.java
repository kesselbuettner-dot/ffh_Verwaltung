package de.bierverein.api;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/devices/import")
public class DeviceExcelImportController {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final String[] COLUMNS = {"Bezeichnung", "Inventarnummer", "Barcode", "Seriennummer", "Hersteller", "Modell", "Kategorie", "Standort", "Fahrzeug", "Fach", "Prüfpflichtig", "Kaufdatum", "Nächster Prüftermin", "Prüfintervall Monate", "Verantwortlich", "Bemerkungen"};
    private final DeviceRepository devices;
    private final FireVehicleRepository vehicles;
    private final VehicleCompartmentRepository compartments;

    public DeviceExcelImportController(DeviceRepository devices, FireVehicleRepository vehicles, VehicleCompartmentRepository compartments) {
        this.devices = devices;
        this.vehicles = vehicles;
        this.compartments = compartments;
    }

    @GetMapping("/template")
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'read')")
    public ResponseEntity<byte[]> template() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Geräte");
            Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont(); font.setBold(true); style.setFont(font);
            for (int i = 0; i < COLUMNS.length; i++) {
                Cell cell = header.createCell(i); cell.setCellValue(COLUMNS[i]); cell.setCellStyle(style);
                sheet.setColumnWidth(i, Math.min(42, Math.max(18, COLUMNS[i].length() + 4)) * 256);
            }
            sheet.createFreezePane(0, 1);
            Sheet help = workbook.createSheet("Hinweise");
            String[] lines = {
                "Eine Zeile pro Gerät. Nur das erste Tabellenblatt 'Geräte' wird importiert.",
                "Pflicht: Bezeichnung und mindestens eine Inventarnummer, Barcode oder Seriennummer.",
                "Inventarnummern, Barcodes und Seriennummern als Text eintragen (führende Nullen!).",
                "Vorhandene Kennungen werden übersprungen; bestehende Geräte werden nicht geändert.",
                "Für die Zuordnung zu einem Fach Fahrzeug UND Fach exakt wie in der Verwaltung benennen.",
                "Ohne Fach bleibt Standort ein freier Text, z. B. Lager oder Werkstatt.",
                "Prüfpflichtig: ja/nein, x/leer oder 1/0. Termine: TT.MM.JJJJ oder JJJJ-MM-TT.",
                "Maximal 1000 Geräte und 5 MB pro Datei. Bei einer fehlerhaften Zeile wird nichts importiert."
            };
            for (int i = 0; i < lines.length; i++) help.createRow(i).createCell(0).setCellValue(lines[i]);
            help.setColumnWidth(0, 120 * 256);
            workbook.write(out);
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=FFH-Geraeteimport-Vorlage.xlsx")
                .body(out.toByteArray());
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    @Transactional(readOnly = true)
    public Preview preview(@RequestPart("file") MultipartFile file) throws IOException {
        return check(file).preview();
    }

    @PostMapping(value = "/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@devicePermissionGuard.allowed(authentication, 'write')")
    @Transactional
    public synchronized ImportResult commit(@RequestPart("file") MultipartFile file) throws IOException {
        Checked checked = check(file);
        Preview preview = checked.preview();
        if (preview.errors() != 0) throw bad("Datei enthält Fehler. Bitte die Vorschau prüfen und korrigieren.");
        List<Device> additions = new ArrayList<>();
        for (Candidate c : checked.candidates()) {
            if (!"NEU".equals(c.status())) continue;
            Device d = new Device();
            d.setName(c.name()); d.setInventoryNumber(c.inventory()); d.setBarcode(c.barcode());
            d.setSerialNumber(c.serial()); d.setManufacturer(c.manufacturer()); d.setModel(c.model());
            d.setCategory(c.category()); d.setLocation(c.location()); d.setCompartment(c.compartment());
            d.setInspectionRequired(c.inspectionRequired()); d.setPurchaseDate(c.purchase());
            d.setNextInspectionDate(c.nextInspection()); d.setInspectionIntervalMonths(c.interval());
            d.setResponsibleUsername(c.responsible()); d.setNotes(c.notes());
            additions.add(d);
        }
        devices.saveAll(additions);
        return new ImportResult(additions.size(), preview.skipped());
    }

    private Checked check(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw bad("Bitte eine Excel-Datei im Format .xlsx wählen.");
        if (file.getSize() > MAX_BYTES) throw bad("Die Excel-Datei darf höchstens 5 MB groß sein.");
        List<Candidate> rows = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(file.getBytes()))) {
            if (!(wb instanceof XSSFWorkbook)) throw bad("Bitte eine echte .xlsx-Datei wählen.");
            Sheet sheet = wb.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS + 1) throw bad("Höchstens 1000 Geräte pro Import.");
            Row header = sheet.getRow(0);
            if (header == null) throw bad("Die Kopfzeile fehlt.");
            DataFormatter formatter = new DataFormatter(Locale.GERMAN);
            Map<String, Integer> cols = new HashMap<>();
            for (Cell c : header) {
                String key = normalize(formatter.formatCellValue(c));
                if (!key.isEmpty() && cols.putIfAbsent(key, c.getColumnIndex()) != null) throw bad("Spalte mehrfach vorhanden: " + key);
            }
            if (!cols.containsKey(normalize(COLUMNS[0]))) throw bad("Spalte 'Bezeichnung' fehlt. Bitte die Vorlage verwenden.");
            for (int n = 1; n <= sheet.getLastRowNum(); n++) {
                Row row = sheet.getRow(n); if (row == null) continue;
                boolean blank = true;
                for (Cell cell : row) if (!formatter.formatCellValue(cell).trim().isEmpty()) { blank = false; break; }
                if (blank) continue;
                if (rows.size() >= MAX_ROWS) throw bad("Höchstens 1000 Geräte pro Import.");
                rows.add(parse(row, cols, formatter));
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw bad("Excel-Datei konnte nicht gelesen werden. Bitte eine gültige .xlsx-Datei verwenden.");
        }
        if (rows.isEmpty()) throw bad("Die Excel-Datei enthält keine Geräte.");
        markDuplicates(rows);
        int errors = (int) rows.stream().filter(c -> "FEHLER".equals(c.status())).count();
        int skipped = (int) rows.stream().filter(c -> "VORHANDEN".equals(c.status())).count();
        return new Checked(rows, new Preview(rows.stream().map(c -> new PreviewRow(c.row(), c.name(), c.inventory(), c.vehicle(), c.room(), c.status(), c.message())).toList(), rows.size() - skipped - errors, skipped, errors));
    }

    private Candidate parse(Row row, Map<String, Integer> cols, DataFormatter formatter) {
        int number = row.getRowNum() + 1;
        try {
            for (Cell cell : row) if (cell.getCellType() == CellType.FORMULA) throw new IllegalArgumentException("Formeln sind im Import nicht erlaubt.");
            String name = value(row, cols, formatter, 0, 150), inventory = value(row, cols, formatter, 1, 80);
            String barcode = value(row, cols, formatter, 2, 80), serial = value(row, cols, formatter, 3, 120);
            if (name == null) throw new IllegalArgumentException("Bezeichnung fehlt.");
            if (inventory == null && barcode == null && serial == null) throw new IllegalArgumentException("Inventarnummer, Barcode oder Seriennummer erforderlich.");
            if (barcode != null && !barcode.matches("[0-9]+")) throw new IllegalArgumentException("Barcode muss nur Ziffern enthalten.");
            String manufacturer = value(row, cols, formatter, 4, 120), model = value(row, cols, formatter, 5, 120);
            String category = value(row, cols, formatter, 6, 80), location = value(row, cols, formatter, 7, 120);
            String vehicleName = value(row, cols, formatter, 8, 120), roomName = value(row, cols, formatter, 9, 80);
            String required = value(row, cols, formatter, 10, 8);
            boolean inspectionRequired = required != null && switch (required.toLowerCase(Locale.ROOT)) {
                case "ja", "j", "x", "1", "true" -> true;
                case "nein", "n", "0", "false" -> false;
                default -> throw new IllegalArgumentException("Prüfpflichtig: ja oder nein eintragen.");
            };
            LocalDate purchase = date(row, cols, formatter, 11), next = date(row, cols, formatter, 12);
            String intervalText = value(row, cols, formatter, 13, 10);
            Integer interval = intervalText == null ? null : Integer.parseInt(intervalText);
            if (interval != null && (interval < 1 || interval > 1200)) throw new IllegalArgumentException("Prüfintervall muss zwischen 1 und 1200 Monaten liegen.");
            String responsible = value(row, cols, formatter, 14, 120), notes = value(row, cols, formatter, 15, 1000);
            VehicleCompartment compartment = null;
            if (vehicleName != null || roomName != null) {
                if (vehicleName == null || roomName == null) throw new IllegalArgumentException("Fahrzeug und Fach müssen beide angegeben sein.");
                List<FireVehicle> matches = vehicles.findByActiveTrueOrderByNameAsc().stream().filter(v -> v.getName().equalsIgnoreCase(vehicleName)).toList();
                if (matches.size() != 1) throw new IllegalArgumentException("Fahrzeug nicht eindeutig gefunden: " + vehicleName);
                List<VehicleCompartment> rooms = compartments.findByVehicleIdOrderByGridYAscGridXAsc(matches.get(0).getId()).stream().filter(c -> c.getName().equalsIgnoreCase(roomName)).toList();
                if (rooms.size() != 1) throw new IllegalArgumentException("Fach nicht eindeutig gefunden: " + roomName);
                compartment = rooms.get(0); location = compartment.getName();
            }
            return new Candidate(number, name, inventory, barcode, serial, manufacturer, model, category, location, vehicleName, roomName, compartment, inspectionRequired, purchase, next, interval, responsible, notes, "NEU", "Bereit zum Import");
        } catch (RuntimeException e) {
            return new Candidate(number, safeName(row, cols, formatter), null, null, null, null, null, null, null, null, null, null, false, null, null, null, null, null, "FEHLER", e instanceof NumberFormatException ? "Prüfintervall muss eine ganze Zahl sein." : e.getMessage());
        }
    }

    private String safeName(Row row, Map<String, Integer> cols, DataFormatter formatter) {
        try { return value(row, cols, formatter, 0, 150); } catch (RuntimeException e) { return null; }
    }

    private void markDuplicates(List<Candidate> candidates) {
        Map<String, Long> registered = new HashMap<>();
        for (Device d : devices.findAll()) {
            for (String k : keys(d.getInventoryNumber(), d.getBarcode(), d.getSerialNumber())) registered.putIfAbsent(k, d.getId());
        }
        Set<String> fileKeys = new HashSet<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i); if (!"NEU".equals(c.status())) continue;
            List<String> identifiers = keys(c.inventory(), c.barcode(), c.serial());
            Set<Long> matches = new HashSet<>(); for (String k : identifiers) if (registered.containsKey(k)) matches.add(registered.get(k));
            boolean repeated = identifiers.stream().anyMatch(fileKeys::contains);
            if (matches.size() > 1 || repeated) candidates.set(i, c.withStatus("FEHLER", "Kennungen gehören zu verschiedenen oder mehrfach aufgeführten Geräten."));
            else if (!matches.isEmpty()) candidates.set(i, c.withStatus("VORHANDEN", "Gerät bereits vorhanden; wird nicht geändert."));
            identifiers.forEach(fileKeys::add);
        }
    }

    private List<String> keys(String inventory, String barcode, String serial) {
        List<String> out = new ArrayList<>();
        if (inventory != null && !inventory.isBlank()) out.add("i:" + inventory.trim().toLowerCase(Locale.ROOT));
        if (barcode != null && !barcode.isBlank()) out.add("b:" + barcode.trim());
        if (serial != null && !serial.isBlank()) out.add("s:" + serial.trim().toLowerCase(Locale.ROOT));
        return out;
    }

    private String value(Row row, Map<String, Integer> cols, DataFormatter formatter, int index, int max) {
        Integer column = cols.get(normalize(COLUMNS[index])); if (column == null) return null;
        Cell cell = row.getCell(column);
        String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
        if (value.isBlank()) return null;
        if (value.length() > max) throw new IllegalArgumentException(COLUMNS[index] + " ist zu lang (maximal " + max + " Zeichen).");
        return value;
    }

    private LocalDate date(Row row, Map<String, Integer> cols, DataFormatter formatter, int index) {
        Integer column = cols.get(normalize(COLUMNS[index])); if (column == null) return null;
        Cell cell = row.getCell(column); if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue()))
                return DateUtil.getLocalDateTime(cell.getNumericCellValue()).toLocalDate();
            String text = formatter.formatCellValue(cell).trim(); if (text.isEmpty()) return null;
            try { return LocalDate.parse(text); }
            catch (DateTimeParseException e) { return LocalDate.parse(text, DateTimeFormatter.ofPattern("d.M.uuuu")); }
        } catch (RuntimeException e) { throw new IllegalArgumentException(COLUMNS[index] + ": Datum im Format TT.MM.JJJJ oder JJJJ-MM-TT eingeben."); }
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replaceAll("[^a-z0-9]", "");
    }
    private ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    public record PreviewRow(int row, String name, String inventoryNumber, String vehicle, String compartment, String status, String message) {}
    public record Preview(List<PreviewRow> rows, int ready, int skipped, int errors) {}
    public record ImportResult(int imported, int skipped) {}
    private record Checked(List<Candidate> candidates, Preview preview) {}
    private record Candidate(int row, String name, String inventory, String barcode, String serial, String manufacturer, String model,
                             String category, String location, String vehicle, String room, VehicleCompartment compartment,
                             boolean inspectionRequired, LocalDate purchase, LocalDate nextInspection, Integer interval,
                             String responsible, String notes, String status, String message) {
        Candidate withStatus(String status, String message) {
            return new Candidate(row, name, inventory, barcode, serial, manufacturer, model, category, location, vehicle, room,
                    compartment, inspectionRequired, purchase, nextInspection, interval, responsible, notes, status, message);
        }
    }
}
