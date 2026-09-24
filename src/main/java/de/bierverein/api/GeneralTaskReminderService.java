package de.bierverein.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** Sends one due/overdue reminder per task per day, only on the worker profile. */
@Service
public class GeneralTaskReminderService {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private final GeneralTaskRepository tasks;private final AppUserRepository users;private final WebPushService push;
 @Value("${spring.profiles.active:}") private String activeProfiles;
 public GeneralTaskReminderService(GeneralTaskRepository tasks,AppUserRepository users,WebPushService push){
  this.tasks=tasks;this.users=users;this.push=push;
 }
 @Scheduled(cron="0 0 8 * * *",zone="Europe/Berlin")
 @Transactional
 public void remindDue(){
  if(!Arrays.asList(activeProfiles.split(",")).contains("worker"))return;
  LocalDate today=LocalDate.now(ZONE);
  for(GeneralTask task:tasks.findByStatusNotAndDueOnLessThanEqual("DONE",today)){
   if(Objects.equals(task.lastDueReminderOn,today))continue;
   AppUser u=users.findById(task.assigneeId).orElse(null);
   if(u==null||!u.isEnabled()||!u.isRegistrationApproved())continue;
   boolean delivered=push.sendToUser(u.getUsername(),"FW-Cockpit: Aufgabe "+(task.dueOn.isBefore(today)?"überfällig":"heute fällig"),
     "Eine Vereinsaufgabe erfordert deine Aufmerksamkeit.","/?my-tasks=1","general-task-due-"+task.id+"-"+today);
   if(delivered){task.lastDueReminderOn=today;tasks.save(task);}
  }
 }
}
