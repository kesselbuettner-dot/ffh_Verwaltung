package de.bierverein.api;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class FireDrivingReminderServiceTest {
 @Test void onlyTheAffectedMemberReceivesOneDueDateReminder(){
  var qualifications=mock(FireMemberQualificationRepository.class);
  var sent=mock(FireQualificationReminderRepository.class);
  var members=mock(MemberRepository.class);
  var push=mock(WebPushService.class);
  var reminders=new FireDrivingReminderService(qualifications,sent,members,push);
  var t=new FireQualificationType("DRIVERS_LICENSE","Führerschein","🚘",true,false,30,6);
  var q=new FireMemberQualification(42L,t);
  org.springframework.test.util.ReflectionTestUtils.setField(q,"id",7L);
  q.nextDueOn=LocalDate.now(ZoneId.of("Europe/Berlin")).plusDays(10);
  Member m=new Member();m.setName("Muster");m.setActive(true);
  AppUser u=new AppUser();u.setUsername("member@example.org");u.setEnabled(true);u.setRegistrationApproved(true);
  u.setMember(m);m.setUser(u);
  when(qualifications.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  when(members.findById(42L)).thenReturn(Optional.of(m));
  when(push.sendToUser(eq("member@example.org"),anyString(),anyString(),anyString(),anyString())).thenReturn(true);
  assertTrue(reminders.notifyOne(7L));
  verify(push).sendToUser(eq("member@example.org"),anyString(),anyString(),eq("/?driver-check=1"),anyString());
  verify(sent).save(argThat(r->r.qualificationId.equals(7L)&&r.recipient.equals("member@example.org")));
  when(sent.existsByQualificationIdAndDueDateAndRecipient(eq(7L),any(),eq("member@example.org"))).thenReturn(true);
  assertFalse(reminders.notifyOne(7L));
  verify(push,times(1)).sendToUser(eq("member@example.org"),anyString(),anyString(),anyString(),anyString());
 }
}
