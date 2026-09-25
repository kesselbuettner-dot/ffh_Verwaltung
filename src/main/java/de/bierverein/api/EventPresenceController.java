package de.bierverein.api;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api/event-presence")
@PreAuthorize("hasAnyRole('ADMIN','VORSTAND')")
public class EventPresenceController {
 private final EventPresenceRepository records;
 private final TrainingScheduleService schedule;
 private final MemberRepository members;
 public EventPresenceController(EventPresenceRepository records,TrainingScheduleService schedule,MemberRepository members){
  this.records=records;this.schedule=schedule;this.members=members;
 }
 public record Row(Long memberId,String name,String participation,LocalTime arrivedAt,LocalTime leftAt){}
 public record View(Long eventId,LocalDate date,String title,String type,Instant startAt,Instant endAt,List<Row> members){}
 public record Input(String participation,LocalTime arrivedAt,LocalTime leftAt){}
 private TrainingScheduleService.OccurrenceView occurrence(Long id,LocalDate date,Authentication auth){
  if(id==null||date==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Veranstaltung und Datum erforderlich");
  return schedule.list(auth.getName(),date,date).occurrences().stream()
    .filter(o->id.equals(o.eventId())&&date.equals(o.occurrenceDate()))
    .findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Termin nicht gefunden oder nicht freigegeben"));
 }
 @GetMapping("/{id}/{date}") @Transactional(readOnly=true)
 public View view(@PathVariable Long id,@PathVariable LocalDate date,Authentication auth){
  var event=occurrence(id,date,auth);
  Map<Long,EventPresence> byMember=new HashMap<>();
  records.findByEventIdAndOccurrenceDate(id,date).forEach(r->byMember.put(r.memberId,r));
  List<Row> rows=members.findAll().stream().filter(Member::isActive)
    .sorted(Comparator.comparing(Member::getName,String.CASE_INSENSITIVE_ORDER))
    .map(m->{var r=byMember.get(m.getId());return new Row(m.getId(),m.getName(),
        r==null?"OPEN":r.participation,r==null?null:r.arrivedAt,r==null?null:r.leftAt);}).toList();
  return new View(id,date,event.title(),event.type(),event.startAt(),event.endAt(),rows);
 }
 @PutMapping("/{id}/{date}/{memberId}") @Transactional
 public Row update(@PathVariable Long id,@PathVariable LocalDate date,@PathVariable Long memberId,@RequestBody Input input,Authentication auth){
  occurrence(id,date,auth);
  Member member=members.findById(memberId).filter(Member::isActive)
    .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied nicht gefunden"));
  String status=input==null||input.participation()==null?"OPEN":input.participation().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("OPEN","YES","NO").contains(status))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte Ja, Nein oder Offen auswählen");
  LocalTime from="YES".equals(status)?input.arrivedAt():null;
  LocalTime until="YES".equals(status)?input.leftAt():null;
  if(from!=null&&until!=null&&until.isBefore(from))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ende muss nach Beginn liegen");
  EventPresence record=records.findByEventIdAndOccurrenceDateAndMemberId(id,date,memberId)
    .orElseGet(()->new EventPresence(id,date,memberId));
  record.participation=status;record.arrivedAt=from;record.leftAt=until;
  record.updatedBy=auth.getName();record.updatedAt=Instant.now();
  records.save(record);
  return new Row(member.getId(),member.getName(),status,from,until);
 }
}
