package de.bierverein.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingScheduleSeriesTest {
    TrainingScheduleEventRepository events = mock(TrainingScheduleEventRepository.class);
    TrainingSeriesExceptionRepository exceptions = mock(TrainingSeriesExceptionRepository.class);
    TrainingAttendanceRepository attendance = mock(TrainingAttendanceRepository.class);
    AppUserRepository users = mock(AppUserRepository.class);
    MemberRepository members = mock(MemberRepository.class);
    EffectivePermissionService permissions = mock(EffectivePermissionService.class);
    DeviceRepository devices = mock(DeviceRepository.class);
    DeviceInspectionTaskRepository tasks = mock(DeviceInspectionTaskRepository.class);
    HolidayService holidays = mock(HolidayService.class);
    TrainingScheduleService service;
    LocalDate monday = LocalDate.now(ZoneId.of("Europe/Berlin")).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @BeforeEach void setup() {
        service = new TrainingScheduleService(events, attendance, exceptions, users, members, permissions, devices, tasks, holidays);
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(1L);
        when(user.getUsername()).thenReturn("wart");
        when(user.getRole()).thenReturn(Role.GERATEWART);
        when(users.findByUsernameWithMember("wart")).thenReturn(Optional.of(user));
        when(permissions.hasPermission(1L, "training.services.write")).thenReturn(true);
        when(events.save(any(TrainingScheduleEvent.class))).thenAnswer(a -> a.getArgument(0));
        when(members.findAllById(any())).thenReturn(List.of());
    }

    private TrainingScheduleService.EventRequest request(LocalDate start, LocalDate end, boolean recurring, boolean separate) {
        return new TrainingScheduleService.EventRequest("SERVICE", "Gerätewart", "Notiz", start, end, recurring,
                List.of("MONDAY"), true, null, null, List.of(), "ALL", null, true, false, false,
                List.of(), List.of(), separate);
    }

    @Test void seriesCanBeStoredAsDistinctEvents() {
        service.create("wart", request(monday, monday.plusWeeks(2), true, true));
        var captor = org.mockito.ArgumentCaptor.forClass(TrainingScheduleEvent.class);
        verify(events, times(3)).save(captor.capture());
        assertEquals(List.of(monday, monday.plusWeeks(1), monday.plusWeeks(2)), captor.getAllValues().stream().map(TrainingScheduleEvent::getStartDate).toList());
        assertTrue(captor.getAllValues().stream().noneMatch(TrainingScheduleEvent::isRecurring));
        assertTrue(captor.getAllValues().stream().allMatch(TrainingScheduleEvent::isRegistrationRequired));
    }

    @Test void detachedOccurrencePreservesAnswersAndInspectionTasks() {
        TrainingScheduleEvent series = new TrainingScheduleEvent();
        ReflectionTestUtils.setField(series,"id",10L);
        series.setType("SERVICE"); series.setStartDate(monday); series.setEndDate(monday.plusWeeks(2));
        series.setRecurring(true); series.setWeekdays("MONDAY"); series.setAllDay(true);
        when(events.findById(10L)).thenReturn(Optional.of(series));
        when(events.saveAndFlush(any(TrainingScheduleEvent.class))).thenAnswer(a -> {
            TrainingScheduleEvent single = a.getArgument(0);
            ReflectionTestUtils.setField(single,"id",99L);
            return single;
        });
        TrainingAttendance response = new TrainingAttendance(10L,monday.plusWeeks(1),"wart",null,"YES");
        when(attendance.findByEventIdAndOccurrenceDate(10L,monday.plusWeeks(1))).thenReturn(List.of(response));
        DeviceInspectionTask task = new DeviceInspectionTask();
        task.setEventId(10L);task.setOccurrenceDate(monday.plusWeeks(1));
        when(tasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(10L,monday.plusWeeks(1))).thenReturn(List.of(task));
        service.updateOccurrence("wart",10L,monday.plusWeeks(1),request(monday.plusWeeks(1).plusDays(1),null,false,false));
        assertEquals(99L,response.getEventId());
        assertEquals(monday.plusWeeks(1).plusDays(1),response.getOccurrenceDate());
        assertEquals(99L,task.getEventId());
        assertEquals(monday.plusWeeks(1).plusDays(1),task.getOccurrenceDate());
        verify(exceptions).save(argThat(x -> x.getSeriesEventId().equals(10L) && x.getOccurrenceDate().equals(monday.plusWeeks(1))));
    }
}
