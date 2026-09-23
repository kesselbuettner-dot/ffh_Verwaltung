package de.bierverein.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Annual audit: historical checks, never current due dates or document/OCR contents. */
@RestController
@RequestMapping("/api/fire/qualifications/driving")
public class DrivingYearReportController {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Europe/Berlin");

    private final FireQualificationCheckRepository checks;
    private final MemberRepository members;
    private final AppUserRepository users;
    private final AppSettingsRepository settings;

    public DrivingYearReportController(FireQualificationCheckRepository checks,
                                       MemberRepository members, AppUserRepository users,
                                       AppSettingsRepository settings) {
        this.checks = checks;
        this.members = members;
        this.users = users;
        this.settings = settings;
    }

    public record Organisation(String name, String street, String postalCode, String city,
                               String email, String phone, String legalRepresentative,
                               boolean logoAvailable) {}
    public record Entry(Long id, Long memberId, String memberName, Instant checkedAt,
                        String checkedBy, String method, String result) {}
    public record AnnualReport(int year, Organisation organisation, List<Entry> entries) {}

    @GetMapping("/report")
    @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.read')")
    @Transactional(readOnly = true)
    public ResponseEntity<AnnualReport> report(@RequestParam int year) {
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte ein gültiges Berichtsjahr wählen.");
        }
        Instant from = LocalDate.of(year, 1, 1).atStartOfDay(REPORT_ZONE).toInstant();
        Instant until = LocalDate.of(year + 1, 1, 1).atStartOfDay(REPORT_ZONE).toInstant();
        List<FireQualificationCheck> history =
                checks.findByCheckedAtGreaterThanEqualAndCheckedAtLessThanOrderByCheckedAtAsc(from, until);

        // Resolve recorded IDs against current names, retaining audit lines for deleted accounts.
        Map<Long, Member> memberIndex = members.findAllById(
                history.stream().map(c -> c.memberId).distinct().toList()).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, AppUser> userIndex = users.findAllById(
                history.stream().map(c -> c.checkedByUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));

        List<Entry> entries = history.stream().map(c -> {
            Member member = memberIndex.get(c.memberId);
            AppUser checker = userIndex.get(c.checkedByUserId);
            String memberName = member == null ? "Mitglied #" + c.memberId : member.getName();
            String checkedBy = checker == null ? "Benutzer #" + c.checkedByUserId
                    : checker.getUsername();
            return new Entry(c.id, c.memberId, memberName, c.checkedAt,
                    checkedBy, c.method, c.result);
        }).toList();

        AppSettings s = settings.findAll().stream().findFirst().orElseGet(AppSettings::new);
        Organisation org = new Organisation(s.getOrganizationName(), s.getStreet(),
                s.getPostalCode(), s.getCity(), s.getContactEmail(), s.getContactPhone(),
                s.getLegalRepresentative(), s.getLogoData() != null && s.getLogoData().length > 0);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AnnualReport(year, org, entries));
    }
}
