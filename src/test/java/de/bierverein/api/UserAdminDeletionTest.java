package de.bierverein.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAdminDeletionTest {
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final MemberRepository members=mock(MemberRepository.class);
 private final ManagedUserRoleRepository assignments=mock(ManagedUserRoleRepository.class);
 private final DeviceCycleTaskRepository tasks=mock(DeviceCycleTaskRepository.class);
 private final PushSubscriptionRepository push=mock(PushSubscriptionRepository.class);
 private final CalendarSubscriptionRepository calendars=mock(CalendarSubscriptionRepository.class);
 private UserAdminController controller(){
  UserAdminController controller=new UserAdminController(users,members,mock(org.springframework.security.crypto.password.PasswordEncoder.class),
   mock(PrimaryRoleSyncService.class),assignments);
  ReflectionTestUtils.setField(controller,"cycleTasks",tasks);
  ReflectionTestUtils.setField(controller,"pushSubscriptions",push);
  ReflectionTestUtils.setField(controller,"calendarSubscriptions",calendars);
  return controller;
 }
 private AppUser user(long id,String name,Role role){
  AppUser u=new AppUser();ReflectionTestUtils.setField(u,"id",id);u.setUsername(name);
  u.setRole(role);u.setEnabled(true);u.setRegistrationApproved(true);return u;
 }
 @Test void deletesLoginButPreservesMemberAndReturnsAssignedInspectionsToManager(){
  AppUser deleted=user(23,"member@example.org",Role.MEMBER);
  Member member=new Member();member.setName("Testmitglied");
  deleted.setMember(member);member.setUser(deleted);
  when(users.findById(23L)).thenReturn(Optional.of(deleted));
  when(assignments.findByUserId(23L)).thenReturn(List.of());
  when(push.findByUsername(deleted.getUsername())).thenReturn(List.of());
  when(calendars.findByUsername(deleted.getUsername())).thenReturn(Optional.empty());
  Authentication auth=mock(Authentication.class);when(auth.getName()).thenReturn("admin");
  controller().delete(23L,auth);
  verify(tasks).releaseAssignments(23L);
  verify(users).delete(deleted);verify(users).flush();
  verify(members,never()).delete(any(Member.class));
  assertNull(member.getUser());
 }
 @Test void blocksDeletionOfOwnAdminLoginAndLastRemainingAdmin(){
  Authentication auth=mock(Authentication.class);when(auth.getName()).thenReturn("admin");
  AppUser admin=user(1,"admin",Role.ADMIN);
  when(users.findById(1L)).thenReturn(Optional.of(admin));
  assertThrows(ResponseStatusException.class,()->controller().delete(1L,auth));
  AppUser second=user(2,"another",Role.ADMIN);
  when(users.findById(2L)).thenReturn(Optional.of(second));
  when(assignments.countByRoleCodeAndUserEnabled("ADMIN",true)).thenReturn(1L);
  assertThrows(ResponseStatusException.class,()->controller().delete(2L,auth));
  verify(users,never()).delete(any(AppUser.class));
 }
}
