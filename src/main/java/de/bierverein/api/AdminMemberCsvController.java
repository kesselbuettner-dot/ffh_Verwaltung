package de.bierverein.api;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/admin/member-import")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberCsvController {
 private final MemberService service;
 private final MemberRepository members;
 private final MemberExtraRepository extras;
 public AdminMemberCsvController(MemberService service,MemberRepository members,MemberExtraRepository extras){
  this.service=service;this.members=members;this.extras=extras;
 }
 public record CsvInput(String csv){}
 public record PreviewRow(int line,String name,String email,String phone,String address,LocalDate birthDate,
                          LocalDate joinedOn,String status,String note){}
 public record Preview(int newCount,int duplicates,int errors,List<PreviewRow> rows){}
 public record Committed(int imported,int duplicates){}
 private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 @PostMapping("/preview") @Transactional(readOnly=true)
 public Preview preview(@RequestBody CsvInput input){return examine(input);}
 @PostMapping("/commit") @Transactional
 public Committed commit(@RequestBody CsvInput input,Authentication auth){
  Preview preview=examine(input);
  if(preview.errors>0||preview.rows.size()>1000)throw bad("CSV enthält Fehler oder zu viele Einträge. Bitte Vorschau korrigieren.");
  int imported=0;
  for(PreviewRow row:preview.rows){
   if(!"NEW".equals(row.status))continue;
   // Race-safe recheck: never silently overwrite an existing member.
   if(row.email!=null&&members.findByEmailIgnoreCase(row.email).isPresent())continue;
   if(members.findAll().stream().anyMatch(m->m.getName().equalsIgnoreCase(row.name)))continue;
   var created=service.create(new MemberDtos.MemberRequest(row.name,row.email,row.phone,row.address,
       true,null,null,null,null),auth);
   if(row.birthDate!=null||row.joinedOn!=null){
    MemberExtra extra=new MemberExtra(created.id());extra.birthDate=row.birthDate;extra.joinedOn=row.joinedOn;
    extras.save(extra);
   }
   imported++;
  }
  return new Committed(imported,preview.duplicates+(preview.newCount-imported));
 }
 private Preview examine(CsvInput input){
  if(input==null||input.csv==null||input.csv.isBlank()||input.csv.length()>1_000_000)throw bad("CSV fehlt oder überschreitet 1 MB");
  String text=input.csv.replace("\uFEFF","");
  char separator=delimiter(text);
  try(CSVParser csv=CSVFormat.DEFAULT.builder().setDelimiter(separator).setHeader()
    .setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setIgnoreSurroundingSpaces(true)
    .get().parse(new StringReader(text))){
   Map<String,String> headers=new HashMap<>();
   for(String h:csv.getHeaderMap().keySet())headers.put(normal(h),h);
   if(!has(headers,"name","fullname","givenname","vorname")||!has(headers,"name","fullname","familyname","nachname","givenname","vorname"))
    throw bad("CSV braucht eine Namensspalte oder Vorname/Nachname.");
   Set<String> emailSeen=new HashSet<>(),nameSeen=new HashSet<>();
   Set<String> emailExisting=new HashSet<>(),nameExisting=new HashSet<>();
   for(Member member:members.findAll()){
    if(member.getEmail()!=null)emailExisting.add(member.getEmail().trim().toLowerCase(Locale.ROOT));
    nameExisting.add(member.getName().trim().toLowerCase(Locale.ROOT));
   }
   List<PreviewRow> rows=new ArrayList<>();int fresh=0,duplicates=0,errors=0;
   for(CSVRecord record:csv){
    if(rows.size()>=1000)throw bad("Pro Import sind höchstens 1000 Datensätze zulässig.");
    String first=pick(record,headers,"vorname","givenname");
    String last=pick(record,headers,"nachname","familyname");
    String name=pick(record,headers,"name","fullname");
    if(name==null)name=String.join(" ",first==null?"":first,last==null?"":last).trim();
    String email=pick(record,headers,"email","email1value","emailaddress","emailadresse","e-mail");
    String phone=pick(record,headers,"telefon","phone","phone1value","handy");
    String address=pick(record,headers,"anschrift","adresse","address","address1formatted");
    String birthday=pick(record,headers,"geburtsdatum","birthday");
    String joined=pick(record,headers,"eintritt","eintrittsdatum","joinedon");
    String state="NEW",note="Neu";
    LocalDate birth=null,start=null;
    try{
     birth=parseDate(birthday);start=parseDate(joined);
     if(name.isBlank()||name.length()>160||email!=null&&(email.length()>320||!email.matches("^[^@\\\\s]+@[^@\\\\s]+\\\\.[^@\\\\s]+$"))||
        phone!=null&&phone.length()>80||address!=null&&address.length()>500||birth!=null&&birth.isAfter(LocalDate.now()))
        throw new IllegalArgumentException("Ungültiger oder zu langer Datensatz");
     String normalizedName=name.toLowerCase(Locale.ROOT),normalizedMail=email==null?null:email.toLowerCase(Locale.ROOT);
     if(nameExisting.contains(normalizedName)||!nameSeen.add(normalizedName)||
       normalizedMail!=null&&(emailExisting.contains(normalizedMail)||!emailSeen.add(normalizedMail))){
      state="DUPLICATE";note="Vorhanden oder Dublette innerhalb der Datei";duplicates++;
     }else fresh++;
    }catch(Exception e){state="ERROR";note="Pflichtfeld, Kontakt oder Datum ungültig";errors++;}
    rows.add(new PreviewRow(Math.toIntExact(record.getRecordNumber()+1),name,email,phone,address,birth,start,state,note));
   }
   return new Preview(fresh,duplicates,errors,List.copyOf(rows));
  }catch(ResponseStatusException e){throw e;}
   catch(Exception e){throw bad("CSV konnte nicht gelesen werden: Format oder Kopfzeilen prüfen");}
 }
 private boolean has(Map<String,String> headers,String... keys){return Arrays.stream(keys).anyMatch(headers::containsKey);}
 private String pick(CSVRecord r,Map<String,String> headers,String... names){
  for(String name:names){String h=headers.get(normal(name));if(h!=null&&r.isMapped(h)&&r.isSet(h)){
   String value=r.get(h).trim();if(!value.isBlank())return value;
  }}
  return null;
 }
 private String normal(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 private char delimiter(String text){
  boolean quoted=false;int commas=0,semis=0;
  for(int i=0;i<Math.min(1000,text.length());i++){
   char c=text.charAt(i);if(c=='"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='"'){i++;continue;}quoted=!quoted;}
   else if(!quoted&&c=='\\n')break;
   else if(!quoted&&c==',')commas++;
   else if(!quoted&&c==';')semis++;
  }
  return semis>commas?';':',';
 }
 private LocalDate parseDate(String text){
  if(text==null||text.isBlank())return null;
  try{return LocalDate.parse(text,DateTimeFormatter.ISO_LOCAL_DATE);}
  catch(DateTimeParseException ignored){return LocalDate.parse(text,DateTimeFormatter.ofPattern("dd.MM.uuuu"));}
 }
}
