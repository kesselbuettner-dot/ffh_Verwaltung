package de.bierverein.api;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Role-checked manual tasks; generated specialist assignments are not duplicated here. */
@RestController
@RequestMapping("/api/my-tasks/general")
public class GeneralTaskController {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private static final Set<String> CREATORS=Set.of("ADMIN","VORSTAND","FEUERWEHRWART","GERATEWART","GETRAENKEWART","KASSENWART");
 private static final Set<String> STATUSES=Set.of("OPEN","IN_PROGRESS","DONE");
 private static final List<String> ALL_CATEGORIES=List.of("GENERAL","FIRE","TRAINING","DEVICE","CATERING","FINANCE");
 private static final Map<String,List<String>> RESPONSIBILITIES=Map.of(
   "FEUERWEHRWART",List.of("FIRE","TRAINING"),
   "GERATEWART",List.of("DEVICE"),
   "GETRAENKEWART",List.of("CATERING"),
   "KASSENWART",List.of("FINANCE"));
 private final GeneralTaskRepository tasks;
 private final AppUserRepository users;
 private final ManagedUserRoleRepository roles;
 private final WebPushService push;
 public GeneralTaskController(GeneralTaskRepository tasks,AppUserRepository users,ManagedUserRoleRepository roles,WebPushService push){
  this.tasks=tasks;this.users=users;this.roles=roles;this.push=push;
 }
 public record Input(String title,String description,Long assigneeId,LocalDate dueOn,String status,String category){}
 public record Person(Long id,String name){}
 public record View(Long id,String title,String description,String category,Long assigneeId,String assigneeName,Long creatorId,String creatorName,LocalDate dueOn,String status,Instant createdAt,Instant updatedAt,Instant completedAt,boolean canEdit,boolean canChangeStatus){}
 public record Overview(boolean canCreate,Long currentUserId,List<String> allowedCategories,List<Person> assignees,List<View> tasks){}
 private ResponseStatusException error(HttpStatus status,String message){return new ResponseStatusException(status,message);}
 private AppUser actor(Authentication authentication){
  if(authentication==null)throw error(HttpStatus.UNAUTHORIZED,"Anmeldung erforderlich");
  AppUser u=users.findByUsernameWithMember(authentication.getName()).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"Benutzer unbekannt"));
  if(!u.isEnabled()||!u.isRegistrationApproved())throw error(HttpStatus.FORBIDDEN,"Konto nicht freigeschaltet");
  return u;
 }
 private Set<String> accountRoles(AppUser u){
  Set<String> codes=new HashSet<>();codes.add(u.getRole().name());
  roles.findByUserId(u.getId()).forEach(r->codes.add(r.getRole().getCode()));
  return codes;
 }
 private boolean isBoard(AppUser u){return accountRoles(u).stream().anyMatch(c->c.equals("ADMIN")||c.equals("VORSTAND"));}
 private List<String> allowedCategories(AppUser u){
  Set<String> active=accountRoles(u);
  if(active.contains("ADMIN")||active.contains("VORSTAND"))return ALL_CATEGORIES;
  return ALL_CATEGORIES.stream().filter(category->RESPONSIBILITIES.entrySet().stream()
    .anyMatch(entry->active.contains(entry.getKey())&&entry.getValue().contains(category))).toList();
 }
 private boolean creator(AppUser u){return !allowedCategories(u).isEmpty();}
 private String category(GeneralTask t){return t.category==null||t.category.isBlank()?"GENERAL":t.category;}
 private boolean canManage(AppUser u,GeneralTask t){
  return isBoard(u)||(Objects.equals(t.creatorId,u.getId())&&allowedCategories(u).contains(category(t)));
 }
 private boolean eligible(AppUser u){return u.isEnabled()&&u.isRegistrationApproved()&&u.getMember()!=null&&u.getMember().isActive();}
 private String name(Long id){return users.findById(id).map(u->u.getMember()==null?u.getUsername():u.getMember().getName()).orElse("Konto nicht vorhanden");}
 private View view(GeneralTask t,AppUser u){
  boolean edit=canManage(u,t);
  return new View(t.id,t.title,t.description,category(t),t.assigneeId,name(t.assigneeId),t.creatorId,name(t.creatorId),t.dueOn,t.status,t.createdAt,t.updatedAt,t.completedAt,edit,Objects.equals(t.assigneeId,u.getId())||edit);
 }
 private GeneralTask visible(Long id,AppUser u){
  GeneralTask t=tasks.findById(id).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Aufgabe nicht gefunden"));
  if(!Objects.equals(t.assigneeId,u.getId())&&!Objects.equals(t.creatorId,u.getId())
      &&!isBoard(u))
   throw error(HttpStatus.NOT_FOUND,"Aufgabe nicht gefunden");
  return t;
 }
 private void notifyAfterCommit(GeneralTask t,String reason){
  Long assignee=t.assigneeId,taskId=t.id;
  String heading="Neue Aufgabe".equals(reason)?"Neue Aufgabe zugewiesen":"Aufgabe aktualisiert";
  Runnable send=()->users.findById(assignee).filter(this::eligible).ifPresent(u->push.sendToUser(u.getUsername(),
   "FW-Cockpit: "+heading,"Eine Vereinsaufgabe wartet auf dich.","/?my-tasks=1","general-task-"+taskId+"-"+reason));
  if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){send.run();}
  });else send.run();
 }
 @GetMapping @Transactional(readOnly=true)
 public Overview list(Authentication auth){
  AppUser u=actor(auth);boolean canCreate=creator(u);
  List<Person> assignees=canCreate?users.findAll().stream().filter(this::eligible)
   .map(p->new Person(p.getId(),p.getMember().getName())).sorted(Comparator.comparing(Person::name,String.CASE_INSENSITIVE_ORDER)).toList():List.of();
  List<GeneralTask> own=tasks.findByAssigneeIdOrCreatorIdOrderByDueOnAscCreatedAtDesc(u.getId(),u.getId());
  return new Overview(canCreate,u.getId(),allowedCategories(u),assignees,own.stream().map(t->view(t,u)).toList());
 }
 private void apply(GeneralTask t,Input input,AppUser actor,boolean creating){
  if(input==null||input.title()==null||input.title().trim().isEmpty()||input.title().trim().length()>160)
   throw error(HttpStatus.BAD_REQUEST,"Aufgabentitel fehlt oder ist zu lang");
  if(input.description()!=null&&input.description().length()>4000)throw error(HttpStatus.BAD_REQUEST,"Beschreibung zu lang");
  String chosen=input.category()==null||input.category().isBlank()
    ?(creating?allowedCategories(actor).get(0):category(t)):input.category().trim().toUpperCase(Locale.ROOT);
  if(!ALL_CATEGORIES.contains(chosen)||!allowedCategories(actor).contains(chosen))
   throw error(HttpStatus.FORBIDDEN,"Aufgaben dürfen nur im eigenen Zuständigkeitsbereich vergeben werden");
  t.category=chosen;
  {
   AppUser selected=users.findById(Objects.requireNonNullElse(input.assigneeId(),-1L))
     .orElseThrow(()->error(HttpStatus.BAD_REQUEST,"Bitte ein Mitglied auswählen"));
   if(!eligible(selected))throw error(HttpStatus.BAD_REQUEST,"Das Mitglied hat keinen freigeschalteten aktiven Zugang");
   t.assigneeId=selected.getId();
  }
  if(input.dueOn()!=null&&input.dueOn().isBefore(LocalDate.now(ZONE).minusYears(1)))throw error(HttpStatus.BAD_REQUEST,"Ungültige Fälligkeit");
  t.title=input.title().trim();t.description=input.description()==null?"":input.description().trim();t.dueOn=input.dueOn();t.updatedAt=Instant.now();
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
 public View create(@RequestBody Input input,Authentication auth){
  AppUser u=actor(auth);if(!creator(u))throw error(HttpStatus.FORBIDDEN,"Aufgaben anlegen dürfen nur Vorstand, Administration und Fachverantwortliche");
  GeneralTask t=new GeneralTask("","",u.getId(),u.getId(),null);apply(t,input,u,true);
  t=tasks.save(t);notifyAfterCommit(t,"Neue Aufgabe");return view(t,u);
 }
 @PutMapping("/{id}") @Transactional
 public View edit(@PathVariable Long id,@RequestBody Input input,Authentication auth){
  AppUser u=actor(auth);GeneralTask t=visible(id,u);
  if(!canManage(u,t))
   throw error(HttpStatus.FORBIDDEN,"Keine Berechtigung zum Bearbeiten");
  if("DONE".equals(t.status))throw error(HttpStatus.CONFLICT,"Abgeschlossene Aufgabe kann nicht bearbeitet werden");
  Long former=t.assigneeId;apply(t,input,u,false);t=tasks.save(t);
  if(!Objects.equals(former,t.assigneeId))notifyAfterCommit(t,"Zuweisung");
  return view(t,u);
 }
 @PatchMapping("/{id}/status") @Transactional
 public View status(@PathVariable Long id,@RequestBody Input input,Authentication auth){
  AppUser u=actor(auth);GeneralTask t=visible(id,u);
  boolean canEdit=canManage(u,t);
  if(!Objects.equals(t.assigneeId,u.getId())&&!canEdit)throw error(HttpStatus.FORBIDDEN,"Keine Berechtigung für diese Aufgabe");
  String status=input==null?null:input.status();
  if(!STATUSES.contains(status))throw error(HttpStatus.BAD_REQUEST,"Ungültiger Status");
  t.status=status;t.completedAt="DONE".equals(status)?Instant.now():null;t.updatedAt=Instant.now();
  return view(tasks.save(t),u);
 }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
 public void delete(@PathVariable Long id,Authentication auth){
  AppUser u=actor(auth);GeneralTask t=visible(id,u);
  if(!canManage(u,t))
   throw error(HttpStatus.FORBIDDEN,"Keine Berechtigung zum Löschen");
  tasks.delete(t);
 }
}
