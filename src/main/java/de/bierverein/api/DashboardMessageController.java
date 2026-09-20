package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard/messages")
public class DashboardMessageController {
    private final DashboardMessageRepository messages; private final AppSettingsRepository settings; private final AppUserRepository users;
    public DashboardMessageController(DashboardMessageRepository m, AppSettingsRepository s, AppUserRepository u){messages=m;settings=s;users=u;}
    @GetMapping @Transactional(readOnly=true) public List<View> list(Authentication auth){
        boolean edit=canEdit(auth); Instant now=Instant.now();
        return messages.findAllByOrderByPriorityDescEventAtAscCreatedAtDesc().stream()
                .filter(m->edit || (m.isActive() && (m.getPublishFrom()==null||!m.getPublishFrom().isAfter(now)) && (m.getPublishUntil()==null||!m.getPublishUntil().isBefore(now))))
                .map(m->view(m,edit)).toList();
    }
    @PostMapping @Transactional public View create(@RequestBody Request r,Authentication auth){requireEdit(auth);DashboardMessage m=new DashboardMessage();apply(m,r);m.setCreatedBy(auth.getName());return view(messages.save(m),true);}
    @PutMapping("/{id}") @Transactional public View update(@PathVariable Long id,@RequestBody Request r,Authentication auth){requireEdit(auth);DashboardMessage m=messages.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));apply(m,r);return view(messages.save(m),true);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id,Authentication auth){requireEdit(auth);if(!messages.existsById(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);messages.deleteById(id);}
    private void apply(DashboardMessage m,Request r){if(r==null||r.title()==null||r.title().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Titel ist erforderlich");String type=Optional.ofNullable(r.type()).orElse("MESSAGE").toUpperCase();if(!Set.of("MESSAGE","APPOINTMENT").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ungültiger Typ");m.setType(type);m.setTitle(trim(r.title(),140));m.setBody(trim(r.body(),2000));m.setEventAt(r.eventAt());m.setPublishFrom(r.publishFrom());m.setPublishUntil(r.publishUntil());m.setPriority(Math.max(0,Math.min(10,r.priority())));m.setActive(r.active());}
    private boolean canEdit(Authentication auth){AppUser u=users.findByUsername(auth.getName()).orElse(null);if(u==null)return false;String configured=settings.findAll().stream().findFirst().map(AppSettings::getMessageEditorRoles).orElse("ADMIN,VORSTAND");return Arrays.stream(configured.split(",")).map(String::trim).anyMatch(x->x.equals(u.getRole().name()));}
    private void requireEdit(Authentication auth){if(!canEdit(auth))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Keine Berechtigung zum Bearbeiten von Meldungen");}
    private String trim(String v,int max){if(v==null||v.isBlank())return null;String x=v.trim();return x.substring(0,Math.min(max,x.length()));}
    private View view(DashboardMessage m,boolean edit){return new View(m.getId(),m.getType(),m.getTitle(),m.getBody(),m.getEventAt(),m.getPublishFrom(),m.getPublishUntil(),m.getPriority(),m.isActive(),m.getCreatedBy(),m.getCreatedAt(),edit);}
    public record Request(String type,String title,String body,Instant eventAt,Instant publishFrom,Instant publishUntil,int priority,boolean active){}
    public record View(Long id,String type,String title,String body,Instant eventAt,Instant publishFrom,Instant publishUntil,int priority,boolean active,String createdBy,Instant createdAt,boolean canEdit){}
}
