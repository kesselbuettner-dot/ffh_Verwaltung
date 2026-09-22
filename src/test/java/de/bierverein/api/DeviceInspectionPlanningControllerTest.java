package de.bierverein.api;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DeviceInspectionPlanningControllerTest {
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final DeviceLocationRepository locations = mock(DeviceLocationRepository.class);
    private final TrainingScheduleEventRepository events = mock(TrainingScheduleEventRepository.class);
    private final DeviceInspectionTaskRepository tasks = mock(DeviceInspectionTaskRepository.class);
    private final TrainingSeriesExceptionRepository exceptions = mock(TrainingSeriesExceptionRepository.class);
    private final DeviceInspectionSessionReportRepository reports = mock(DeviceInspectionSessionReportRepository.class);
    private final DeviceInspectionPlanningController controller =
        new DeviceInspectionPlanningController(devices, locations, events, tasks, exceptions);

    DeviceInspectionPlanningControllerTest() {
        ReflectionTestUtils.setField(controller, "reports", reports);
    }

    private TrainingScheduleEvent appointment(LocalDate date) {
        TrainingScheduleEvent event = new TrainingScheduleEvent();
        ReflectionTestUtils.setField(event, "id", 23L);
        event.setTitle("Prüfdienst");
        event.setStartDate(date);
        event.setEndDate(date);
        event.setDeviceInspection(true);
        event.setDeviceLocations("LF 20");
        event.setDeviceCategories("Strahlrohr");
        return event;
    }

    private DeviceInspectionTask position(Device d, LocalDate date, String status) {
        DeviceInspectionTask task = new DeviceInspectionTask();
        task.setDevice(d);
        task.setEventId(23L);
        task.setOccurrenceDate(date);
        task.setStatus(status);
        return task;
    }

    @Test void sessionsReadIsTransactionalForLazyDeviceReferences() throws Exception {
        Method method = DeviceInspectionPlanningController.class.getMethod("sessions");
        Transactional tx = method.getAnnotation(Transactional.class);
        assertNotNull(tx, "Controller must keep device references attached throughout session DTO conversion");
        assertTrue(tx.readOnly());
    }

    @Test void listsEmptyAndPopulatedAppointmentsWithoutDroppingPendingPositions() {
        LocalDate today = LocalDate.now();
        TrainingScheduleEvent event = appointment(today);
        when(events.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(any(), any()))
            .thenReturn(List.of(), List.of(event));
        assertEquals(List.of(), controller.sessions());

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 12L);
        device.setName("Strahlrohr");
        device.setActive(true);
        device.setInspectionRequired(true);
        DeviceInspectionTask pending = position(device, today, "PENDING");
        DeviceInspectionTask complete = position(device, today, "INSPECTED");
        when(tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(eq(23L), eq(today)))
            .thenReturn(List.of(pending, complete));
        when(exceptions.findBySeriesEventId(23L)).thenReturn(List.of());
        when(reports.findByEventIdAndOccurrenceDate(23L, today)).thenReturn(Optional.empty());

        var result = controller.sessions();
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).total());
        assertEquals(1, result.get(0).completed());
        assertNull(result.get(0).reportId());
    }

    @Test void legacyCycleDeviceAppearsInBothOverviewAndDetail() {
        LocalDate today = LocalDate.now();
        TrainingScheduleEvent event = appointment(today);
        when(events.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(any(), any()))
            .thenReturn(List.of(event));
        when(events.findById(23L)).thenReturn(Optional.of(event));
        when(exceptions.findBySeriesEventId(23L)).thenReturn(List.of());
        when(exceptions.existsBySeriesEventIdAndOccurrenceDate(23L, today)).thenReturn(false);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 14L);
        device.setName("Altgerät mit Prüfintervall");
        device.setActive(true);
        device.setInspectionRequired(false);
        device.setInspectionIntervalMonths(12);
        device.setLocation("LF 20");
        device.setCategory("Strahlrohr");
        device.setLastInspectionDate(today.minusYears(1));
        device.setNextInspectionDate(today);

        DeviceInspectionTask task = position(device, today, "PENDING");
        ReflectionTestUtils.setField(task, "id", 55L);
        when(tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(23L, today))
            .thenReturn(List.of(task));
        when(devices.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(device));
        when(reports.findByEventIdAndOccurrenceDate(23L, today)).thenReturn(Optional.empty());

        var overview = controller.sessions();
        assertEquals(1, overview.size());
        assertEquals(1, overview.get(0).total());
        var detail = controller.session(23L, today);
        assertEquals(1, detail.tasks().size(), "Cycle-based legacy device must not disappear from detail view");
        assertEquals(14L, detail.tasks().get(0).deviceId());
    }

    @Test void archivedDeviceDoesNotBreakAppointmentOverview() {
        LocalDate today = LocalDate.now();
        TrainingScheduleEvent event = appointment(today);
        when(events.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(any(), any()))
            .thenReturn(List.of(event));
        when(exceptions.findBySeriesEventId(23L)).thenReturn(List.of());
        Device archived = new Device();
        archived.setActive(false);
        when(tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(23L, today))
            .thenReturn(List.of(position(archived, today, "PENDING")));
        when(reports.findByEventIdAndOccurrenceDate(23L, today)).thenReturn(Optional.empty());
        var result = controller.sessions();
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).total());
    }
}
