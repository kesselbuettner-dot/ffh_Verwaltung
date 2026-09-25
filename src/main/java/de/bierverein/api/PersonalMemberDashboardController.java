package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Personal dashboard: never accepts a member ID from the browser. */
@RestController
@RequestMapping("/api/me/overview")
public class PersonalMemberDashboardController {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private final AppUserRepository users;
 private final MemberExtraRepository extras;
 private final FireMemberQualificationRepository qualifications;
 private final TrainingScheduleService schedule;
 private final TrainingAttendanceRepository attendance;
 private final OrderRepository orders;

 public PersonalMemberDashboardController(AppUserRepository users,MemberExtraRepository extras,
  FireMemberQualificationRepository qualifications,TrainingScheduleService schedule,
  TrainingAttendanceRepository attendance,OrderRepository orders){
  this.users=users;this.extras=extras;this.qualifications=qualifications;this.schedule=schedule;
  this.attendance=attendance;this.orders=orders;
 }
 public record Identity(Long memberId,String name,String firstName,String lastName,String email,
   String phone,String address,LocalDate joinedOn,LocalDate birthDate,String username,String accountRole){}
 public record QualificationView(String title,String category,LocalDate issuedOn,LocalDate expiresOn,
   LocalDate nextDueOn,String status){}
 public record AppointmentView(Long eventId,LocalDate date,String title,String type,String startAt,
   boolean registrationRequired,String response){}
 public record ResponseView(LocalDate date,String status){}
 public record PurchaseView(Long id,Instant date,BigDecimal total,String status){}
 public record Overview(boolean linked,Identity member,BigDecimal balance,
   List<QualificationView> qualifications,List<AppointmentView> appointments,
   List<ResponseView> responses,List<PurchaseView> purchases,List<String> warnings){}

 @GetMapping
 @Transactional(readOnly=true)
 public Overview mine(Authentication auth){
  if(auth==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich");
  AppUser u=users.findByUsernameWithMember(auth.getName())
   .orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Benutzer nicht vorhanden"));
  if(!u.isEnabled()||!u.isRegistrationApproved())
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Konto nicht freigeschaltet");
  Member m=u.getMember();
  if(m==null)return new Overview(false,null,null,List.of(),List.of(),List.of(),List.of(),List.of());
  MemberExtra extra=extras.findById(m.getId()).orElse(null);
  Identity personal=new Identity(m.getId(),m.getName(),m.getFirstName(),m.getLastName(),m.getEmail(),
   m.getPhone(),m.getAddress(),extra==null?null:extra.joinedOn,
   extra==null?null:extra.birthDate,u.getUsername(),u.getRole().name());
  LocalDate today=LocalDate.now(ZONE);
  List<String> warnings=new ArrayList<>();
  List<QualificationView> cards=new ArrayList<>();
  try{
   for(FireMemberQualification q:qualifications.findByMemberId(m.getId())){
    if(!q.active||q.type==null)continue;
    FireQualificationType type=q.type;
    LocalDate next=type.tracked&&q.lastCheckedOn!=null&&type.intervalMonths>0
     ?q.lastCheckedOn.plusMonths(type.intervalMonths)
     :q.nextDueOn!=null?q.nextDueOn:q.expiresOn;
    String status=next==null?(type.tracked?"UNSCHEDULED":"VALID"):
     next.isBefore(today)?"OVERDUE":!next.isAfter(today.plusDays(type.warningDays))?"DUE":"VALID";
    String category="MEDICAL_DUE".equals(type.code)?"SUITABILITY":
     "DRIVERS_LICENSE".equals(type.code)?"CERTIFICATE_DOCUMENT":
     type.category==null?"QUALIFICATION":type.category;
    // Personal view exposes only the owner's qualification status; not a licence number or attached scan.
    cards.add(new QualificationView(type.title,category,q.issuedOn,q.expiresOn,next,status));
   }
   cards.sort(Comparator.comparing((QualificationView v)->"OVERDUE".equals(v.status())?0:
       "DUE".equals(v.status())?1:2).thenComparing(QualificationView::title));
  }catch(RuntimeException ex){warnings.add("Qualifikationen konnten nicht vollständig geladen werden.");}
  List<AppointmentView> events=new ArrayList<>();
  try{
   for(var occurrence:schedule.dashboard(u.getUsername())){
    events.add(new AppointmentView(occurrence.eventId(),occurrence.occurrenceDate(),
     occurrence.title(),occurrence.type(),occurrence.startAt().toString(),
     occurrence.registrationRequired(),occurrence.response()));
   }
  }catch(RuntimeException ex){warnings.add("Termine können derzeit nicht geladen werden.");}
  List<ResponseView> responses=new ArrayList<>();
  try{
   attendance.findTop25ByUsernameOrderByOccurrenceDateDesc(u.getUsername()).stream()
    .filter(item->!item.getOccurrenceDate().isBefore(today.minusYears(1)))
    .limit(12).forEach(item->responses.add(new ResponseView(item.getOccurrenceDate(),item.getStatus())));
  }catch(RuntimeException ex){warnings.add("Dienst-Rückmeldungen können derzeit nicht geladen werden.");}
  List<PurchaseView> purchases=new ArrayList<>();
  try{
   orders.findTop50ByMemberIdOrderByCreatedAtDesc(m.getId()).stream().limit(8)
    .forEach(o->purchases.add(new PurchaseView(o.getId(),o.getCreatedAt(),o.getTotal(),o.getStatus().name())));
  }catch(RuntimeException ex){warnings.add("Kontobewegungen können derzeit nicht geladen werden.");}
  return new Overview(true,personal,m.getBalance()==null?BigDecimal.ZERO:m.getBalance(),
   cards,events,responses,purchases,warnings);
 }
}
