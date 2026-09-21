package de.bierverein.api;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrivingCheckServiceTest {
 private final FireMemberQualificationRepository qualified=mock(FireMemberQualificationRepository.class);
 private final FireQualificationCheckRepository checks=mock(FireQualificationCheckRepository.class);
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final FireQualificationPermissions permission=mock(FireQualificationPermissions.class);
 private final Authentication auth=mock(Authentication.class);
 private final DrivingCheckService service=new DrivingCheckService(qualified,checks,users,permission,"a-test-pepper-that-is-not-a-production-secret");
 private FireMemberQualification data(Long id,Long owner) {
  var type=new FireQualificationType("DRIVERS_LICENSE","Führerschein","🚘",true,false,30,6);
  ReflectionTestUtils.setField(type,"id",12L);
  var q=new FireMemberQualification(owner,type);
  ReflectionTestUtils.setField(q,"id",id);
  q.licenseNumberMac=service.fingerprint("L123456789");
  return q;
 }
 private AppUser user(Long id,Member member){
  var u=new AppUser();
  ReflectionTestUtils.setField(u,"id",id);
  u.setMember(member);u.setEnabled(true);u.setRegistrationApproved(true);
  return u;
 }
 @Test void exactOcrNameAndReferenceAutomaticallyBookPositiveWithoutPhoto(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",42L);member.setName("Max Muster");
  var q=data(7L,42L);
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findById(7L)).thenReturn(Optional.of(q));
  when(checks.save(any(FireQualificationCheck.class))).thenAnswer(i->i.getArgument(0));
  var result=service.autoScan(7L,new DrivingCheckService.ScanInput("Max Muster","L123456789"),auth);
  assertEquals("POSITIVE",result.result());
  assertEquals("AUTO_OCR_MATCH",result.method());
  assertEquals(LocalDate.now(),q.lastCheckedOn);
  assertEquals(LocalDate.now().plusMonths(6),q.nextDueOn);
  verify(qualified).save(q);
  verify(checks).save(argThat(c->c.memberId.equals(42L)&&"AUTO_OCR_MATCH".equals(c.method)));
 }
 @Test void wrongNumberNeverCreatesPositiveCheck(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",42L);member.setName("Max Muster");
  var q=data(7L,42L);
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findById(7L)).thenReturn(Optional.of(q));
  assertThrows(ResponseStatusException.class,()->service.autoScan(7L,
    new DrivingCheckService.ScanInput("Max Muster","L999999999"),auth));
  verifyNoInteractions(checks);verify(qualified,never()).save(any());
 }
 @Test void aMemberCannotScanAnotherMembersQualification(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",24L);member.setName("Fremd");
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findById(7L)).thenReturn(Optional.of(data(7L,42L)));
  assertThrows(ResponseStatusException.class,()->service.autoScan(7L,
    new DrivingCheckService.ScanInput("Fremd","L123456789"),auth));
  verifyNoInteractions(checks);
 }
 @Test void manualBookingIsPositiveAndRequiresExplicitConfirmation(){
  var q=data(7L,42L);
  when(qualified.findById(7L)).thenReturn(Optional.of(q));
  when(permission.userId(auth)).thenReturn(11L);
  assertThrows(ResponseStatusException.class,()->service.manual(7L,new DrivingCheckService.ManualInput(false),auth));
  when(checks.save(any(FireQualificationCheck.class))).thenAnswer(i->i.getArgument(0));
  assertEquals("MANUAL",service.manual(7L,new DrivingCheckService.ManualInput(true),auth).method());
 }
 @Test void photoBytesAreNeverAcceptedInTheScanInput(){
  var components=DrivingCheckService.ScanInput.class.getRecordComponents();
  assertEquals(2,components.length);
  for(var component:components){
   assertEquals(String.class,component.getType());
   assertFalse(component.getName().toLowerCase().contains("image"));
   assertFalse(component.getName().toLowerCase().contains("photo"));
  }
 }
}
