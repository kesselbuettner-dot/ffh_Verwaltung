package de.bierverein.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class DrivingYearReportControllerTest {
    private final FireQualificationCheckRepository checks = mock(FireQualificationCheckRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppSettingsRepository settings = mock(AppSettingsRepository.class);
    private final DrivingYearReportController controller =
            new DrivingYearReportController(checks, members, users, settings);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test void annualReportIncludesOnlySavedChecksWithinLocalCalendarYearAndOrganisation() {
        FireQualificationCheck check = new FireQualificationCheck(42L, 70L, 5L, "MANUAL", "2025");
        check.id = 99L;
        check.checkedAt = Instant.parse("2025-12-31T23:30:00Z"); // 01.01.2026 in Berlin
        when(checks.findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(check));
        Member member = new Member();
        ReflectionTestUtils.setField(member, "id", 42L);
        member.setName("Max Muster");
        AppUser checker = new AppUser();
        ReflectionTestUtils.setField(checker, "id", 5L);
        checker.setUsername("wehrleitung");
        when(members.findAllById(any())).thenReturn(List.of(member));
        when(users.findAllById(any())).thenReturn(List.of(checker));
        AppSettings organisation = new AppSettings();
        organisation.setOrganizationName("Freiwillige Feuerwehr Beispiel");
        organisation.setStreet("Gerätehaus 1");
        organisation.setCity("Leipzig");
        organisation.setLogoData(new byte[] {1, 2, 3});
        when(settings.findAll()).thenReturn(List.of(organisation));

        var response = controller.report(2026);
        assertEquals("no-store", response.getHeaders().getCacheControl());
        var report = response.getBody();
        assertNotNull(report);
        assertEquals(2026, report.year());
        assertEquals("Freiwillige Feuerwehr Beispiel", report.organisation().name());
        assertTrue(report.organisation().logoAvailable());
        assertEquals(1, report.entries().size());
        var line = report.entries().get(0);
        assertEquals("Max Muster", line.memberName());
        assertEquals("wehrleitung", line.checkedBy());
        assertEquals(check.checkedAt, line.checkedAt());
        assertEquals("MANUAL", line.method());
        assertEquals("POSITIVE", line.result());

        verify(checks).findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(
                LocalDate.of(2026, 1, 1).atStartOfDay(BERLIN).toInstant(),
                LocalDate.of(2027, 1, 1).atStartOfDay(BERLIN).toInstant());
    }

    @Test void removedMemberAndReviewerDoNotEraseHistoricAudits() {
        FireQualificationCheck check = new FireQualificationCheck(42L, 70L, 5L, "AUTO_OCR_MATCH", "2026");
        when(checks.findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of(check));
        when(members.findAllById(any())).thenReturn(List.of());
        when(users.findAllById(any())).thenReturn(List.of());
        when(settings.findAll()).thenReturn(List.of());
        var report = controller.report(2026).getBody();
        assertNotNull(report);
        assertEquals("Mitglied #42", report.entries().get(0).memberName());
        assertEquals("Benutzer #5", report.entries().get(0).checkedBy());
    }

    @Test void invalidYearIsRejectedBeforeDatabaseAccess() {
        var error = assertThrows(ResponseStatusException.class, () -> controller.report(1999));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(checks, members, users, settings);
    }

    @Test void emptyYearHasEmptyEntriesButKeepsOrganisation() {
        when(checks.findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(any(), any()))
                .thenReturn(List.of());
        when(members.findAllById(any())).thenReturn(List.of());
        when(users.findAllById(any())).thenReturn(List.of());
        when(settings.findAll()).thenReturn(List.of());
        var report = controller.report(2026).getBody();
        assertNotNull(report);
        assertTrue(report.entries().isEmpty());
    }
}
