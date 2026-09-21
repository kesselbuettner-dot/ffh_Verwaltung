package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingScheduleService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Set<String> TYPES = Set.of("SERVICE", "TRAINING", "EVENT");
    private final TrainingScheduleEventRepository events;
    private final TrainingAttendanceRepository attendance;
    private final TrainingSeriesExceptionRepository seriesExceptions;
    private final AppUserRepository users;
    private final MemberRepository members;
    private final EffectivePermissionService permissions;
    private final DeviceRepository devices;
    private final DeviceInspectionTaskRepository inspectionTasks;
    private final HolidayService holidays;

    public TrainingScheduleService(TrainingScheduleEventRepository events,
                                   TrainingAttendanceRepository attendance,
                                   TrainingSeriesExceptionRepository seriesExceptions,
                                   AppUserRepository users,
                                   MemberRepository members,
                                   EffectivePermissionService permissions,DeviceRepository devices,
                                   DeviceInspectionTaskRepository inspectionTasks,HolidayService holidays) {
        this.events = events;
        this.attendance = attendance;
        this.seriesExceptions = seriesExceptions;
        this.users = users;
        this.members = members;
        this.permissions = permissions;
        this.devices=devices;this.inspectionTasks=inspectionTasks;this.holidays=holidays;
    }

    @Transactional(readOnly = true)
    public ModuleView list(String username, LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        AppUser user = user(username);
        List<Member> allMembers = members.findAll();
        Map<Long, Member> memberMap = allMembers.stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        List<TrainingScheduleEvent> matching = events.findByActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateAsc(range.to(), range.from());
        List<TrainingScheduleEvent> visible = matching.stream().filter(e -> visibleTo(e, user)).toList();
        List<EventView> definitions = visible.stream().map(e -> eventView(e, user)).toList();
        List<OccurrenceView> occurrences = visible.stream().flatMap(e -> occurrenceDates(e, range.from(), range.to()).stream()
                .map(date -> occurrenceView(e, date, user, memberMap))).sorted(Comparator.comparing(OccurrenceView::startAt)).toList();
        Map<String, Boolean> canCreate = new LinkedHashMap<>();
        for (String type : TYPES) canCreate.put(type, can(user, type, "write"));
        boolean anyWrite = canCreate.values().stream().anyMatch(Boolean.TRUE::equals);
        List<MemberOption> memberOptions = anyWrite ? allMembers.stream().filter(Member::isActive)
                .sorted(Comparator.comparing(Member::getName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new MemberOption(m.getId(), m.getName())).toList() : List.of();
        List<RoleOption> roleOptions = anyWrite ? Arrays.stream(Role.values())
                .map(r -> new RoleOption(r.name(), roleLabel(r))).toList() : List.of();
        List<DeviceOption> deviceOptions=anyWrite?devices.findByActiveTrueOrderByNameAsc().stream()
                .filter(Device::isInspectionRequired).filter(d->deviceLocation(d)!=null&&d.getCategory()!=null).map(d->new DeviceOption(deviceLocation(d),d.getCategory())).distinct().toList():List.of();
        return new ModuleView(definitions, occurrences, canCreate, memberOptions, roleOptions,deviceOptions);
    }

    @Transactional(readOnly = true)
    public List<OccurrenceView> dashboard(String username) {
        LocalDate today = LocalDate.now(ZONE);
        return list(username, today, today.plusDays(90)).occurrences().stream().limit(8).toList();
    }

    @Transactional
    public EventView create(String username, EventRequest request) {
        AppUser user = user(username);
        String type = normalizeType(request == null ? null : request.type());
        require(user, type, "write");
        TrainingScheduleEvent event = new TrainingScheduleEvent();
        event.setCreatedBy(username);
        apply(event, request, type);
        if (event.isRecurring() && Boolean.TRUE.equals(request.separateOccurrences())) {
            List<LocalDate> dates = occurrenceDates(event, event.getStartDate(), event.getEndDate());
            if (dates.isEmpty()) throw bad("Die Serie enthält keine Termine. Bitte die Wochentage prüfen.");
            if (dates.size() > 400) throw bad("Für Einzeltermine sind höchstens 400 Termine pro Serie zulässig.");
            TrainingScheduleEvent first = null;
            for (LocalDate date : dates) {
                TrainingScheduleEvent single = copyForDate(event, date, username);
                TrainingScheduleEvent saved = events.save(single);
                if (first == null) first = saved;
            }
            return eventView(first, user);
        }
        return eventView(events.save(event), user);
    }

    @Transactional
    public EventView update(String username, Long id, EventRequest request) {
        AppUser user = user(username);
        TrainingScheduleEvent event = find(id);
        require(user, event.getType(), "write");
        String type = normalizeType(request == null ? null : request.type());
        require(user, type, "write");
        LocalDate originalDate = event.isRecurring() ? null : event.getStartDate();
        apply(event, request, type);
        event.touch();
        EventView saved = eventView(events.save(event), user);
        if (originalDate != null && !event.isRecurring() && !originalDate.equals(event.getStartDate()))
            moveResponsesAndTasks(id, originalDate, id, event.getStartDate());
        return saved;
    }

    @Transactional
    public void delete(String username, Long id) {
        AppUser user = user(username);
        TrainingScheduleEvent event = find(id);
        require(user, event.getType(), "delete");
        attendance.deleteByEventId(id);
        inspectionTasks.deleteByEventId(id);
        seriesExceptions.deleteBySeriesEventId(id);
        events.delete(event);
    }

    /** Separate one occurrence from its series and preserve all registrations and inspection work. */
    @Transactional
    public EventView updateOccurrence(String username, Long seriesId, LocalDate originalDate, EventRequest request) {
        AppUser user = user(username);
        TrainingScheduleEvent series = find(seriesId);
        require(user, series.getType(), "write");
        if (!series.isRecurring() || originalDate == null || !occursOn(series, originalDate))
            throw bad("Dieser Einzeltermin gehört nicht zu einer aktiven Serie.");
        if (request == null || Boolean.TRUE.equals(request.recurring()))
            throw bad("Einzeltermine dürfen keine eigene Serie enthalten.");
        String type = normalizeType(request.type());
        if (!series.getType().equals(type)) throw bad("Der Termintyp der Serie darf beim Einzeltermin nicht geändert werden.");
        TrainingScheduleEvent single = new TrainingScheduleEvent();
        single.setCreatedBy(username);
        apply(single, request, type);
        if (single.isRecurring()) throw bad("Bitte einen einzelnen Tag auswählen.");
        single = events.saveAndFlush(single);
        seriesExceptions.save(new TrainingSeriesException(seriesId, originalDate, single.getId()));
        moveResponsesAndTasks(seriesId, originalDate, single.getId(), single.getStartDate());
        return eventView(single, user);
    }

    private void moveResponsesAndTasks(Long oldId, LocalDate oldDate, Long newId, LocalDate newDate) {
        for (TrainingAttendance response : attendance.findByEventIdAndOccurrenceDate(oldId, oldDate)) {
            response.setEventId(newId); response.setOccurrenceDate(newDate);
            attendance.save(response);
        }
        for (DeviceInspectionTask task : inspectionTasks.findByEventIdAndOccurrenceDateOrderByDeviceNameAsc(oldId, oldDate)) {
            task.setEventId(newId); task.setOccurrenceDate(newDate);
            inspectionTasks.save(task);
        }
    }

    private TrainingScheduleEvent copyForDate(TrainingScheduleEvent template, LocalDate date, String username) {
        TrainingScheduleEvent single = new TrainingScheduleEvent();
        single.setCreatedBy(username);
        single.setType(template.getType()); single.setTitle(template.getTitle()); single.setNotes(template.getNotes());
        single.setStartDate(date); single.setEndDate(date); single.setRecurring(false);
        single.setWeekdays(date.getDayOfWeek().name()); single.setLastWeekdayOfMonth(false);
        single.setAllDay(template.isAllDay()); single.setStartTime(template.getStartTime()); single.setEndTime(template.getEndTime());
        single.setResponsibleMemberIds(template.getResponsibleMemberIds());
        single.setAudienceType(template.getAudienceType()); single.setAudienceRole(template.getAudienceRole());
        single.setRegistrationRequired(template.isRegistrationRequired());
        single.setDeviceInspection(template.isDeviceInspection());
        single.setDeviceLocations(template.getDeviceLocations()); single.setDeviceCategories(template.getDeviceCategories());
        return single;
    }

    @Transactional
    public OccurrenceView respond(String username, Long id, LocalDate date, String requestedStatus) {
        AppUser user = user(username);
        TrainingScheduleEvent event = find(id);
        String status = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("YES", "NO").contains(status)) throw bad("Bitte Zu- oder Absage auswählen.");
        if (date == null || !occursOn(event, date)) throw bad("Dieser Termin findet am gewählten Tag nicht statt.");
        if (!event.isRegistrationRequired()) throw bad("Für diesen Termin ist keine Anmeldung vorgesehen.");
        if (!audienceMatches(event, user) && !isResponsible(event, user)) throw forbidden("Dieser Termin ist nicht für deine Rolle freigegeben.");
        TrainingAttendance answer = attendance.findByEventIdAndOccurrenceDateAndUsername(id, date, username)
                .orElseGet(() -> new TrainingAttendance(id, date, username, user.getMember() == null ? null : user.getMember().getId(), status));
        answer.setStatus(status);
        attendance.save(answer);
        Map<Long, Member> memberMap = members.findAll().stream().collect(Collectors.toMap(Member::getId, Function.identity()));
        return occurrenceView(event, date, user, memberMap);
    }

    @Transactional(readOnly = true)
    public List<OccurrenceView> calendarEntries(String username, LocalDate from, LocalDate to) {
        return list(username, from, to).occurrences();
    }

    private void apply(TrainingScheduleEvent event, EventRequest r, String type) {
        if (r == null || r.title() == null || r.title().isBlank()) throw bad("Thema ist erforderlich.");
        if (r.startDate() == null) throw bad("Startdatum ist erforderlich.");
        boolean recurring = Boolean.TRUE.equals(r.recurring());
        LocalDate endDate = recurring ? r.endDate() : r.startDate();
        if (endDate == null || endDate.isBefore(r.startDate())) throw bad("Das Serienende muss am oder nach dem Startdatum liegen.");
        if (ChronoUnit.DAYS.between(r.startDate(), endDate) > 1095) throw bad("Eine Terminserie darf höchstens drei Jahre umfassen.");
        Set<DayOfWeek> weekdays = new LinkedHashSet<>();
        if (recurring && r.weekdays() != null) for (String value : r.weekdays()) {
            try { weekdays.add(DayOfWeek.valueOf(value)); } catch (Exception ignored) {}
        }
        if (recurring && weekdays.isEmpty()) throw bad("Für eine Terminserie muss mindestens ein Wochentag gewählt werden.");
        if (!recurring) weekdays.add(r.startDate().getDayOfWeek());
        boolean allDay = Boolean.TRUE.equals(r.allDay());
        if (!allDay && (r.startTime() == null || r.endTime() == null || !r.endTime().isAfter(r.startTime()))) {
            throw bad("Bitte eine gültige Uhrzeit von/bis angeben.");
        }
        String audienceType = Optional.ofNullable(r.audienceType()).orElse("ALL").trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "ROLE").contains(audienceType)) throw bad("Ungültige Teilnehmerauswahl.");
        String audienceRole = null;
        if ("ROLE".equals(audienceType)) {
            try { audienceRole = Role.valueOf(Optional.ofNullable(r.audienceRole()).orElse("").toUpperCase(Locale.ROOT)).name(); }
            catch (Exception e) { throw bad("Bitte eine gültige Teilnehmerrolle auswählen."); }
        }
        List<Long> responsibleIds = Optional.ofNullable(r.responsibleMemberIds()).orElse(List.of()).stream()
                .filter(Objects::nonNull).distinct().toList();
        Set<Long> knownIds = members.findAllById(responsibleIds).stream().map(Member::getId).collect(Collectors.toSet());
        if (knownIds.size() != responsibleIds.size()) throw bad("Mindestens ein ausgewähltes Mitglied wurde nicht gefunden.");
        event.setType(type);
        event.setTitle(trim(r.title(), 160));
        event.setNotes(trim(r.notes(), 3000));
        event.setStartDate(r.startDate()); event.setEndDate(endDate); event.setRecurring(recurring);
        event.setWeekdays(weekdays.stream().map(Enum::name).collect(Collectors.joining(",")));
        event.setAllDay(allDay); event.setStartTime(allDay ? null : r.startTime()); event.setEndTime(allDay ? null : r.endTime());
        event.setResponsibleMemberIds(responsibleIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        event.setAudienceType(audienceType); event.setAudienceRole(audienceRole);
        event.setRegistrationRequired(Boolean.TRUE.equals(r.registrationRequired())); event.setActive(true);
        event.setLastWeekdayOfMonth(recurring&&Boolean.TRUE.equals(r.lastWeekdayOfMonth()));
        boolean deviceInspection="SERVICE".equals(type)&&Boolean.TRUE.equals(r.deviceInspection());
        List<String> locations=cleanValues(r.deviceLocations()),categories=cleanValues(r.deviceCategories());
        if(deviceInspection&&(locations.isEmpty()||categories.isEmpty()))throw bad("Für eine Geräteprüfung bitte mindestens einen Standort und eine Gerätekategorie auswählen.");
        event.setDeviceInspection(deviceInspection);event.setDeviceLocations(String.join(",",locations));event.setDeviceCategories(String.join(",",categories));
    }

    private OccurrenceView occurrenceView(TrainingScheduleEvent event, LocalDate date, AppUser user, Map<Long, Member> memberMap) {
        ZonedDateTime start = event.isAllDay() ? date.atStartOfDay(ZONE) : date.atTime(event.getStartTime()).atZone(ZONE);
        ZonedDateTime end = event.isAllDay() ? date.plusDays(1).atStartOfDay(ZONE) : date.atTime(event.getEndTime()).atZone(ZONE);
        List<Long> responsibleIds = ids(event.getResponsibleMemberIds());
        List<String> responsibleNames = responsibleIds.stream().map(memberMap::get).filter(Objects::nonNull).map(Member::getName).toList();
        List<TrainingAttendance> answers = event.isRegistrationRequired() ? attendance.findByEventIdAndOccurrenceDate(event.getId(), date) : List.of();
        String own = answers.stream().filter(a -> a.getUsername().equals(user.getUsername())).map(TrainingAttendance::getStatus).findFirst().orElse(null);
        boolean manager = can(user, event.getType(), "write");
        boolean responsible = isResponsible(event, user);
        boolean showNames = manager || responsible;
        List<String> yesNames = showNames ? answerNames(answers, "YES", memberMap) : List.of();
        List<String> noNames = showNames ? answerNames(answers, "NO", memberMap) : List.of();
        return new OccurrenceView(event.getId(), date, event.getType(), event.getTitle(), event.getNotes(), event.isAllDay(),
                event.getStartTime(), event.getEndTime(), start.toInstant(), end.toInstant(), event.isRecurring(), responsibleNames,
                "ROLE".equals(event.getAudienceType()) ? roleLabel(Role.valueOf(event.getAudienceRole())) : "Alle Mitglieder",
                event.isRegistrationRequired(), own,
                (int) answers.stream().filter(a -> "YES".equals(a.getStatus())).count(),
                (int) answers.stream().filter(a -> "NO".equals(a.getStatus())).count(),
                yesNames, noNames, manager, event.isRegistrationRequired() && (audienceMatches(event, user) || responsible),
                holidays.name(date).orElse(null),event.isDeviceInspection());
    }

    private List<String> answerNames(List<TrainingAttendance> answers, String status, Map<Long, Member> memberMap) {
        return answers.stream().filter(a -> status.equals(a.getStatus())).map(a -> {
            Member member = a.getMemberId() == null ? null : memberMap.get(a.getMemberId());
            return member == null ? a.getUsername() : member.getName();
        }).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private EventView eventView(TrainingScheduleEvent e, AppUser user) {
        return new EventView(e.getId(), e.getType(), e.getTitle(), e.getNotes(), e.getStartDate(), e.getEndDate(), e.isRecurring(),
                split(e.getWeekdays()), e.isAllDay(), e.getStartTime(), e.getEndTime(), ids(e.getResponsibleMemberIds()),
                e.getAudienceType(), e.getAudienceRole(), e.isRegistrationRequired(), can(user, e.getType(), "write"), can(user, e.getType(), "delete"),
                e.isLastWeekdayOfMonth(),e.isDeviceInspection(),split(e.getDeviceLocations()),split(e.getDeviceCategories()));
    }

    private boolean visibleTo(TrainingScheduleEvent event, AppUser user) { return can(user,event.getType(),"read") && (can(user,event.getType(),"write") || audienceMatches(event,user) || isResponsible(event,user)); }
    private boolean audienceMatches(TrainingScheduleEvent event, AppUser user) { return "ALL".equals(event.getAudienceType()) || user.getRole().name().equals(event.getAudienceRole()); }
    private boolean isResponsible(TrainingScheduleEvent event, AppUser user) { return user.getMember()!=null && ids(event.getResponsibleMemberIds()).contains(user.getMember().getId()); }
    public boolean occursOn(TrainingScheduleEvent event, LocalDate date) {
        return matchesPattern(event,date) && (event.getId()==null || !seriesExceptions.existsBySeriesEventIdAndOccurrenceDate(event.getId(),date));
    }
    private boolean matchesPattern(TrainingScheduleEvent e, LocalDate date) {
        return !date.isBefore(e.getStartDate()) && !date.isAfter(e.getEndDate()) &&
                (!e.isRecurring() ? date.equals(e.getStartDate()) : split(e.getWeekdays()).contains(date.getDayOfWeek().name()) &&
                (!e.isLastWeekdayOfMonth() || date.plusWeeks(1).getMonth()!=date.getMonth()));
    }
    private List<LocalDate> occurrenceDates(TrainingScheduleEvent e, LocalDate from, LocalDate to) {
        Set<LocalDate> excluded=e.getId()==null?Set.of():seriesExceptions.findBySeriesEventId(e.getId()).stream().map(TrainingSeriesException::getOccurrenceDate).collect(Collectors.toSet());
        List<LocalDate> result=new ArrayList<>();
        LocalDate date=e.getStartDate().isAfter(from)?e.getStartDate():from;
        LocalDate end=e.getEndDate().isBefore(to)?e.getEndDate():to;
        for(;!date.isAfter(end);date=date.plusDays(1))if(matchesPattern(e,date)&&!excluded.contains(date))result.add(date);
        return result;
    }
    private boolean can(AppUser user,String type,String action){return permissions.hasPermission(user.getId(),permissionArea(type)+"."+action);}
    private void require(AppUser user,String type,String action){if(!can(user,type,action))throw forbidden("Keine Berechtigung für diesen Terminbereich.");}
    private String permissionArea(String type){return switch(type){case "SERVICE"->"training.services";case "TRAINING"->"training.courses";case "EVENT"->"events";default->throw bad("Ungültiger Termintyp.");};}
    private AppUser user(String username){return users.findByUsernameWithMember(username).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));}
    private TrainingScheduleEvent find(Long id){return events.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Termin nicht gefunden."));}
    private String normalizeType(String value){String type=Optional.ofNullable(value).orElse("").trim().toUpperCase(Locale.ROOT);if(!TYPES.contains(type))throw bad("Ungültiger Termintyp.");return type;}
    private DateRange range(LocalDate from,LocalDate to){LocalDate start=from==null?LocalDate.now(ZONE).minusDays(30):from;LocalDate end=to==null?start.plusYears(1):to;if(end.isBefore(start)||ChronoUnit.DAYS.between(start,end)>1095)throw bad("Ungültiger Kalenderzeitraum.");return new DateRange(start,end);}
    private List<Long> ids(String value){if(value==null||value.isBlank())return List.of();return Arrays.stream(value.split(",")).map(String::trim).filter(v->!v.isBlank()).map(Long::valueOf).distinct().toList();}
    private List<String> split(String value){if(value==null||value.isBlank())return List.of();return Arrays.stream(value.split(",")).map(String::trim).filter(v->!v.isBlank()).distinct().toList();}
    private String trim(String value,int max){if(value==null||value.isBlank())return null;String v=value.trim();return v.substring(0,Math.min(max,v.length()));}
    private List<String> cleanValues(List<String> values){return Optional.ofNullable(values).orElse(List.of()).stream().filter(Objects::nonNull).map(String::trim).filter(v->!v.isBlank()).distinct().toList();}
    private String deviceLocation(Device d){return d.getCompartment()!=null?d.getCompartment().getVehicle().getName():d.getLocation();}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);} private ResponseStatusException forbidden(String message){return new ResponseStatusException(HttpStatus.FORBIDDEN,message);}
    private String roleLabel(Role role){return switch(role){case ADMIN->"Administrator";case VORSTAND->"Vorstand";case KASSENWART->"Kassenwart";case FEUERWEHRWART->"Feuerwehrwart";case GERATEWART->"Gerätewart";case GETRAENKEWART->"Getränkewart";case THEKE->"Theke";case MEMBER->"Mitglied";};}

    private record DateRange(LocalDate from,LocalDate to){}
    public record EventRequest(String type,String title,String notes,LocalDate startDate,LocalDate endDate,Boolean recurring,List<String> weekdays,Boolean allDay,LocalTime startTime,LocalTime endTime,List<Long> responsibleMemberIds,String audienceType,String audienceRole,Boolean registrationRequired,Boolean lastWeekdayOfMonth,Boolean deviceInspection,List<String> deviceLocations,List<String> deviceCategories,Boolean separateOccurrences){}
    public record EventView(Long id,String type,String title,String notes,LocalDate startDate,LocalDate endDate,boolean recurring,List<String> weekdays,boolean allDay,LocalTime startTime,LocalTime endTime,List<Long> responsibleMemberIds,String audienceType,String audienceRole,boolean registrationRequired,boolean canEdit,boolean canDelete,boolean lastWeekdayOfMonth,boolean deviceInspection,List<String> deviceLocations,List<String> deviceCategories){}
    public record OccurrenceView(Long eventId,LocalDate occurrenceDate,String type,String title,String notes,boolean allDay,LocalTime startTime,LocalTime endTime,Instant startAt,Instant endAt,boolean recurring,List<String> responsibleNames,String audienceLabel,boolean registrationRequired,String response,int yesCount,int noCount,List<String> yesNames,List<String> noNames,boolean canManage,boolean canRespond,String holidayName,boolean deviceInspection){}
    public record MemberOption(Long id,String name){} public record RoleOption(String code,String name){}
    public record DeviceOption(String location,String category){}
    public record ModuleView(List<EventView> definitions,List<OccurrenceView> occurrences,Map<String,Boolean> canCreate,List<MemberOption> members,List<RoleOption> roles,List<DeviceOption> deviceOptions){}
}
