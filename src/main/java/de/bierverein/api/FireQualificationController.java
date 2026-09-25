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
 private final MemberExtraRepository memberExtras;
 private final FireQualificationAttachmentRepository attachments;
 public FireQualificationController(FireQualificationTypeRepository types,FireMemberQualificationRepository records,
     MemberRepository members,FireQualificationPermissions permission,DrivingCheckService driving,ManagedUserRoleRepository managedRoles,MemberExtraRepository memberExtras,FireQualificationAttachmentRepository attachments){
  this.types=types;this.records=records;this.members=members;this.permission=permission;this.driving=driving;this.managedRoles=managedRoles;this.memberExtras=memberExtras;this.attachments=attachments;
 }
 public record TypeView(Long id,String code,String title,String icon,String shortLabel,String category,boolean tracked,boolean sensitive,int warningDays,int intervalMonths){}
 public record TypeInput(String code,String title,String icon,String shortLabel,String category,Boolean tracked,Boolean sensitive,Integer warningDays,Integer intervalMonths){}
 public record AssignmentInput(Long typeId,LocalDate issuedOn,LocalDate expiresOn,LocalDate nextDueOn,
   Boolean active,String licenseNumber,List<String> licenseClasses){}
 public record Card(Long id,Long memberId,String memberName,String code,String title,String icon,String shortLabel,String category,List<String> licenseClasses,boolean documentAttached,
   LocalDate issuedOn,LocalDate expiresOn,LocalDate lastCheckedOn,LocalDate nextDueOn,String status,boolean referencePresent){}
 private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private TypeView view(FireQualificationType t){return new TypeView(t.id,t.code,t.title,t.icon,abbreviation(t),category(t),t.tracked,t.sensitive,t.warningDays,t.intervalMonths);}
 private static final Set<String> LICENSE_CLASSES=Set.of("AM","A1","A2","A","B","BE","B96","C1","C1E","C","CE","D1","D1E","D","DE","L","T");
 private String category(FireQualificationType t){
  // Existing medical examination types are displayed under suitability without altering saved member records.
  if("MEDICAL_DUE".equals(t.code))return "SUITABILITY";
  if("DRIVERS_LICENSE".equals(t.code))return "CERTIFICATE_DOCUMENT";
  if(t.category!=null&&Set.of("QUALIFICATION","CERTIFICATE_DOCUMENT","SUITABILITY").contains(t.category))return t.category;
  return "QUALIFICATION";
 }
 private String validCategory(String category){
  if(category==null||category.isBlank())return "QUALIFICATION";
  if(!Set.of("QUALIFICATION","CERTIFICATE_DOCUMENT","SUITABILITY").contains(category))throw bad("Ungültige Kachelkategorie");
  return category;
 }
 private String classes(List<String> selected){
  if(selected==null)return null;
  var normalized=new TreeSet<String>();
  for(String raw:selected){if(raw==null)throw bad("Führerscheinklasse fehlt");String v=raw.trim().toUpperCase(Locale.ROOT);
    if(!LICENSE_CLASSES.contains(v))throw bad("Ungültige Führerscheinklasse: "+v);normalized.add(v);}
  return String.join(",",normalized);
 }
 private List<String> classList(String saved){return saved==null||saved.isBlank()?List.of():List.of(saved.split(","));}
 private String abbreviation(FireQualificationType t){
  if(t.shortLabel!=null&&!t.shortLabel.isBlank())return t.shortLabel;
  return switch(t.code){
   case "DRIVERS_LICENSE" -> "FS";
   case "CHAINSAW" -> "MS";
   case "MEDICAL_DUE" -> "UNT";
   case "RETRAINING" -> "AW";
   default -> {
    String normalized=t.title.trim();
    String[] words=normalized.split("[\\s/-]+");
    StringBuilder shortName=new StringBuilder();
    if(words.length==1)yield normalized.substring(0,Math.min(3,normalized.length())).toUpperCase(Locale.GERMAN);
    for(String word:words)if(!word.isBlank()&&shortName.length()<5)shortName.append(word.substring(0,1).toUpperCase(Locale.GERMAN));
    yield shortName.toString();
   }
  };
 }
 private String checkedIcon(String raw){
  if(raw==null||raw.isBlank())return "📋";
  String icon=raw.trim();
  if(icon.length()>80||icon.contains("<")||icon.contains(">"))throw bad("Icon ungültig oder zu lang");
  return icon;
 }
 private String checkedShortLabel(String text){
  if(text==null||text.isBlank())return null;
  String label=text.trim().toUpperCase(Locale.GERMAN);
  if(label.length()>10||!label.matches("[\\p{L}\\p{N} -]+"))throw bad("Abkürzung: maximal 10 Buchstaben/Ziffern");
  return label;
 }
 private int range(Integer value,int max,int otherwise){int n=value==null?otherwise:value;if(n<0||n>max)throw bad("Intervall außerhalb des gültigen Bereichs");return n;}
 private String required(String s,int max){if(s==null||s.isBlank()||s.length()>max)throw bad("Pflichtfeld fehlt oder ist zu lang");return s.trim();}
 private boolean canSensitive(Authentication auth){return permission.allowed(auth,"fire.qualifications.sensitive.read");}
 private FireMemberQualification record(Long id){return records.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Qualifikation nicht gefunden"));}
 private Card card(FireMemberQualification q){
  Member m=members.findById(q.memberId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied fehlt"));
  LocalDate due=due(q),now=LocalDate.now();
  String state=!q.active?"INACTIVE":due==null?(q.type.tracked?"UNSCHEDULED":"VALID"):
    due.isBefore(now)?"OVERDUE":!due.isAfter(now.plusDays(q.type.warningDays))?"DUE":"VALID";
  return new Card(q.id,q.memberId,m.getName(),q.type.code,q.type.title,q.type.icon,abbreviation(q.type),category(q.type),classList(q.licenseClasses),attachments.existsByQualificationId(q.id),
   q.issuedOn,q.expiresOn,q.lastCheckedOn,due,state,q.licenseNumberMac!=null);
 }
 private LocalDate due(FireMemberQualification q){
  if(q.type.tracked&&q.lastCheckedOn!=null&&q.type.intervalMonths>0)
   return q.lastCheckedOn.plusMonths(q.type.intervalMonths);
  return q.nextDueOn!=null?q.nextDueOn:q.expiresOn;
 }
 public record Person(Long id,String name,String avatar) {}
 @GetMapping("/people")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public List<Person> people(){
  Map<Long,MemberExtra> avatars=new HashMap<>();
  for(MemberExtra extra:memberExtras.findAll())if(extra.avatarData!=null&&extra.avatarData.length<=100_000)avatars.put(extra.memberId,extra);
  return members.findAll().stream().filter(Member::isActive).map(m->{
   MemberExtra extra=avatars.get(m.getId());
   String avatar=extra==null||extra.avatarMime==null?null:
     "data:"+extra.avatarMime+";base64,"+Base64.getEncoder().encodeToString(extra.avatarData);
   return new Person(m.getId(),m.getName(),avatar);
  }).sorted(Comparator.comparing(Person::name)).toList();
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
  if(types.findByCode(code).isEmpty()){FireQualificationType type=new FireQualificationType(code,title,icon,tracked,sensitive,warn,months);
   type.category=code.equals("DRIVERS_LICENSE")?"CERTIFICATE_DOCUMENT":"QUALIFICATION";types.save(type);}
 }
 @PostMapping("/types")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public TypeView createType(@RequestBody TypeInput input){
  if(input==null)throw bad("Qualifikationstyp fehlt");
  String code=required(input.code(),64).toUpperCase(Locale.ROOT);
  if(!code.matches("[A-Z][A-Z0-9_]{0,63}")||types.findByCode(code).isPresent())throw bad("Kennung ungültig oder vorhanden");
  var t=new FireQualificationType(code,required(input.title(),120),
      checkedIcon(input.icon()),Boolean.TRUE.equals(input.tracked()),
      Boolean.TRUE.equals(input.sensitive()),range(input.warningDays(),365,30),range(input.intervalMonths(),120,0));
  t.shortLabel=checkedShortLabel(input.shortLabel());
  t.category=validCategory(input.category());
  return view(types.save(t));
 }
 @PutMapping("/types/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public TypeView updateType(@PathVariable Long id,@RequestBody TypeInput input){
  if(input==null)throw bad("Daten fehlen");
  FireQualificationType t=types.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  t.title=required(input.title(),120);if(input.icon()!=null)t.icon=checkedIcon(input.icon());
  t.shortLabel=checkedShortLabel(input.shortLabel());
  t.category=validCategory(input.category());
  t.tracked=Boolean.TRUE.equals(input.tracked());t.sensitive=Boolean.TRUE.equals(input.sensitive());
  t.warningDays=range(input.warningDays(),365,t.warningDays);t.intervalMonths=range(input.intervalMonths(),120,t.intervalMonths);
  return view(types.save(t));
 }
 @GetMapping("/cards")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.read')")
 @Transactional(readOnly=true)
 public List<Card> cards(Authentication auth,@RequestParam(required=false) Long memberId){
  return (memberId==null?records.findAll():records.findByMemberId(memberId)).stream()
   .filter(q->q.active).filter(q->!q.type.sensitive||canSensitive(auth)).map(this::card)
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
  var previous=records.findByMemberIdAndTypeCode(memberId,type.code);
  if(previous.isPresent()){
   if(previous.get().active)throw new ResponseStatusException(HttpStatus.CONFLICT,"Kachel bereits zugewiesen");
   FireMemberQualification restored=previous.get();restored.active=true;fill(restored,input,false);return card(records.save(restored));
  }
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
  if(in.licenseClasses()!=null){if(!"DRIVERS_LICENSE".equals(q.type.code))throw bad("Führerscheinklassen nur bei Führerschein");q.licenseClasses=classes(in.licenseClasses());}
  if(in.licenseNumber()!=null&&!in.licenseNumber().isBlank()){
   if(!"DRIVERS_LICENSE".equals(q.type.code))throw bad("Führerscheinnummer darf nur bei Führerschein hinterlegt werden");
   q.licenseNumberMac=driving.fingerprint(in.licenseNumber());
  }
 }
 @DeleteMapping("/cards/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public ResponseEntity<Void> removeCard(@PathVariable Long id,Authentication auth){
  FireMemberQualification q=record(id);
  if(q.type.sensitive&&!canSensitive(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  // Archiving preserves historical checks and attachments and allows later restoration.
  q.active=false;records.save(q);return ResponseEntity.noContent().build();
 }
 @DeleteMapping("/types/{id}")
 @PreAuthorize("@fireQualificationPermissions.allowed(authentication,'fire.qualifications.write')")
 @Transactional
 public ResponseEntity<Void> removeType(@PathVariable Long id,Authentication auth){
  FireQualificationType t=types.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(t.sensitive&&!canSensitive(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  if(records.findAll().stream().anyMatch(q->q.type.id.equals(id)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Kacheltyp ist Mitgliedern zugeordnet bzw. archiviert. Bitte die Zuordnungen zunächst klären; bestehende Prüfhistorie bleibt erhalten.");
  types.delete(t);return ResponseEntity.noContent().build();
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
