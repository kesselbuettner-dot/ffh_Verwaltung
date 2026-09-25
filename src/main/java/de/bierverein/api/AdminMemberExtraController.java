package de.bierverein.api;
import java.time.LocalDate;
import java.util.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/admin/member-profiles")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMemberExtraController {
 private final MemberExtraRepository extras;
 private final MemberCustomFieldRepository fields;
 private final MemberRepository members;
 private final ObjectMapper mapper;
 public AdminMemberExtraController(MemberExtraRepository extras,MemberCustomFieldRepository fields,
     MemberRepository members,ObjectMapper mapper){this.extras=extras;this.fields=fields;this.members=members;this.mapper=mapper;}
 public record Profile(Long memberId,LocalDate birthDate,LocalDate joinedOn,Map<String,String> custom){}
 public record Input(LocalDate birthDate,LocalDate joinedOn,Map<String,String> custom){}
 public record FieldInput(String code,String title,String type){}
 public record AvatarInput(String imageData){}
 public record AvatarView(String imageData){}
 @GetMapping("/{id}/avatar")
 @Transactional(readOnly=true)
 public AvatarView avatar(@PathVariable Long id){
  member(id);
  MemberExtra profile=extras.findById(id).orElse(null);
  if(profile==null||profile.avatarData==null||profile.avatarMime==null)return new AvatarView(null);
  return new AvatarView("data:"+profile.avatarMime+";base64,"+
      Base64.getEncoder().encodeToString(profile.avatarData));
 }
 @PutMapping("/{id}/avatar")
 @Transactional
 public AvatarView saveAvatar(@PathVariable Long id,@RequestBody AvatarInput input){
  member(id);
  if(input==null)throw bad("Avatar fehlt");
  MemberExtra profile=extras.findById(id).orElseGet(()->new MemberExtra(id));
  if(input.imageData()==null||input.imageData().isBlank()){
   profile.avatarData=null;profile.avatarMime=null;extras.save(profile);return new AvatarView(null);
  }
  String text=input.imageData();
  if(text.length()>140000)throw bad("Avatar zu groß (maximal 100 KB)");
  int comma=text.indexOf(',');
  if(comma<0)throw bad("Avatarformat ungültig");
  String mime=text.substring(0,comma);
  if(!mime.equals("data:image/jpeg;base64")&&!mime.equals("data:image/png;base64")&&!mime.equals("data:image/webp;base64"))
    throw bad("Nur JPEG, PNG oder WEBP erlaubt");
  byte[] data;
  try{data=Base64.getDecoder().decode(text.substring(comma+1));}
  catch(IllegalArgumentException ex){throw bad("Ungültige Bilddaten");}
  if(data.length<32||data.length>100000)throw bad("Avatargröße ungültig");
  boolean jpeg=data[0]==(byte)0xff&&data[1]==(byte)0xd8&&data[2]==(byte)0xff;
  boolean png=data[0]==(byte)0x89&&data[1]==0x50&&data[2]==0x4e&&data[3]==0x47;
  boolean webp=data.length>12&&new String(data,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&
    new String(data,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
  if(!(mime.startsWith("data:image/jpeg")&&jpeg||mime.startsWith("data:image/png")&&png||
       mime.startsWith("data:image/webp")&&webp))throw bad("Dateityp und Bilddaten stimmen nicht überein");
  profile.avatarData=data;profile.avatarMime=mime.substring(5,mime.indexOf(';'));extras.save(profile);
  return new AvatarView("data:"+profile.avatarMime+";base64,"+Base64.getEncoder().encodeToString(data));
 }

 private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
 private void member(Long id){if(members.findById(id).isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied nicht gefunden");}
 private Map<String,String> parse(String json){
  if(json==null||json.isBlank())return Map.of();
  try{return mapper.readValue(json,new TypeReference<Map<String,String>>(){});}
  catch(Exception e){throw new IllegalStateException("Stammdatenfelder können nicht gelesen werden",e);}
 }
 private Profile profile(MemberExtra extra){return new Profile(extra.memberId,extra.birthDate,extra.joinedOn,parse(extra.customJson));}
 @GetMapping("/overview") @Transactional(readOnly=true)
 public List<Profile> overview(){return extras.findAll().stream().map(this::profile).toList();}
 @GetMapping("/fields") @Transactional(readOnly=true)
 public List<MemberCustomField> fields(){return fields.findAll().stream().filter(x->x.active).sorted(Comparator.comparing(x->x.title)).toList();}
 @PostMapping("/fields") @Transactional
 public MemberCustomField addField(@RequestBody FieldInput input){
  if(input==null||input.code()==null||input.title()==null||input.type()==null)throw bad("Felddefinition fehlt");
  String code=input.code().trim().toUpperCase(Locale.ROOT),title=input.title().trim(),type=input.type().trim().toUpperCase(Locale.ROOT);
  if(!code.matches("[A-Z][A-Z0-9_]{0,47}")||title.isEmpty()||title.length()>100||!List.of("TEXT","DATE","NUMBER").contains(type)
     ||fields.findAll().stream().anyMatch(x->x.code.equals(code)))throw bad("Kennung, Typ oder Titel ungültig");
  return fields.save(new MemberCustomField(code,title,type));
 }
 @GetMapping("/{id}") @Transactional(readOnly=true)
 public Profile get(@PathVariable Long id){
  member(id);return profile(extras.findById(id).orElseGet(()->new MemberExtra(id)));
 }
 @PutMapping("/{id}") @Transactional
 public Profile save(@PathVariable Long id,@RequestBody Input input){
  member(id);if(input==null)throw bad("Profil fehlt");
  if(input.birthDate()!=null&&input.birthDate().isAfter(LocalDate.now()))throw bad("Geburtsdatum liegt in der Zukunft");
  if(input.joinedOn()!=null&&input.joinedOn().isAfter(LocalDate.now().plusYears(1)))throw bad("Eintrittsdatum ungültig");
  Map<String,String> values=input.custom()==null?Map.of():input.custom();
  if(values.size()>60)throw bad("Zu viele benutzerdefinierte Felder");
  Map<String,MemberCustomField> allowed=new HashMap<>();
  for(var field:fields.findAll())if(field.active)allowed.put(field.code,field);
  Map<String,String> clean=new LinkedHashMap<>();
  for(var item:values.entrySet()){
   MemberCustomField field=allowed.get(item.getKey());
   if(field==null)throw bad("Unbekanntes oder deaktiviertes Stammdatenfeld");
   String val=item.getValue();if(val==null||val.isBlank())continue;
   val=val.trim();if(val.length()>500)throw bad("Stammdatenfeld zu lang");
   try{
    switch(field.type){
     case "DATE" -> LocalDate.parse(val);
     case "NUMBER" -> new java.math.BigDecimal(val);
     default -> {}
    }
   }catch(Exception e){throw bad("Feldformat ungültig: "+field.title);}
   clean.put(item.getKey(),val);
  }
  MemberExtra extra=extras.findById(id).orElseGet(()->new MemberExtra(id));
  extra.birthDate=input.birthDate();extra.joinedOn=input.joinedOn();
  try{extra.customJson=mapper.writeValueAsString(clean);}catch(Exception e){throw bad("Felder können nicht gespeichert werden");}
  return profile(extras.save(extra));
 }
}
