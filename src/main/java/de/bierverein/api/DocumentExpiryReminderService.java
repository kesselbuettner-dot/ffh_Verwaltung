package de.bierverein.api;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only the worker sends one expiry notice per document per day to its owner.
 * Titles are intentionally not included in push payloads. */
@Service
public class DocumentExpiryReminderService {
 private final ArchiveDocumentRepository docs;
 private final AppUserRepository users;
 private final WebPushService push;
 @Value("${spring.profiles.active:}") private String profiles;
 public DocumentExpiryReminderService(ArchiveDocumentRepository docs,AppUserRepository users,WebPushService push){
  this.docs=docs;this.users=users;this.push=push;
 }
 @Scheduled(cron="0 15 8 * * *",zone="Europe/Berlin")
 @Transactional
 public void remind(){
  if(!Arrays.asList(profiles.split(",")).contains("worker"))return;
  LocalDate today=LocalDate.now(ZoneId.of("Europe/Berlin"));
  for(ArchiveDocument d:docs.findByArchivedFalseAndExpiresOnLessThanEqual(today.plusDays(30))){
   if(today.equals(d.lastExpiryNotification))continue;
   AppUser u=users.findByUsername(d.owner).orElse(null);
   if(u==null||!u.isEnabled()||!u.isRegistrationApproved())continue;
   boolean delivered=push.sendToUser(u.getUsername(),"FW-Cockpit: Dokumentenfrist",
      "Ein Dokument läuft innerhalb von 30 Tagen ab oder ist bereits abgelaufen.",
      "/?page=documents","document-expiry-"+d.id+"-"+today);
   if(delivered){d.lastExpiryNotification=today;docs.save(d);}
  }
 }
}
