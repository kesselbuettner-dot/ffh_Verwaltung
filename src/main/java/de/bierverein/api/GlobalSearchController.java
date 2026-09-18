package de.bierverein.api;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/search") @PreAuthorize("isAuthenticated()")
public class GlobalSearchController {
 private final MemberRepository members; private final DrinkRepository drinks; private final TrainingDocumentRepository documents;
 public GlobalSearchController(MemberRepository m,DrinkRepository d,TrainingDocumentRepository docs){members=m;drinks=d;documents=docs;}
 @GetMapping public List<SearchResult> search(@RequestParam(defaultValue="") String q){
  String x=q.trim().toLowerCase(Locale.ROOT); if(x.isBlank())return List.of(); List<SearchResult> out=new ArrayList<>();
  members.findAll().stream().filter(m->contains(m.getName(),x)||contains(m.getEmail(),x)||contains(m.getPhone(),x)).limit(8).forEach(m->out.add(new SearchResult("Mitglied",m.getId(),m.getName(),m.getEmail(),"members")));
  drinks.findAll().stream().filter(d->d.isActive()&&(contains(d.getName(),x)||contains(d.getCategory(),x)||contains(d.getEan(),x))).limit(8).forEach(d->out.add(new SearchResult("Artikel",d.getId(),d.getName(),d.getCategory(),"articles")));
  documents.findAll().stream().filter(d->contains(d.getTitle(),x)||contains(d.getDescription(),x)||contains(d.getOriginalFileName(),x)).limit(8).forEach(d->out.add(new SearchResult("Schulung / Dienst",d.getId(),d.getTitle(),d.getOriginalFileName(),"training")));
  return out.stream().limit(20).toList();
 }
 private boolean contains(String v,String q){return v!=null&&v.toLowerCase(Locale.ROOT).contains(q);}
 public record SearchResult(String type,Long id,String title,String subtitle,String target){}
}