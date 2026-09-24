package de.bierverein.api;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
/** Five days ahead, notify only eligible members who have neither accepted nor declined.
 * A persisted dispatch key prevents duplicate notifications on subsequent executions.
 */
@Component
public class TrainingResponseReminderScheduler {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private final TrainingScheduleService service;
 private final AppUserRepository users;
 private final TrainingReminderDispatchRepository dispatches;
 private final WebPushService push;
 public TrainingResponseReminderScheduler(TrainingScheduleService service,AppUserRepository users,TrainingReminderDispatchRepository dispatches,WebPushService push){
  this.service=service;this.users=users;this.dispatches=dispatches;this.push=push;
 }
 @Scheduled(cron="0 0 9 * * *",zone="Europe/Berlin")
 public void remindUnansweredServices(){
  if(!push.enabled())return;
  LocalDate date=LocalDate.now(ZONE).plusDays(5);
  for(AppUser user:users.findAll()){
   if(!user.isEnabled()||!user.isRegistrationApproved())continue;
   try{
    for(TrainingScheduleService.OccurrenceView event:service.list(user.getUsername(),date,date).occurrences()){
     if(!"SERVICE".equals(event.type())||!event.registrationRequired()||!event.canRespond()||event.response()!=null)continue;
     if(dispatches.existsByEventIdAndOccurrenceDateAndUsername(event.eventId(),date,user.getUsername()))continue;
     boolean sent=push.sendToUser(user.getUsername(),"Rückmeldung zum Dienst ausstehend",
       event.title()+" – "+date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))+
       ". Bitte gib deine Zu- oder Absage ab.","/?service-reminders=1",
       "ffh-service-reminder-"+event.eventId()+"-"+date);
     if(sent)dispatches.saveAndFlush(new TrainingReminderDispatch(event.eventId(),date,user.getUsername()));
    }
   }catch(Exception ignored){/* One invalid or inactive account must not block other members. */}
  }
 }
}
