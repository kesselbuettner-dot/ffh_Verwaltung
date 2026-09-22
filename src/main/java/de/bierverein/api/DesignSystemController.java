package de.bierverein.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Separate settings endpoint: editing visual tokens can never reset menu, roles or organization. */
@RestController
@RequestMapping("/api/settings/design-system")
public class DesignSystemController {
 private final AppSettingsRepository repository;
 private final ObjectMapper json;
 private static final Map<String,Pattern> VALIDATORS=Map.of(
  "space",Pattern.compile("^(?:[4-9]|[1-3][0-9]|40)px$"),
  "radius",Pattern.compile("^(?:[4-9]|1[0-9]|2[0-4])px$"),
  "controlHeight",Pattern.compile("^(?:4[4-9]|[5-6][0-9])px$"),
  "pageWidth",Pattern.compile("^(?:9[0-9][0-9]|1[0-6][0-9][0-9])px$"),
  "textSize",Pattern.compile("^(?:1[4-9]|20)px$"),
  "shadow",Pattern.compile("^(?:none|0 4px 20px #00000015|0 8px 28px #00000030)$"),
  "templateColumns",Pattern.compile("^[234]$"),
  "templateGap",Pattern.compile("^(?:8|12|16|20|24|32)px$")
 );
 public record DesignTokens(Map<String,String> tokens){}
 public DesignSystemController(AppSettingsRepository repository,ObjectMapper json){
  this.repository=repository;this.json=json;
 }
 private AppSettings current(){
  return repository.findAll().stream().findFirst().orElseGet(()->repository.save(new AppSettings()));
 }
 @GetMapping
 @Transactional(readOnly=true)
 @PreAuthorize("isAuthenticated()")
 public DesignTokens get(){return new DesignTokens(read(current().getDesignTokens()));}
 @PutMapping
 @Transactional
 @PreAuthorize("hasRole('ADMIN')")
 public DesignTokens save(@RequestBody DesignTokens body){
  if(body==null||body.tokens()==null||body.tokens().size()>VALIDATORS.size())
   throw invalid();
  Map<String,String> values=new LinkedHashMap<>();
  for(var item:body.tokens().entrySet()){
   if(item.getKey()==null||item.getValue()==null||!VALIDATORS.containsKey(item.getKey())
    ||!VALIDATORS.get(item.getKey()).matcher(item.getValue()).matches())throw invalid();
   values.put(item.getKey(),item.getValue());
  }
  AppSettings settings=current();
  try{settings.setDesignTokens(json.writeValueAsString(values));}
  catch(Exception ex){throw invalid();}
  repository.save(settings);
  return new DesignTokens(Map.copyOf(values));
 }
 private Map<String,String> read(String data){
  if(data==null||data.isBlank())return Map.of();
  try{
   Map<String,String> stored=json.readValue(data,new TypeReference<Map<String,String>>(){});
   Map<String,String> valid=new LinkedHashMap<>();
   for(var entry:stored.entrySet())if(entry.getKey()!=null&&entry.getValue()!=null
     &&VALIDATORS.containsKey(entry.getKey())&&VALIDATORS.get(entry.getKey()).matcher(entry.getValue()).matches())
      valid.put(entry.getKey(),entry.getValue());
   return valid;
  }catch(Exception ex){return Map.of();}
 }
 private ResponseStatusException invalid(){
  return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Designsystem-Werte ungültig. Nur vordefinierte Abstände, Rundungen, Schriftgrößen und Schatten sind erlaubt.");
 }
}
