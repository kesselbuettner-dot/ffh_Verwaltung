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
 private final TransientLicenseOcrService ocr=mock(TransientLicenseOcrService.class);
 private final DrivingCheckService service=new DrivingCheckService(qualified,checks,users,permission,"a-test-pepper-that-is-not-a-production-secret",ocr);
 private FireMemberQualification data(Long id,Long owner) {
  var type=new FireQualificationType("DRIVERS_LICENSE","Führerschein","🚘",true,false,30,6);
  ReflectionTestUtils.setField(type,"id",12L);
  var q=new FireMemberQualification(owner,type);
  ReflectionTestUtils.setField(q,"id",id);
  q.licenseNumberMac=service.fingerprint("L123456789");
  q.nextDueOn=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin"));
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
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  when(checks.save(any(FireQualificationCheck.class))).thenAnswer(i->i.getArgument(0));
  when(ocr.read("data:image/jpeg;base64,FAKE_TEST_IMAGE")).thenReturn("MAX MUSTER\nL123456789");
  var result=service.autoScan(7L,new DrivingCheckService.ScanInput("data:image/jpeg;base64,FAKE_TEST_IMAGE"),auth);
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
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  when(ocr.read("data:image/jpeg;base64,FAKE_TEST_IMAGE")).thenReturn("MAX MUSTER\nL999999999");
  assertThrows(ResponseStatusException.class,()->service.autoScan(7L,
    new DrivingCheckService.ScanInput("data:image/jpeg;base64,FAKE_TEST_IMAGE"),auth));
  verifyNoInteractions(checks);verify(qualified,never()).save(any());
 }
 @Test void aMemberCannotScanAnotherMembersQualification(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",24L);member.setName("Fremd");
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(data(7L,42L)));
  assertThrows(ResponseStatusException.class,()->service.autoScan(7L,
    new DrivingCheckService.ScanInput("data:image/jpeg;base64,FAKE_TEST_IMAGE"),auth));
  verifyNoInteractions(checks);
 }
 @Test void manualBookingIsPositiveAndRequiresExplicitConfirmation(){
  var q=data(7L,42L);
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  when(permission.userId(auth)).thenReturn(11L);
  assertThrows(ResponseStatusException.class,()->service.manual(7L,new DrivingCheckService.ManualInput(false),auth));
  when(checks.save(any(FireQualificationCheck.class))).thenAnswer(i->i.getArgument(0));
  assertEquals("MANUAL",service.manual(7L,new DrivingCheckService.ManualInput(true),auth).method());
 }
 @Test void clientSuppliedOcrTextCannotAuthorizeAutomaticCompletion(){
  var components=DrivingCheckService.ScanInput.class.getRecordComponents();
  assertEquals(1,components.length);
  assertEquals("imageData",components[0].getName());
  assertEquals(String.class,components[0].getType());
  assertTrue(java.util.Arrays.stream(components).noneMatch(c->c.getName().contains("recognized")),
    "Client-supplied OCR fields must not automatically attest a driving check");
 }
 @Test void tooEarlyDrivingChecksNeverProcessPhoto(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",42L);member.setName("Max Muster");
  var q=data(7L,42L);q.nextDueOn=java.time.LocalDate.now(java.time.ZoneId.of("Europe/Berlin")).plusMonths(4);
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  assertThrows(ResponseStatusException.class,()->service.autoScan(7L,new DrivingCheckService.ScanInput("camera-image"),auth));
  verifyNoInteractions(ocr,checks);
 }
 @Test void limitsRepeatedMemberOcrAttempts(){
  var member=new Member();ReflectionTestUtils.setField(member,"id",42L);member.setName("Max Muster");
  var q=data(7L,42L);
  when(permission.userId(auth)).thenReturn(9L);
  when(users.findById(9L)).thenReturn(Optional.of(user(9L,member)));
  when(qualified.findByIdForUpdate(7L)).thenReturn(Optional.of(q));
  when(ocr.read("camera-image")).thenReturn("NOT AN ACCEPTABLE LICENSE");
  for(int i=0;i<6;i++){
   assertThrows(ResponseStatusException.class,()->service.autoScan(7L,new DrivingCheckService.ScanInput("camera-image"),auth));
  }
  var over=assertThrows(ResponseStatusException.class,()->service.autoScan(7L,new DrivingCheckService.ScanInput("camera-image"),auth));
  assertEquals(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,over.getStatusCode());
  verify(ocr,times(6)).read("camera-image");
  verifyNoInteractions(checks);
 }

}
