package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Leadership reporting: service responses are distinct from verified actual presence.
 * Only YES event-presence entries with both clock times contribute to hours. */
@RestController
@RequestMapping("/api/wehrleitung/analytics")
public class LeadershipAnalyticsController {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private final TrainingScheduleService schedule;
 private final TrainingScheduleEventRepository events;
 private final TrainingAttendanceRepository replies;
 private final EventPresenceRepository presence;
 private final MemberRepository members;
 private final AppUserRepository users;
 private final EffectivePermissionService permissions;
 public LeadershipAnalyticsController(TrainingScheduleService schedule,TrainingScheduleEventRepository events,
   TrainingAttendanceRepository replies,EventPresenceRepository presence,MemberRepository members,
   AppUserRepository users,EffectivePermissionService permissions){
  this.schedule=schedule;this.events=events;this.replies=replies;this.presence=presence;
  this.members=members;this.users=users;this.permissions=permissions;
 }
 public record Slice(String label,long count){}
 public record Hours(String name,double services,double training,double events,double total){}
 public record Overview(int year,int membersTotal,long servicesTotal,long servicesWithRegistration,
   long responseOpportunities,long yes,long no,long unanswered,long responsibleAssignments,
   List<Slice> responsibles,List<Hours> topMembers,long incompleteTimeEntries,long totalPresenceEntries){}
 private record Key(Long eventId,LocalDate date){}
 private static final class Totals{long serviceMinutes,trainingMinutes,eventMinutes;}
 @GetMapping
 @Transactional(readOnly=true)
 public Overview overview(@RequestParam int year,Authentication authentication){
  AppUser actor=users.findByUsernameWithMember(authentication.getName())
    .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(!permissions.hasPermission(actor.getId(),"fire.qualifications.read"))
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Keine Berechtigung für Wehrleitungs-Auswertungen");
  int current=LocalDate.now(ZONE).getYear();
  if(year<2000||year>current+1)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiges Jahr");

  List<Member> people=members.findAll();
  Map<Long,Member> peopleById=people.stream().collect(Collectors.toMap(Member::getId,Function.identity()));
  List<Member> active=people.stream().filter(Member::isActive).toList();
  LocalDate from=LocalDate.of(year,1,1),to=LocalDate.of(year,12,31);
  var occurrences=schedule.leadershipOccurrences(actor.getUsername(),from,to);
  Map<Long,TrainingScheduleEvent> definitions=events.findAllById(
    occurrences.stream().map(TrainingScheduleService.OccurrenceView::eventId).distinct().toList())
    .stream().collect(Collectors.toMap(TrainingScheduleEvent::getId,Function.identity()));
  Map<String,Long> leaders=new HashMap<>();
  Map<Long,Totals> timeByMember=new HashMap<>();
  long services=0,registrationServices=0,yes=0,no=0,unanswered=0,incomplete=0,actualPresence=0;
  for(var item:occurrences){
   Key key=new Key(item.eventId(),item.occurrenceDate());
   TrainingScheduleEvent event=definitions.get(key.eventId());
   if(event==null)continue;
   if("SERVICE".equals(item.type())){
    services++;
    if(item.responsibleNames().isEmpty())leaders.merge("Nicht zugeordnet",1L,Long::sum);
    else item.responsibleNames().forEach(n->leaders.merge(n,1L,Long::sum));
    if(event.isRegistrationRequired()){
     registrationServices++;
     List<Member> eligible=active.stream()
       .filter(m->!"ROLE".equals(event.getAudienceType())||
         (m.getUser()!=null&&m.getUser().getRole().name().equals(event.getAudienceRole())))
       .toList();
     Set<Long> eligibleIds=eligible.stream().map(Member::getId).collect(Collectors.toSet());
     Map<Long,String> states=new HashMap<>();
     for(TrainingAttendance answer:replies.findByEventIdAndOccurrenceDate(key.eventId(),key.date())){
      Long id=answer.getMemberId();
      if(id==null){AppUser user=users.findByUsernameWithMember(answer.getUsername()).orElse(null);
       if(user!=null&&user.getMember()!=null)id=user.getMember().getId();}
      if(id!=null&&eligibleIds.contains(id))states.put(id,answer.getStatus());
     }
     for(Member m:eligible){
      String status=states.get(m.getId());
      if("YES".equals(status))yes++;
      else if("NO".equals(status))no++;
      else unanswered++;
     }
    }
   }
   for(EventPresence row:presence.findByEventIdAndOccurrenceDate(key.eventId(),key.date())){
    if(!"YES".equals(row.participation))continue;
    actualPresence++;
    if(row.arrivedAt==null||row.leftAt==null){incomplete++;continue;}
    long minutes=Duration.between(row.arrivedAt,row.leftAt).toMinutes();
    if(minutes<=0){incomplete++;continue;}
    Totals t=timeByMember.computeIfAbsent(row.memberId,id->new Totals());
    switch(item.type()){
     case "SERVICE" -> t.serviceMinutes+=minutes;
     case "TRAINING" -> t.trainingMinutes+=minutes;
     case "EVENT" -> t.eventMinutes+=minutes;
     default -> {}
    }
   }
  }
  List<Slice> responsibles=leaders.entrySet().stream()
    .sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
    .map(e->new Slice(e.getKey(),e.getValue())).toList();
  List<Hours> top=timeByMember.entrySet().stream().map(e->{
    Totals t=e.getValue();Member m=peopleById.get(e.getKey());
    return new Hours(m==null?"Mitglied #"+e.getKey():m.getName(),
      hours(t.serviceMinutes),hours(t.trainingMinutes),hours(t.eventMinutes),
      hours(t.serviceMinutes+t.trainingMinutes+t.eventMinutes));
   }).filter(e->e.total()>0)
   .sorted(Comparator.comparingDouble(Hours::total).reversed().thenComparing(Hours::name))
   .limit(10).toList();
  return new Overview(year,active.size(),services,registrationServices,yes+no+unanswered,
    yes,no,unanswered,responsibles.stream().mapToLong(Slice::count).sum(),
    responsibles,top,incomplete,actualPresence);
 }
 private static double hours(long minutes){return Math.round(minutes/60.0*100.0)/100.0;}
}
