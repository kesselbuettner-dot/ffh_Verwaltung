package de.bierverein.api;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;

/** Deterministic suggestions, never an authoritative inspection record. */
@Service
public class DocumentSuggestionsService {
 public record Field(String value,String evidence){}
 public record SuggestedFields(String category,Field serialNumber,Field manufacturer,
   Field inspectionDate,Field nextInspectionDate,String note){}
 private static final DateTimeFormatter DMY=DateTimeFormatter.ofPattern("d.M.uuuu").withResolverStyle(ResolverStyle.STRICT);
 private static final Pattern DATE=Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[.]\\s*(\\d{1,2})\\s*[.]\\s*(20\\d{2})(?!\\d)");
 private static final Pattern SERIAL=Pattern.compile("(?im)^(?:serien(?:nummer|-nr\\.)?|fabrik(?:nummer|-nr\\.)?|s\\s*/\\s*n|ident(?:-?nr\\.?|ifikationsnummer)?)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./_-]{2,55})\\s*$");
 private static final Pattern MFG=Pattern.compile("(?im)^(?:hersteller|fabrikat)\\s*[:#-]?\\s*([^\\r\\n]{2,100})");
 private static final Pattern INSPECTION=Pattern.compile("(?i)(?:letzte\\s+prüfung|prüfdatum|geprüft\\s+am|datum\\s+der\\s+prüfung)\\s*[:\\s-]*");
 private static final Pattern NEXT=Pattern.compile("(?i)(?:nächste(?:r|s)?\\s+prüfung|nächster?\\s+prüftermin|wiederholungsprüfung|fällig(?:keit)?\\s*(?:am)?)\\s*[:\\s-]*");
 static String validDate(String raw){
  Matcher m=DATE.matcher(raw);
  if(!m.find())return null;
  try{return LocalDate.parse(m.group(1)+"."+m.group(2)+"."+m.group(3),DMY).toString();}
  catch(DateTimeException ex){return null;}
 }
 private static Field dateAfter(String text,Pattern label){
  Matcher matcher=label.matcher(text);
  while(matcher.find()){
   String tail=text.substring(matcher.end(),Math.min(text.length(),matcher.end()+48)).split("[\\r\\n]",2)[0];
   String date=validDate(tail);
   if(date!=null)return new Field(date,text.substring(matcher.start(),Math.min(text.length(),matcher.end()+48)).trim());
  }
  return null;
 }
 private static Field match(String text,Pattern pattern){
  Matcher m=pattern.matcher(text);
  if(!m.find())return null;
  return new Field(m.group(1).trim(),m.group().trim());
 }
 public SuggestedFields suggest(String extracted,String fileName){
  String t=extracted==null?"":extracted.substring(0,Math.min(21000,extracted.length()));
  String l=(t+" "+Objects.toString(fileName,"")).toLowerCase(Locale.GERMAN);
  String category=l.contains("prüfbericht")||l.contains("geräteprüfung")||l.contains("seriennummer")?"DEVICE":
    l.contains("fahrzeug")||l.contains("kennzeichen")?"VEHICLE":
    l.contains("schulung")||l.contains("lehrgang")?"TRAINING":"GENERAL";
  return new SuggestedFields(category,match(t,SERIAL),match(t,MFG),
    dateAfter(t,INSPECTION),dateAfter(t,NEXT),
    t.isBlank()?"Kein auswertbarer Text. Bei Bildern OCR-Auflösung und Lesbarkeit prüfen.":
    "Automatische Erkennung – alle Werte vor einer Übernahme mit dem Original vergleichen.");
 }
}
