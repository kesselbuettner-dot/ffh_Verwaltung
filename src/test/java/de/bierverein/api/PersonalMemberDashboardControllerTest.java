package de.bierverein.api;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** All personal queries must be tied to the authenticated account, never to request-supplied IDs. */
class PersonalMemberDashboardControllerTest {
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final MemberExtraRepository extras=mock(MemberExtraRepository.class);
 private final FireMemberQualificationRepository qualifications=mock(FireMemberQualificationRepository.class);
 private final TrainingScheduleService schedule=mock(TrainingScheduleService.class);
 private final TrainingAttendanceRepository attendance=mock(TrainingAttendanceRepository.class);
 private final OrderRepository orders=mock(OrderRepository.class);
 private final PersonalMemberDashboardController controller=new PersonalMemberDashboardController(
  users,extras,qualifications,schedule,attendance,orders);
 private Authentication auth(String name){
  Authentication a=mock(Authentication.class);when(a.getName()).thenReturn(name);return a;
 }
 private AppUser account(String username,Long memberId){
  AppUser u=new AppUser();u.setUsername(username);u.setRole(Role.ADMIN);u.setEnabled(true);u.setRegistrationApproved(true);
  if(memberId!=null){
   Member m=new Member();ReflectionTestUtils.setField(m,"id",memberId);m.setName("Mein Mitglied");
   m.setBalance(new BigDecimal("18.50"));u.setMember(m);
  }
  when(users.findByUsernameWithMember(username)).thenReturn(Optional.of(u));return u;
 }
 @Test void ownMemberOnlyIsQueriedEvenWhenAdmin(){
  account("self",42L);
  when(qualifications.findByMemberId(42L)).thenReturn(List.of());
  when(schedule.dashboard("self")).thenReturn(List.of());
  when(attendance.findTop25ByUsernameOrderByOccurrenceDateDesc("self")).thenReturn(List.of());
  when(orders.findTop50ByMemberIdOrderByCreatedAtDesc(42L)).thenReturn(List.of());
  var v=controller.mine(auth("self"));
  assertTrue(v.linked());assertEquals(42L,v.member().memberId());
  assertEquals(new BigDecimal("18.50"),v.balance());
  verify(qualifications).findByMemberId(42L);
  verify(orders).findTop50ByMemberIdOrderByCreatedAtDesc(42L);
  verifyNoMoreInteractions(orders,qualifications);
 }
 @Test void unlinkedAccountNeverQueriesOtherMembers(){
  account("unlinked",null);
  var v=controller.mine(auth("unlinked"));
  assertFalse(v.linked());assertNull(v.member());
  verifyNoInteractions(orders,qualifications,attendance,extras,schedule);
 }
 @Test void rejectedUserCannotReadPersonalInformation(){
  AppUser disabled=account("blocked",42L);disabled.setEnabled(false);
  assertThrows(ResponseStatusException.class,()->controller.mine(auth("blocked")));
  verifyNoInteractions(orders,qualifications,attendance,extras,schedule);
 }
 @Test void failedCalendarKeepsBalanceAndOtherDataAvailable(){
  account("self",42L);
  when(schedule.dashboard("self")).thenThrow(new IllegalStateException("DB temporarily unavailable"));
  var v=controller.mine(auth("self"));
  assertTrue(v.linked());assertEquals(new BigDecimal("18.50"),v.balance());
  assertEquals(1,v.warnings().size());
 }
}
