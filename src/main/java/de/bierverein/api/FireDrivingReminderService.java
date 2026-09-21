package de.bierverein.api;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FireDrivingReminderService {
 private final FireMemberQualificationRepository qualifications;
 private final FireQualificationReminderRepository sent;
 private final MemberRepository members;
 private final WebPushService push;
 public FireDrivingReminderService(FireMemberQualificationRepository qualifications,
   FireQualificationReminderRepository sent,MemberRepository members,WebPushService push){
  this.qualifications=qualifications;this.sent=sent;this.members=members;this.push=push;
 }
 @Scheduled(cron="0 0 8 * * *",zone="Europe/Berlin")
 @Transactional
 public void dailyReminders(){
  for(FireMemberQualification qualification:qualifications.findAllByTypeCode("DRIVERS_LICENSE")){
   notifyIfDue(qualification);
  }
 }
 @Transactional
 public boolean notifyOne(Long qualificationId){
  return qualifications.findById(qualificationId)
    .filter(q->"DRIVERS_LICENSE".equals(q.type.code))
    .map(this::notifyIfDue).orElse(false);
 }
 private boolean notifyIfDue(FireMemberQualification q){
  if(!q.active||!q.type.tracked)return false;
  LocalDate now=LocalDate.now(ZoneId.of("Europe/Berlin"));
  LocalDate due=q.lastCheckedOn!=null&&q.type.intervalMonths>0
   ?q.lastCheckedOn.plusMonths(q.type.intervalMonths):q.nextDueOn;
  if(due==null||now.isBefore(due.minusDays(q.type.warningDays)))return false;
  Member member=members.findById(q.memberId).orElse(null);
  AppUser user=member==null?null:member.getUser();
  if(user==null||!user.isEnabled()||!user.isRegistrationApproved()||!member.isActive())return false;
  String recipient=user.getUsername();
  if(sent.existsByQualificationIdAndDueDateAndRecipient(q.id,due,recipient))return false;
  boolean delivered=push.sendToUser(recipient,"FW-Cockpit: Prüfung fällig",
    "Bitte öffne deinen persönlichen Prüfauftrag. Es werden keine Fotos gespeichert.",
    "/?driver-check=1","driving-check-"+q.id+"-"+due);
  if(delivered)sent.save(new FireQualificationReminder(q.id,due,recipient));
  return delivered;
 }
}
