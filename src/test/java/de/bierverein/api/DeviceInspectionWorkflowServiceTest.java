package de.bierverein.api;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceInspectionWorkflowServiceTest {
 private final DeviceRepository devices=mock(DeviceRepository.class);
 private final DeviceInspectionRepository inspections=mock(DeviceInspectionRepository.class);
 private final DeviceCycleTaskRepository cycles=mock(DeviceCycleTaskRepository.class);
 private final DeviceInspectionTaskRepository sessions=mock(DeviceInspectionTaskRepository.class);
 private final DeviceInspectionWorkflowService workflow=
  new DeviceInspectionWorkflowService(devices,inspections,cycles,sessions);
 private static final LocalDate TODAY=LocalDate.now(ZoneId.of("Europe/Berlin"));

 private Device device(){
  Device d=new Device();
  ReflectionTestUtils.setField(d,"id",42L);
  d.setName("Strahlrohr");d.setInventoryNumber("GH-12");d.setLocation("LF 20");
  d.setActive(true);d.setInspectionIntervalMonths(6);d.setNextInspectionDate(TODAY);
  return d;
 }
 private String signature(){
  try{
   BufferedImage png=new BufferedImage(600,170,BufferedImage.TYPE_INT_RGB);
   Graphics2D g=png.createGraphics();
   g.setColor(Color.WHITE);g.fillRect(0,0,600,170);
   g.setColor(Color.BLACK);g.setStroke(new BasicStroke(5));g.drawLine(40,50,500,80);g.dispose();
   ByteArrayOutputStream out=new ByteArrayOutputStream();
   assertTrue(ImageIO.write(png,"png",out));
   return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
  }catch(Exception e){throw new AssertionError(e);}
 }
 @Test void signedIndividualInspectionUpdatesBothTaskViewsAndDates(){
  Device d=device();DeviceCycleTask cycle=new DeviceCycleTask(d,TODAY);
  ReflectionTestUtils.setField(cycle,"id",11L);
  DeviceInspectionTask session=new DeviceInspectionTask();
  session.setDevice(d);session.setOccurrenceDate(TODAY.plusDays(3));session.setStatus("DEFECTIVE");
  when(cycles.findByDeviceIdAndStatusOrderByDueOnAsc(42L,"OPEN")).thenReturn(List.of(cycle));
  when(sessions.findByDeviceIdAndInspectionIdIsNull(42L)).thenReturn(List.of(session));
  when(inspections.save(any(DeviceInspection.class))).thenAnswer(inv->{
   DeviceInspection i=inv.getArgument(0);ReflectionTestUtils.setField(i,"id",55L);return i;
  });
  DeviceInspection proof=workflow.record(d,"BESTANDEN","", "kamerad",signature(),
    "Einzelprüfung",11L,null,null);
  assertEquals(55L,proof.getId());assertNotNull(proof.getSignedAt());
  assertEquals(TODAY.plusMonths(6),d.getNextInspectionDate());
  assertEquals("DONE",cycle.getStatus());assertEquals(55L,cycle.getInspectionId());
  assertEquals("INSPECTED",session.getStatus());assertEquals(55L,session.getInspectionId());
  verify(inspections,times(1)).save(any(DeviceInspection.class));
  verify(cycles).save(cycle);verify(sessions).save(session);

 }
 @Test void unsignedOrInvalidChecksCannotBeRecorded(){
  Device d=device();
  assertThrows(ResponseStatusException.class,()->workflow.record(d,"BESTANDEN","",
   "kamerad",null,"Einzelprüfung",null,null,null));
  assertThrows(ResponseStatusException.class,()->workflow.record(d,"BESTANDEN","",
   "kamerad","data:image/svg+xml;base64,PHN2Zz4=","Einzelprüfung",null,null,null));
  assertThrows(ResponseStatusException.class,()->workflow.record(d,null,"",
   "kamerad",signature(),"Einzelprüfung",null,null,null));
  verifyNoInteractions(inspections,cycles,sessions);
 }
 @Test void failedChecksKeepDueDateAndRequireDefectExplanation(){
  Device d=device();
  assertThrows(ResponseStatusException.class,()->workflow.record(d,"MIT_MANGEL","",
   "kamerad",signature(),"Einzelprüfung",null,null,null));
  when(inspections.save(any(DeviceInspection.class))).thenAnswer(inv->inv.getArgument(0));
  when(cycles.findByDeviceIdAndStatusOrderByDueOnAsc(42L,"OPEN")).thenReturn(List.of());
  when(sessions.findByDeviceIdAndInspectionIdIsNull(42L)).thenReturn(List.of());
  DeviceInspection proof=workflow.record(d,"MIT_MANGEL","Schlauch undicht",
   "kamerad",signature(),"Einzelprüfung",null,null,null);
  assertEquals(TODAY,d.getNextInspectionDate());
  assertEquals("DEFECTIVE",d.getOperationalStatus());
  assertEquals("Schlauch undicht",proof.getDefects());
 }
}
