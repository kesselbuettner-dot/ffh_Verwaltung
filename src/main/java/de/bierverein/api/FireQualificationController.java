package de.bierverein.api;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/fire/qualifications")
public class FireQualificationController {
 private final FireQualificationTypeRepository types;
 private final FireMemberQualificationRepository records;
 private final MemberRepository members;
 private final FireQualificationPermissions permission;
 private final DrivingCheckService driving;
 private final ManagedUserRoleRepository managedRoles;
 public FireQualificationController(FireQualificationTypeRepository types,FireMemberQualificationRepository records,
     MemberRepository members,FireQualificationPermissions permission,DrivingCheckService driving,ManagedUserRoleRepository managedRoles){
  this.types=types;this.records=records;this.members=members;this.permission=permission;this.driving=driving;this.managedRoles=managedRoles;
 }
 public record TypeView(Long id,String code,String title,String icon,boolean tracked,boolean sensitive,int warningDays,int intervalMonths){}
 public record TypeInput(String code,String title,String icon,Boolean tracked,Boolean sensitive,Integer warningDays,Integer intervalMonths){}
 public record AssignmentInput(Long typeId,LocalDate issuedOn,LocalDate expiresOn,LocalDate nextDueOn,
   Boolean active,String licenseNumber){}
 public record Card(Long id,Long memberId,String memberName,String code,String title,String icon,
   LocalDate issuedOn,LocalDate expiresOn,LocalDate lastCheckedOn,LocalDate nextDueOn,String status,boolean referencePresent){}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private TypeView view(FireQualificationType t){return new TypeView(t.id,t.code,t.title,t.icon,t.tracked,t.sensitive,t.warningDays,t.intervalMonths);}
 private int range(Integer value,int max,int otherwise){int n=value==null?otherwise:value;if(n<0||n>max)throw bad("Intervall außerhalb des gültigen Bereichs");return n;}
 private String required(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw bad("Pflichtfeld fehlt oder ist zu lang");return s.trim();}
 private boolean canSensitive(Authentication auth){return permission.allowed(auth,"fire.qualifications.sensitive.read");}
 private FireMemberQualification record(Long id){return records.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Qualifikation nicht gefunden"));}
 private Card card(FireMemberQualification q){
  Member m=members.findById(q.memberId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied fehlt"));
  LocalDate due=due(q),now=LocalDate.now();
  String state=!q.active?"INACTIVE":due==null?(q.type.tracked?"UNSCHEDULED":"VALID"):
    due.isBefore(now)?"OVERDUE":!due.isAfter(now.plusDays(q.type.warningDays))?"DUE":"VALID";
  return new Card(q.id,q.memberId,m.getName(),q.type.code,q.type.title,q.type.icon,
   q.issuedOn,q.expiresOn,q.lastCheckedOn,due,state,q.licenseNumberMac!=null);
 }
 private LocalDate due(FireMemberQualification q){
  if(q.type.tracked&&q.lastCheckedOn!=null&&q.type.intervalMonths>0)
   return q.lastCheckedOn.plusMonths(q.type.intervalMonths);
  return q.nextDueOn!=null?q.nextDueOn:q.expiresOn;
 }
 public record Person(Long id,String name) {}
 @GetMapping("/people")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public List<Person> people(){
  return members.findAll().stream().filter(Member::isActive).map(m->new Person(m.getId(),m.getName()))
   .sorted(Comparator.comparing(Person::name)).toList();
 }
 public record DueSummary(long due,long overdue){}
 @GetMapping("/dashboard")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public ResponseEntity<DueSummary> dashboard(Authentication auth){
  Long uid=permission.userId(auth);
  // Display warnings ONLY for explicit WEHRLEITER role holders, not every admin.
  if(uid==null||managedRoles.findByUserId(uid).stream().noneMatch(a->"WEHRLEITER".equals(a.getRole().getCode())))
   return ResponseEntity.noContent().build();
  LocalDate now=LocalDate.now();long due=0,overdue=0;
  for(FireMemberQualification q:records.findAll()){
   if(!q.active||!q.type.tracked||(q.type.sensitive&&!canSensitive(auth)))continue;
   LocalDate date=due(q);if(date==null)continue;
   if(date.isBefore(now))overdue++;else if(!date.isAfter(now.plusDays(q.type.warningDays)))due++;
  }
  return ResponseEntity.ok(new DueSummary(due,overdue));
 }
 @GetMapping("/types")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public List<TypeView> types(){return types.findAll().stream().sorted(Comparator.comparing(t->t.title)).map(this::view).toList();}
 @PostMapping("/types/defaults")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public List<TypeView> defaults(){
  defaultType("DRIVERS_LICENSE","Führerschein","🚘",true,false,30,6);
  defaultType("CHAINSAW","Motorkettenschein","🪚",false,false,30,0);
  defaultType("MEDICAL_DUE","Untersuchung (Fälligkeit)","🩺",true,true,30,12);
  defaultType("RETRAINING","Ausbildungswiederholung","🎓",true,false,30,12);
  return types();
 }
 private void defaultType(String code,String title,String icon,boolean tracked,boolean sensitive,int warn,int months){
  if(types.findByCode(code).isEmpty())types.save(new FireQualificationType(code,title,icon,tracked,sensitive,warn,months));
 }
 @PostMapping("/types")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public TypeView createType(@RequestBody TypeInput input){
  if(input==null)throw bad("Qualifikationstyp fehlt");
  String code=required(input.code(),64).toUpperCase(Locale.ROOT);
  if(!code.matches("[A-Z][A-Z0-9_]{0,63}")||types.findByCode(code).isPresent())throw bad("Kennung ungültig oder vorhanden");
  var t=new FireQualificationType(code,required(input.title(),120),
      input.icon()==null?"📋":input.icon(),Boolean.TRUE.equals(input.tracked()),
      Boolean.TRUE.equals(input.sensitive()),range(input.warningDays(),365,30),range(input.intervalMonths(),120,0));
  return view(types.save(t));
 }
 @PutMapping("/types/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public TypeView updateType(@PathVariable Long id,@RequestBody TypeInput input){
  if(input==null)throw bad("Daten fehlen");
  FireQualificationType t=types.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  t.title=required(input.title(),120);if(input.icon()!=null&&input.icon().length()<=20)t.icon=input.icon();
  t.tracked=Boolean.TRUE.equals(input.tracked());t.sensitive=Boolean.TRUE.equals(input.sensitive());
  t.warningDays=range(input.warningDays(),365,t.warningDays);t.intervalMonths=range(input.intervalMonths(),120,t.intervalMonths);
  return view(types.save(t));
 }
 @GetMapping("/cards")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public List<Card> cards(Authentication auth,@RequestParam(required=false) Long memberId){
  return (memberId==null?records.findAll():records.findByMemberId(memberId)).stream()
   .filter(q->!q.type.sensitive||canSensitive(auth)).map(this::card)
   .sorted(Comparator.comparing(Card::memberName).thenComparing(Card::title)).toList();
 }
 @PostMapping("/members/{memberId}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public Card assign(@PathVariable Long memberId,@RequestBody AssignmentInput input,Authentication auth){
  if(input==null||input.typeId()==null)throw bad("Qualifikation fehlt");
  if(members.findById(memberId).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied nicht gefunden");
  FireQualificationType type=types.findById(input.typeId()).orElseThrow(()->bad("Qualifikationstyp unbekannt"));
  if(type.sensitive&&!canSensitive(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(records.findByMemberIdAndTypeCode(memberId,type.code).isPresent())
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Qualifikation bereits zugewiesen");
  FireMemberQualification q=new FireMemberQualification(memberId,type);fill(q,input,true);
  return card(records.save(q));
 }
 @PutMapping("/cards/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public Card updateCard(@PathVariable Long id,@RequestBody AssignmentInput input,Authentication auth){
  if(input==null)throw bad("Daten fehlen");
  FireMemberQualification q=record(id);
  if(q.type.sensitive&&!canSensitive(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(input.typeId()!=null&&!input.typeId().equals(q.type.id))throw bad("Qualifikationstyp kann nicht geändert werden");
  fill(q,input,false);return card(records.save(q));
 }
 private void fill(FireMemberQualification q,AssignmentInput in,boolean creation){
  q.issuedOn=in.issuedOn();q.expiresOn=in.expiresOn();
  if(in.active()!=null)q.active=in.active();
  if(creation)q.nextDueOn=in.nextDueOn()!=null?in.nextDueOn():(q.type.tracked?LocalDate.now():in.expiresOn());
  else if(in.nextDueOn()!=null)q.nextDueOn=in.nextDueOn();
  if(in.licenseNumber()!=null&&!in.licenseNumber().isBlank()){
   if(!"DRIVERS_LICENSE".equals(q.type.code))throw bad("Führerscheinnummer darf nur bei Führerschein hinterlegt werden");
   q.licenseNumberMac=driving.fingerprint(in.licenseNumber());
  }
 }
 @GetMapping("/driving")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.drivingcheck.read')")
 @Transactional(readOnly=true)
 public List<Card> driving(){
  return records.findAllByTypeCode("DRIVERS_LICENSE").stream().filter(q->q.active)
   .map(this::card).sorted(Comparator.comparing(Card::nextDueOn,Comparator.nullsLast(Comparator.naturalOrder()))).toList();
 }
 @GetMapping("/driving/my")
 @Transactional(readOnly=true)
 public List<Card> own(Authentication auth){return driving.own(auth).stream().map(this::card).toList();}
}
