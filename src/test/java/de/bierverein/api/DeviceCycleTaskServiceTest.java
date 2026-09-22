package de.bierverein.api;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceCycleTaskServiceTest {
 private final DeviceRepository devices=mock(DeviceRepository.class);
 private final DeviceCycleTaskRepository tasks=mock(DeviceCycleTaskRepository.class);
 private final AppUserRepository users=mock(AppUserRepository.class);
 private final ManagedUserRoleRepository roles=mock(ManagedUserRoleRepository.class);
 private final DeviceInspectionRepository inspections=mock(DeviceInspectionRepository.class);
 private final DeviceCycleTaskService service=new DeviceCycleTaskService(devices,tasks,users,roles,inspections);
 private JwtAuthenticationToken auth(Long id){
  JwtAuthenticationToken token=mock(JwtAuthenticationToken.class);
  Jwt jwt=mock(Jwt.class);
  when(token.getToken()).thenReturn(jwt);
  when(jwt.getClaim("userId")).thenReturn(id);
  return token;
 }
 private AppUser account(Long id,Role role){
  AppUser u=new AppUser();
  ReflectionTestUtils.setField(u,"id",id);
  u.setUsername("user"+id);
  u.setRole(role);u.setEnabled(true);u.setRegistrationApproved(true);
  return u;
 }
 private Device device(Long id,LocalDate due){
  Device d=new Device();ReflectionTestUtils.setField(d,"id",id);
  d.setName("Atemschutzgerät "+id);d.setLocation("LF 20");d.setActive(true);
  d.setInspectionRequired(true);d.setInspectionIntervalMonths(6);d.setNextInspectionDate(due);
  return d;
 }
 private DeviceCycleTask job(Long id,Device device){
  DeviceCycleTask t=new DeviceCycleTask(device,device.getNextInspectionDate());
  ReflectionTestUtils.setField(t,"id",id);
  return t;
 }
 @Test void createsExactlyOneCycleTaskWithinWarningWindowAndNoPrematureTask(){
  LocalDate today=LocalDate.now(ZoneId.of("Europe/Berlin"));
  Device due=device(2L,today.plusDays(14)),future=device(3L,today.plusDays(90));
  when(devices.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(due,future));
  when(tasks.insertIfAbsent(2L,today.plusDays(14))).thenReturn(1,0);
  assertEquals(1,service.generateDue());
  assertEquals(0,service.generateDue());
  verify(tasks,times(2)).insertIfAbsent(2L,today.plusDays(14));
  verify(tasks,never()).insertIfAbsent(eq(3L),any());
 }
 @Test void firstInspectionDateIsStableWhenNoHistoryExists(){
  Device first=device(2L,null);
  when(devices.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(first));
  when(tasks.insertIfAbsent(eq(2L),any())).thenReturn(1,0);
  assertEquals(1,service.generateDue());
  LocalDate date=LocalDate.now(ZoneId.of("Europe/Berlin"));
  assertEquals(date,first.getNextInspectionDate());
  assertEquals(0,service.generateDue());
  verify(devices,times(1)).save(first);
  verify(tasks,times(2)).insertIfAbsent(2L,date);
 }
 @Test void managerSeesRoleInboxAndCanDelegatePerDeviceOnlyToActiveMember(){
  AppUser manager=account(1L,Role.GERATEWART),member=account(2L,Role.MEMBER);
  Member m=new Member();m.setName("Max Muster");m.setActive(true);member.setMember(m);
  when(users.findById(1L)).thenReturn(Optional.of(manager));
  when(users.findById(2L)).thenReturn(Optional.of(member));
  when(devices.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
  DeviceCycleTask task=job(8L,device(3L,LocalDate.now()));
  when(tasks.findLocked(8L)).thenReturn(Optional.of(task));
  when(tasks.save(task)).thenAnswer(inv->inv.getArgument(0));
  var assigned=service.assign(8L,new DeviceCycleTaskService.AssignInput(2L),auth(1L));
  assertEquals(2L,assigned.assignedUserId());
  assertEquals("Max Muster",assigned.assignedName());
  verify(tasks).save(task);
 }
 @Test void memberCannotAssignOrCompleteSomeoneElsesTask(){
  AppUser member=account(2L,Role.MEMBER);
  when(users.findById(2L)).thenReturn(Optional.of(member));
  DeviceCycleTask task=job(8L,device(3L,LocalDate.now()));
  when(tasks.findLocked(8L)).thenReturn(Optional.of(task));
  assertThrows(ResponseStatusException.class,()->service.assign(8L,new DeviceCycleTaskService.AssignInput(2L),auth(2L)));
  assertThrows(ResponseStatusException.class,()->service.finish(8L,new DeviceCycleTaskService.FinishInput("BESTANDEN",""),auth(2L)));
  verifyNoInteractions(inspections);
 }
 @Test void memberCompletesAssignedTaskAndAdvancesDeviceCycleWithoutWritingAnUnassignedTask(){
  LocalDate now=LocalDate.now(ZoneId.of("Europe/Berlin"));
  Device device=device(3L,now);
  DeviceCycleTask task=job(8L,device);
  task.assign(2L,"geraetewart");
  AppUser member=account(2L,Role.MEMBER);
  when(users.findById(2L)).thenReturn(Optional.of(member));
  when(tasks.findLocked(8L)).thenReturn(Optional.of(task));
  when(tasks.save(task)).thenAnswer(inv->inv.getArgument(0));
  var result=service.finish(8L,new DeviceCycleTaskService.FinishInput("BESTANDEN","Alles OK"),auth(2L));
  assertEquals("DONE",result.status());
  assertEquals(now.plusMonths(6),device.getNextInspectionDate());
  verify(inspections).save(argThat(i->i.getInspectionDate().equals(now)
     &&i.getResult().equals("BESTANDEN")
     &&i.getDevice()==device
     &&i.getNextInspectionDate().equals(now.plusMonths(6))));
  assertThrows(ResponseStatusException.class,()->service.finish(8L,
    new DeviceCycleTaskService.FinishInput("BESTANDEN",""),auth(2L)));
  verify(inspections,times(1)).save(any(DeviceInspection.class));
 }
 @Test void defectsRequireNotesAndKeepExistingDueDateAndMarkDeviceDefective(){
  LocalDate now=LocalDate.now(ZoneId.of("Europe/Berlin"));
  Device d=device(3L,now);
  DeviceCycleTask task=job(8L,d);
  AppUser manager=account(1L,Role.GERATEWART);
  when(users.findById(1L)).thenReturn(Optional.of(manager));
  when(tasks.findLocked(8L)).thenReturn(Optional.of(task));
  when(tasks.save(task)).thenAnswer(inv->inv.getArgument(0));
  assertThrows(ResponseStatusException.class,()->service.finish(8L,
   new DeviceCycleTaskService.FinishInput("MIT_MANGEL",""),auth(1L)));
  var done=service.finish(8L,new DeviceCycleTaskService.FinishInput("MIT_MANGEL","Schlauch porös"),auth(1L));
  assertEquals("DONE",done.status());
  assertEquals(now,d.getNextInspectionDate());
  assertEquals("DEFECTIVE",d.getOperationalStatus());
  verify(inspections).save(argThat(i->"MIT_MANGEL".equals(i.getResult())&&"Schlauch porös".equals(i.getDefects())));
 }
}
