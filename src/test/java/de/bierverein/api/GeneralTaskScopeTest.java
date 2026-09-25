package de.bierverein.api;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Prevents scope escalation through direct REST calls (not only hidden UI options). */
class GeneralTaskScopeTest {
 private final GeneralTaskRepository tasks=mock(GeneralTaskRepository.class);
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final ManagedUserRoleRepository roles=mock(ManagedUserRoleRepository.class);
 private final WebPushService push=mock(WebPushService.class);
 private final GeneralTaskController controller=new GeneralTaskController(tasks,users,roles,push);
 private AppUser account(long id,String name,Role role){
  AppUser u=new AppUser();ReflectionTestUtils.setField(u,"id",id);
  u.setUsername(name);u.setRole(role);u.setEnabled(true);u.setRegistrationApproved(true);
  Member m=new Member();m.setName(name);m.setActive(true);u.setMember(m);
  when(users.findById(id)).thenReturn(Optional.of(u));
  when(users.findByUsernameWithMember(name)).thenReturn(Optional.of(u));
  when(roles.findByUserId(id)).thenReturn(List.of());
  return u;
 }
 private Authentication auth(AppUser u){
  Authentication a=mock(Authentication.class);when(a.getName()).thenReturn(u.getUsername());return a;
 }
 private GeneralTaskController.Input input(String category,long assignee){
  return new GeneralTaskController.Input("Aufgabe","Beschreibung",assignee,null,null,category);
 }
 @Test void specialistCanOnlyCreateWithinOwnArea(){
  AppUser manager=account(1,"geraetewart",Role.GERATEWART);
  account(2,"mitglied",Role.MEMBER);
  var view=controller.list(auth(manager));
  assertEquals(List.of("DEVICE"),view.allowedCategories());
  assertThrows(ResponseStatusException.class,()->controller.create(input("GENERAL",2),auth(manager)));
  assertThrows(ResponseStatusException.class,()->controller.create(input("CATERING",2),auth(manager)));
  verify(tasks,never()).save(any());
  when(tasks.save(any(GeneralTask.class))).thenAnswer(i->i.getArgument(0));
  var accepted=controller.create(input("DEVICE",2),auth(manager));
  assertEquals("DEVICE",accepted.category());
 }
 @Test void ownAssignmentOutsideResponsibilityCannotBeEditedOrDeleted(){
  AppUser manager=account(1,"geraetewart",Role.GERATEWART);account(2,"mitglied",Role.MEMBER);
  GeneralTask task=new GeneralTask("Alt","",2L,1L,null);
  task.id=10L;task.category="FINANCE";
  when(tasks.findById(10L)).thenReturn(Optional.of(task));
  assertFalse(controller.list(auth(manager)).canCreate()==false);
  assertThrows(ResponseStatusException.class,()->controller.edit(10L,input("DEVICE",2),auth(manager)));
  assertThrows(ResponseStatusException.class,()->controller.delete(10L,auth(manager)));
 }
 @Test void boardCanCreateCrossDepartmentAndAssigneeCanUpdateStatus(){
  AppUser board=account(1,"vorstand",Role.VORSTAND),member=account(2,"mitglied",Role.MEMBER);
  when(tasks.save(any(GeneralTask.class))).thenAnswer(i->i.getArgument(0));
  var created=controller.create(input("CATERING",2),auth(board));
  assertEquals("CATERING",created.category());
  GeneralTask task=new GeneralTask("Getränke","",2L,1L,null);
  task.category="CATERING";task.id=8L;
  when(tasks.findById(8L)).thenReturn(Optional.of(task));
  var updated=controller.status(8L,new GeneralTaskController.Input(null,null,null,null,"DONE",null),auth(member));
  assertEquals("DONE",updated.status());
 }
}
